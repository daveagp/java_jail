package traceprinter.ramtools;


/****
Usage: 
 - takes one command line argument, which is a class name;
 - takes stdin that is a JSON object, where "bytecodes" is a map from class names to bytecodes
 - loads those classes and runs args[0]'s public static void main(String[] args)
 - the first line of output is either 'Error' or 'Success'
 - Success means public static main(String[]) was found (we called Method.invoke), not that the invoke was trouble-free

Sample usage: let's say we feed CompileToBytes this input:

{"A":"public class A{public static void main(String[]args){System.out.println(\"hi\");}}"}

it produces this output:

{"status":"Success","bytecodes":{"A":"CAFEBABE0000003300220A0006001409001500160800170A0018001907001A07001B0100063C696E69743E010003282956010004436F646501000F4C696E654E756D6265725461626C650100124C6F63616C5661726961626C655461626C65010004746869730100034C413B0100046D61696E010016285B4C6A6176612F6C616E672F537472696E673B2956010004617267730100135B4C6A6176612F6C616E672F537472696E673B01000A536F7572636546696C65010006412E6A6176610C0007000807001C0C001D001E010002686907001F0C00200021010001410100106A6176612F6C616E672F4F626A6563740100106A6176612F6C616E672F53797374656D0100036F75740100154C6A6176612F696F2F5072696E7453747265616D3B0100136A6176612F696F2F5072696E7453747265616D0100077072696E746C6E010015284C6A6176612F6C616E672F537472696E673B2956002100050006000000000002000100070008000100090000002F00010001000000052AB70001B100000002000A00000006000100000001000B0000000C000100000005000C000D00000009000E000F00010009000000330002000100000009B200021203B60004B100000002000A00000006000100000001000B0000000C00010000000900100011000000010012000000020013"}}

If we pipe this into RAMRun A

it prints out

Success: found A.main. Invoking...
hi

*/


import traceprinter.shoelace.*;
import traceprinter.ramtools.*;
import java.lang.reflect.*;
import java.io.*;
import java.util.Map;
import javax.json.*;

public class RAMRun {
    public static void main(String[] args) {
        JsonObject jo = null;
        try {
            jo = (JsonObject)
                Json.createReader(new InputStreamReader
                                  (System.in, "UTF-8"))
                .readObject();
        } 
        catch (IOException e) {
            System.out.println("Error: Could not parse input");
            e.printStackTrace();
        }
        Method main = null;
        try {
            JsonObject classes = jo.getJsonObject("bytecodes");

            if (classes == null) {
                System.out.println("Error: Could not find 'bytecodes' in input. Input was:");
                System.out.println(jo);
                return;
            }

            ByteClassLoader bcl = new ByteClassLoader();
            
            for (Map.Entry<String, JsonValue> me : classes.entrySet()) {
                String classname = me.getKey();
                String classfile = ((JsonString)me.getValue()).getString();

                byte[] bytecode = new byte[classfile.length()/2];
                
                for (int i=0; i<classfile.length()/2; i++)
                    bytecode[i] = (byte)Integer.parseInt(classfile.substring(2*i, 2*i+2), 16);
                
                bcl.define(classname, bytecode);
            }

            // doesn't actually work.
            //Thread.currentThread().setContextClassLoader(bcl);
            //Class definedClass = Class.forName("A");
            
            Class mainClass = ByteClassLoader.publicFindClass(args[0]);

            main = mainClass.getMethod("main", String[].class);
        }
        catch (Throwable t) {
            System.out.println("Error: could not find class or main method");
            t.printStackTrace();
            return;
        }
        if (main.getModifiers() != (Modifier.PUBLIC | Modifier.STATIC)) {
            System.out.println("Error: main is not public static");
            return;
        }
        try {
            System.out.println("Success: found "+args[0]+".main. Invoking...");
            main.invoke(null, (Object)new String[0]);
        } catch (IllegalAccessException e) {
            //System.out.println("Error: illegal access exception");
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.getCause().printStackTrace();
        }
    }
}