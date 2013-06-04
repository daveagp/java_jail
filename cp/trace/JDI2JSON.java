import java.util.*;
import com.sun.jdi.*;
import com.sun.jdi.request.*;
import com.sun.jdi.event.*;

import java.io.*;
import javax.json.*;

public class JDI2JSON {
    
    private class InputPuller {
        InputStreamReader vm_link;
        StringWriter contents = new java.io.StringWriter();
        String getContents() {
            return contents.toString();
        }
        InputPuller(InputStream ir) {
            vm_link = new InputStreamReader(ir);
        }
        void pull() {
            int BUFFER_SIZE = 2048;
            char[] cbuf = new char[BUFFER_SIZE];
            int count;
            try {
                while (vm_link.ready() 
                       && ((count = vm_link.read(cbuf, 0, BUFFER_SIZE)) >= 0))
                    contents.write(cbuf, 0, count);
            } 
            catch(IOException e) {
                throw new RuntimeException("I/O Error!");
            }
        }
    }
    
    private InputPuller stdout, stderr;
    private JsonObject last_ep = null;
    private TreeMap<Long, ObjectReference> heap;
    private TreeSet<Long> heap_done;

    public List<ReferenceType> staticListable = new ArrayList<>();

    public JDI2JSON(InputStream vm_stdout, InputStream vm_stderr) {
        stdout = new InputPuller(vm_stdout);
        stderr = new InputPuller(vm_stderr);
    }
    
    // returns null when nothing changed since the last time
    // (or when only event type changed and new value is "step_line")
    public JsonObject convertExecutionPoint(Event e, Location loc, ThreadReference t) {
        stdout.pull();
        stderr.pull();        
        
        heap_done = new TreeSet<Long>();
        heap = new TreeMap<>();

        JsonObjectBuilder result = Json.createObjectBuilder();
        result.add("stdout", stdout.getContents());
        if (e instanceof MethodEntryEvent) {
            result.add("event", "call");
            result.add("line", loc.lineNumber());
        }
        else if (e instanceof MethodExitEvent) {
            JsonValue returnValue = convertValue(((MethodExitEvent)e).returnValue());
            // because the returning frame is already gone, we just
            // fake that it's still there, returning the most recent e.p.
            // with the modification added
            return createReturnEventFrom(loc, last_ep, returnValue);
        }
        else if (e instanceof BreakpointEvent || e instanceof StepEvent) {
            result.add("event", "step_line");
            result.add("line", loc.lineNumber());
        }
        else {
            throw new RuntimeException("Bad event " + e);
        }
                        
        JsonArrayBuilder frames = Json.createArrayBuilder();
        try {
            //int count = 0;
            for (StackFrame sf : t.frames()) {
                if (!showFramesInLocation(sf.location()))
                    continue;
                //count++;
                frames.add(convertFrame(sf));
            }
        }
        catch (IncompatibleThreadStateException ex) {
	    //thread was not suspended .. should not normally happen
	    
            throw new RuntimeException("ITSE");
        }
        result.add("stack_locals", frames);

        JsonObjectBuilder statics = Json.createObjectBuilder();
        JsonArrayBuilder statics_a = Json.createArrayBuilder();
        for (ReferenceType rt : staticListable) 
            if (rt.isInitialized()) 
                for (Field f : rt.visibleFields()) 
                    if (f.isStatic()) {
                        statics.add(rt.name()+"."+f.name(),
                                    convertValue(rt.getValue(f)));
                        statics_a.add(f.typeName()+"."+f.name());
                    }
        result.add("globals", statics);
        //result.add("ordered_globals", statics_a);
        
        result.add("func_name", loc.method().name());
        
        /*JsonObjectBuilder heapDescription = Json.createObjectBuilder();
        describeHeap(heapDescription);
        result.add("heap", heapDescription);*/
        JsonObject curr_ep = result.build();        
        if (reallyChanged(last_ep, curr_ep)) {
            last_ep = curr_ep;
            return curr_ep;
        }
        else
            return null;
    }
    
    private String[] builtin_packages = {"java", "javax", "sun", "com.sun"};

    private boolean in_builtin_package(String S) {
        for (String badPrefix: builtin_packages)
            if (S.startsWith(badPrefix+"."))
	    return true;
        return false;
    }

    private boolean showFramesInLocation(Location loc) {
	return (!in_builtin_package(loc.toString()));
    }

    private boolean showGuts(ReferenceType rt) {
	return (!in_builtin_package(rt.name()));
    }    

    public boolean reportEventsAtLocation(Location loc) {
	return (!in_builtin_package(loc.toString()));
    }
    
    private JsonObject createReturnEventFrom(Location loc, JsonObject base_ep, JsonValue returned) {
	try {
	    JsonObjectBuilder result = Json.createObjectBuilder();
	    result.add("event", "return");
	    result.add("line", loc.lineNumber());
	    for (Map.Entry<String, JsonValue> me : base_ep.entrySet()) {
		if (me.getKey().equals("event") || me.getKey().equals("line")) 
		    {}
		else if (me.getKey().equals("stack_locals")) {
		    JsonArray old_stack_locals = (JsonArray)me.getValue();
		    JsonArray old_top_name_frame = (JsonArray)(old_stack_locals.get(0));
		    JsonObject old_top_frame_vars = (JsonObject)(old_top_name_frame.get(1));
		    result.add("stack_locals", 
			       jsonModifiedArray(old_stack_locals, 0,
						 jsonModifiedArray(old_top_name_frame, 1, 
								   jsonModifiedObject(old_top_frame_vars, "__return__", returned))));
		}
		else result.add(me.getKey(), me.getValue());
	    }
	    return result.build();
	}
	catch (IndexOutOfBoundsException exc) {
	    return base_ep;
	}
    }
    
    private boolean reallyChanged(JsonObject old_ep, JsonObject new_ep) {
        if (old_ep == null) return true;
        return (!new_ep.equals(jsonModifiedObject(old_ep, "event", 
                                                  jsonString("step_line"))));
    }
    
    private JsonArrayBuilder convertFrame(StackFrame sf) {
        JsonObjectBuilder result = Json.createObjectBuilder();
        if (sf.thisObject() != null)
            result.add("this", convertValue(sf.thisObject()));
        List<LocalVariable> frame_vars = null;
        try {
            frame_vars = sf.location().method().variables(); //only throwing statement
            for (LocalVariable lv : frame_vars) {
                try {
                    result.add(lv.name(),
                               convertValue(sf.getValue(lv)));
                }
                catch (IllegalArgumentException exc) {
                    // variable not yet defined, don't list it
                }
            }
            
        }
        catch (AbsentInformationException ex) {
            //System.out.println("AIE: can't list variables in " + sf.location());
        }            
        return Json.createArrayBuilder()
     .add(sf.location().method().name())
     .add(result);
    }
    
    /*void describeHeap(JsonObjectBuilder result) {
        TreeSet<Long> heap_done = new java.util.TreeSet<>();
        while (!heap.isEmpty()) {
            Map.Entry<Long, ObjectReference> first = heap.firstEntry();
            ObjectReference obj = first.getValue();
            long id = first.getKey();
            heap.remove(id);
            if (heap_done.contains(id))
                continue;
            result.add(""+id, convertObject(obj));
            heap_done.add(id);
        }
    }*/
    
    private JsonValue convertObject(ObjectReference obj) {
        JsonArrayBuilder result = Json.createArrayBuilder();
        if (obj instanceof ArrayReference) {
            if (heap_done.contains(obj.uniqueID())) {
                result.add("P_LIST");
                result.add(obj.uniqueID());
            }
            else {
                result.add("LIST");
                result.add(obj.uniqueID());
                heap_done.add(obj.uniqueID());
                for (Value v : ((ArrayReference)obj).getValues())
                    result.add(convertValue(v));
            }
            return result.build();
        }
        else if (obj instanceof StringReference) {
            return jsonString(((StringReference)obj).value());
        }
        // do we need special cases for ClassObjectReference, ThreadReference,.... ?
        
        // now deal with Objects. 
        else if (heap_done.contains(obj.uniqueID())) {            
            result.add("P_INSTANCE");
            result.add(obj.uniqueID());
            return result.build();
        }
        else {
            heap_done.add(obj.uniqueID());
            result.add("INSTANCE").add(obj.uniqueID()).
                add(obj.referenceType().name());
            if (showGuts(obj.referenceType())) {
                // fields: -inherited -hidden +synthetic
                // visibleFields: +inherited -hidden +synthetic
                // allFields: +inherited +hidden +repeated_synthetic
                for (Map.Entry<Field,Value> me :  
                         obj.getValues(obj.referenceType().visibleFields()).entrySet()) {
                    if (!me.getKey().isStatic())
                        result.add(Json.createArrayBuilder()
                                       .add(me.getKey().name())
                                       .add(convertValue(me.getValue())));
                }
            }
            return result.build(); 
        }
    }

    private JsonValue convertValue(Value v) {
        if (v instanceof BooleanValue) {
            if (((BooleanValue)v).value()==true) 
                return JsonValue.TRUE;
            else
                return JsonValue.FALSE;
        }
        else if (v instanceof ByteValue) return jsonInt(((ByteValue)v).value());
        else if (v instanceof ShortValue) return jsonInt(((ShortValue)v).value());
        else if (v instanceof IntegerValue) return jsonInt(((IntegerValue)v).value());
        else if (v instanceof LongValue) return jsonInt(((LongValue)v).value());
        else if (v instanceof FloatValue) return jsonReal(((FloatValue)v).value());
        else if (v instanceof DoubleValue) return jsonReal(((DoubleValue)v).value());
        else if (v instanceof CharValue) return jsonString(((CharValue)v).value()+"");
        else if (v instanceof VoidValue) 
            return jsonString("<VOID>");
        else if (!(v instanceof ObjectReference)) return JsonValue.NULL; //not a hack
        else {
            return convertObject((ObjectReference)v);
/*            ObjectReference obj = (ObjectReference)v;
            heap.put(obj.uniqueID(), obj);
            return Json.createArrayBuilder().add("REF").add(obj.uniqueID()).build();*/
        }
    }


    static String error(String message) {
	return Json.createArrayBuilder().add
	    (Json.createObjectBuilder()
	     .add("line", "1")
	     .add("event", "uncaught_exception")
	     .add("offset", "1")
	     .add("exception_msg", message)).build().toString();
    }

    /* JSON utility methods */
    
    static JsonValue jsonInt(long l) {
        return Json.createArrayBuilder().add(l).build().getJsonNumber(0);
    }

    static JsonValue jsonReal(double d) {
        return Json.createArrayBuilder().add(d).build().getJsonNumber(0);
    }

    static JsonValue jsonString(String S) {
        return Json.createArrayBuilder().add(S).build().getJsonString(0);
    }
    
    static JsonObject jsonModifiedObject(JsonObject obj, String S, JsonValue v) {
        JsonObjectBuilder result = Json.createObjectBuilder();
        result.add(S, v);
        for (Map.Entry<String, JsonValue> me : obj.entrySet()) {
            if (!S.equals(me.getKey()))
                result.add(me.getKey(), me.getValue());
        }
        return result.build();
    }

    static JsonArray jsonModifiedArray(JsonArray arr, int tgt, JsonValue v) {
        JsonArrayBuilder result = Json.createArrayBuilder();
        int i = 0;
        for (JsonValue w : arr) {
            if (i == tgt) result.add(v);
            else result.add(w);
            i++;
        }
        return result.build();
    }

}