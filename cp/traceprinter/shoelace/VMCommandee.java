package traceprinter.shoelace;

import java.lang.reflect.*;

/***
This class receives commands from traceprinter.VMCommander
telling what user code should be run. (Note that VMCommander
is in the debugger JVM, and VMCommandee is in the debugee.)
***/

public class VMCommandee {

    // returns null if everything worked
    // else, returns an error message
    public String runMain(String className, String[] args, String stdin) {

        Class<?> target;
        try {
            target = ByteClassLoader.publicFindClass(className);
        } catch (ClassNotFoundException e) {
            return "Internal error: main class "+className+" not found";
        }

        Method main;
        try {
            main = target.getMethod("main", new Class[]{String[].class});
        } catch (NoSuchMethodException e) { 
            return "Class "+className+" needs public static void main(String[] args)";
        }

        if (stdin != null)
            try {
                System.setIn(new java.io.ByteArrayInputStream(stdin.getBytes("UTF-8")));
            }
            catch (SecurityException | java.io.UnsupportedEncodingException e) {
                return "Internal error: can't setIn";
            }

        int modifiers = main.getModifiers();
        if (modifiers != (Modifier.PUBLIC | Modifier.STATIC))
            return "Class "+className+" needs public static void main(String[] args)";
        try {
            // first is null since it is a static method
            main.invoke(null, new Object[]{args});
            return null;
        }
        catch (IllegalAccessException e) {
            return "Internal error invoking main";
        }
        catch (InvocationTargetException e) {
            RuntimeException throwMe = null;
            try {
                // throw it again in a way that the tracer can see it
                throwMe = (RuntimeException)e.getTargetException();
            }
            catch (ClassCastException cce) {
                return "Internal error handling exception";
            }
            throw throwMe;
        }
    }

}