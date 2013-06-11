/*****************************************************************************

traceprinter: a Java package to print traces of Java programs
David Pritchard (daveagp@gmail.com), created May 2013

The contents of this directory are released under the GNU Affero 
General Public License, versions 3 or later. See LICENSE or visit:
http://www.gnu.org/licenses/agpl.html

See README for documentation on this package.

******************************************************************************/

package traceprinter;

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
    
    private VirtualMachine vm;
    private InputPuller stdout, stderr;
    private JsonObject last_ep = null;
    private TreeMap<Long, ObjectReference> heap;
    private TreeSet<Long> heap_done;

    /*    private ArrayList<Long> frame_stack = new ArrayList<Long>();*/
    private long frame_ticker = 0;

    public List<ReferenceType> staticListable = new ArrayList<>();

    public JDI2JSON(VirtualMachine vm, InputStream vm_stdout, InputStream vm_stderr) {
        stdout = new InputPuller(vm_stdout);
        stderr = new InputPuller(vm_stderr);
	//frame_stack.add(frame_ticker++);
    }
    
    // returns null when nothing changed since the last time
    // (or when only event type changed and new value is "step_line")
    public ArrayList<JsonObject> convertExecutionPoint(Event e, Location loc, ThreadReference t) {
        stdout.pull();
        stderr.pull();        

	//System.out.println(e);

	ArrayList<JsonObject> results = new ArrayList<>();
        
        heap_done = new TreeSet<Long>();
        heap = new TreeMap<>();

	JsonValue returnValue = null;

        JsonObjectBuilder result = Json.createObjectBuilder();
        result.add("stdout", stdout.getContents());
        if (e instanceof MethodEntryEvent) {
            result.add("event", "call");
	    //frame_stack.add(frame_ticker++);
            result.add("line", loc.lineNumber());
        }
        else if (e instanceof MethodExitEvent) {
            returnValue = convertValue(((MethodExitEvent)e).returnValue());
	    result.add("event", "return");
	    result.add("line", loc.lineNumber());
        }
        else if (e instanceof BreakpointEvent || e instanceof StepEvent) {
            result.add("event", "step_line");
            result.add("line", loc.lineNumber());
        }
        else if (e instanceof ExceptionEvent) {
            // we could compare this with null to see if it was caught.
            // Location katch = ((ExceptionEvent)e).catchLocation();
            
            // but it turns out we don't care, since either the code
            // keeps going or just halts appropriately anyway.

            result.add("event", "exception");
            
            result.add("exception_msg", exceptionMessage((ExceptionEvent)e));
        }
                        
        JsonArrayBuilder frames = Json.createArrayBuilder();
        try {
	    boolean firstFrame = true;
            for (StackFrame sf : t.frames()) {
                if (!showFramesInLocation(sf.location()))
                    continue;
                frame_ticker++;
                frames.add(convertFrame(sf, firstFrame, returnValue));
		firstFrame = false;
		returnValue = null;
            }
        }
        catch (IncompatibleThreadStateException ex) {
	    //thread was not suspended .. should not normally happen
	    
            throw new RuntimeException("ITSE");
        }
        result.add("stack_to_render", frames);

        //if (e instanceof MethodExitEvent)
	//  frame_stack.remove(frame_stack.size()-1);

        JsonObjectBuilder statics = Json.createObjectBuilder();
        JsonArrayBuilder statics_a = Json.createArrayBuilder();
        for (ReferenceType rt : staticListable) 
            if (rt.isInitialized()) 
                for (Field f : rt.visibleFields()) 
                    if (f.isStatic()) {
                        statics.add(rt.name()+"."+f.name(),
                                    convertValue(rt.getValue(f)));
                        statics_a.add(rt.name()+"."+f.name());
                    }
        result.add("globals", statics);
        result.add("ordered_globals", statics_a);
        
        result.add("func_name", loc.method().name());
        
        JsonObjectBuilder heapDescription = Json.createObjectBuilder();
        convertHeap(heapDescription);
        result.add("heap", heapDescription);

	JsonObject this_ep = result.build();
	if (reallyChanged(last_ep, this_ep)) {
	    results.add(this_ep);
	    last_ep = this_ep;
	}
	
	return results;
    }
    
    private String[] builtin_packages = {"java", "javax", "sun", "com.sun", "traceprinter"};

    private boolean in_builtin_package(String S) {
        for (String badPrefix: builtin_packages)
            if (S.startsWith(badPrefix+"."))
	    return true;
        return false;
    }

    private boolean showFramesInLocation(Location loc) {
	return (!in_builtin_package(loc.toString())
                && !loc.method().name().contains("$"));
        // skip synthetic accessor methods
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
		else if (me.getKey().equals("stack_to_render")) {
		    JsonArray old_stack_to_render = (JsonArray)me.getValue();
		    JsonObject old_top_frame = (JsonObject)(old_stack_to_render.get(0));
		    JsonObject old_top_frame_vars = (JsonObject)(old_top_frame.get("encoded_locals"));
		    JsonArray old_top_frame_vars_o = (JsonArray)(old_top_frame.get("ordered_varnames"));
		    result.add("stack_to_render", 
			       jsonModifiedArray(old_stack_to_render, 0,
						 jsonModifiedObject
						 (jsonModifiedObject
						  (old_top_frame, 
						   "encoded_locals", 
						   jsonModifiedObject(old_top_frame_vars, "__return__", returned)),
						  "ordered_varnames", 
						  jsonModifiedArray(old_top_frame_vars_o, -1, jsonString("__return__")))));
		}
		else result.add(me.getKey(), me.getValue());
	    }
	    return result.build();
	}
	catch (IndexOutOfBoundsException exc) {
	    return base_ep;
	}
    }

    // issue: the frontend uses persistent frame ids but JDI doesn't provide them
    // approach 1, trying to compute them, seems intractable (esp. w/ callbacks)
    // approach 2, using an id based on stack depth, does not work w/ frontend
    // approach 3, just give each frame at each execution point a unique id,
    // is what we do. but we also want to skip animating e.p.'s where nothing changed,
    // and if only the frame ids changed, we should treat it as if nothing changed
    private boolean reallyChanged(JsonObject old_ep, JsonObject new_ep) {
        if (old_ep == null) return true;
        return !stripFrameIDs(new_ep).equals(stripFrameIDs(old_ep));
    }

    private JsonObject stripFrameIDs(JsonObject ep) {
        JsonArrayBuilder result = Json.createArrayBuilder();
        for (JsonValue frame : (JsonArray)(ep.get("stack_to_render"))) {
                 result.add(jsonModifiedObject
                            (jsonModifiedObject( (JsonObject)frame,
                                                 "unique_hash", 
                                                 jsonString("")),
                             "frame_id",
                             jsonInt(0)));
             }
        return jsonModifiedObject(ep, "stack_to_render", result.build());
    }

    private JsonObjectBuilder convertFrame(StackFrame sf, boolean highlight, JsonValue returnValue) {
        JsonObjectBuilder result = Json.createObjectBuilder();
        JsonArrayBuilder result_ordered = Json.createArrayBuilder();
        if (sf.thisObject() != null) {
            result.add("this", convertValue(sf.thisObject()));
            result_ordered.add("this");
	}
        List<LocalVariable> frame_vars = null, frame_args = null;
        try {
            // args make sense to show first
            frame_args = sf.location().method().arguments(); //throwing statement
            for (LocalVariable lv : frame_args) {
                //System.out.println(sf.location().method().getClass());
                try {
                    result.add(lv.name(),
                               convertValue(sf.getValue(lv)));
                    result_ordered.add(lv.name());
                }
                catch (IllegalArgumentException exc) {
                    System.out.println("That shouldn't happen!");
                }
            }

            // now non-args

            /* We're using the fact that the hashCode tells us something
               about the variable's position (which is subject to change)
               to compensate for that the natural order of variables()
               is often different from the declaration order (see LinkedList.java) */
            frame_vars = sf.location().method().variables(); //throwing statement
            TreeMap<Integer, String> orderByHash = null;
            int offset = 0;
            for (LocalVariable lv : frame_vars) 
                if (!lv.isArgument()) 
                    if (!lv.name().endsWith("$")) { // skip for-loop synthetics
                        try {
                            result.add(lv.name(),
                                       convertValue(sf.getValue(lv)));
                            if (orderByHash == null) {
                                offset = lv.hashCode();
                                orderByHash = new TreeMap<>();
                            }
                            orderByHash.put(lv.hashCode() - offset, lv.name());
                        }
                        catch (IllegalArgumentException exc) {
                            // variable not yet defined, don't list it
                        }
                    }
            if (orderByHash != null) // maybe no local vars
                for (Map.Entry<Integer,String> me : orderByHash.entrySet())
                    result_ordered.add(me.getValue());
        }
        catch (AbsentInformationException ex) {
            //System.out.println("AIE: can't list variables in " + sf.location());
        }            
        if (returnValue!=null) {
            result.add("__return__", returnValue);
            result_ordered.add("__return__");
	}        
	return Json.createObjectBuilder()
	    .add("func_name", sf.location().method().name()+":"+sf.location().lineNumber())
	    .add("encoded_locals", result)
	    .add("ordered_varnames", result_ordered)
	    .add("parent_frame_id_list", Json.createArrayBuilder())
	    .add("is_highlighted", highlight)//frame_stack.size()-1)
	    .add("is_zombie", false)
	    .add("is_parent", false)
	    .add("unique_hash", ""+frame_ticker)//frame_stack.get(level))
	    .add("frame_id", frame_ticker);//frame_stack.get(level));
    }
    
   void convertHeap(JsonObjectBuilder result) {
        heap_done = new java.util.TreeSet<>();
        while (!heap.isEmpty()) {
            Map.Entry<Long, ObjectReference> first = heap.firstEntry();
            ObjectReference obj = first.getValue();
            long id = first.getKey();
            heap.remove(id);
            if (heap_done.contains(id))
                continue;
            heap_done.add(id);
            result.add(""+id, convertObject(obj, true));
        }
    }
    
    private JsonValue convertObject(ObjectReference obj, boolean fullVersion) {
        JsonArrayBuilder result = Json.createArrayBuilder();

	// abbreviated versions are for references to objects
	if (!fullVersion) {
	    result.add("REF").add(obj.uniqueID());
	    heap.put(obj.uniqueID(), obj);
	    return result.build();
	}

	// full versions are for describing the objects themselves,
	// in the heap
        else if (obj instanceof ArrayReference) {
	    result.add("LIST");
	    heap_done.add(obj.uniqueID());
	    for (Value v : ((ArrayReference)obj).getValues()) {
		result.add(convertValue(v));
	    }
	    return result.build();
	}
        else if (obj instanceof StringReference) {
            return Json.createArrayBuilder()
		.add("HEAP_PRIMITIVE")
		.add("String")
		.add(jsonString(((StringReference)obj).value()))
		.build();
        }
        // do we need special cases for ClassObjectReference, ThreadReference,.... ?
        
        // now deal with Objects. 
        else {
            heap_done.add(obj.uniqueID());
            result.add("INSTANCE")
                .add(obj.referenceType().name());
            if (showGuts(obj.referenceType())) {
                // fields: -inherited -hidden +synthetic
                // visibleFields: +inherited -hidden +synthetic
                // allFields: +inherited +hidden +repeated_synthetic
                for (Map.Entry<Field,Value> me :  
                         obj.getValues(obj.referenceType().visibleFields()).entrySet()) {
                    if (!me.getKey().isStatic()
                        //&& !me.getKey().isSynthetic() // uncomment to hide synthetic fields (this$0 or val$lv)
                        )
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
	    ObjectReference obj = (ObjectReference)v;
            heap.put(obj.uniqueID(), obj);
            return convertObject(obj, false);
        }
    }

    static JsonObject compileErrorOutput(String usercode, String errmsg, long row, long col) {
	return output(usercode,
	    Json.createArrayBuilder().add
	    (Json.createObjectBuilder()
	     .add("line", ""+row)
	     .add("event", "uncaught_exception")
	     .add("offset", ""+col)
	     .add("exception_msg", errmsg))
                      .build()
                      );
    }

    static JsonObject output(String usercode, JsonArray trace) {
        return Json.createObjectBuilder()
            .add("code", usercode)
            .add("trace", trace).build();
    }

    String exceptionMessage(ExceptionEvent event) {
        String msg = "";
        try {
            ObjectReference exc = event.exception();
            msg = exc.toString();
            ReferenceType excType = exc.referenceType();
            //for (Field ff : excType.allFields())
            //  System.out.println(ff);
            ThreadReference t = event.thread();
            //            System.out.println(t);
            //Method mm = excType.methodsByName("getMessage").get(0);
            //System.out.println(mm);
            Field ff = excType.fieldByName("detailMessage");
            StringReference sr = (StringReference) exc.getValue(ff);
            //System.out.println(sr);
            return excType.name()+": "+sr.value();
            //System.out.println("type: "+event.exception().type());
            //System.out.println("message: "+msg);
        }
        catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
            return msg + "fail dynamic message lookup";
        }
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

    // add at specified position, or end if -1
    static JsonArray jsonModifiedArray(JsonArray arr, int tgt, JsonValue v) {
        JsonArrayBuilder result = Json.createArrayBuilder();
        int i = 0;
        for (JsonValue w : arr) {
            if (i == tgt) result.add(v);
            else result.add(w);
            i++;
        }
	if (tgt == -1)
	    result.add(v);
        return result.build();
    }

}