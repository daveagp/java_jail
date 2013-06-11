package traceprinter.shoelace;

import java.lang.reflect.*;

/***
This class receives commands from traceprinter.VMCommander
telling what user code should be run. (Note that VMCommander
is in the dubgger JVM, and VMCommandee is in the debugee.)
***/

public class VMCommandee {

    // returns null if everything worked
    // else, returns an error message
    public String runMainNoArgs(String className) {

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

        int modifiers = main.getModifiers();
        if (modifiers != (Modifier.PUBLIC | Modifier.STATIC))
            return "Class "+className+" needs public static void main(String[] args)";
        try {
            // first is null since it is a static method
            main.invoke(null, new Object[]{new String[0]});
            return null;
        }
        catch (IllegalAccessException e) {
            return "Internal error invoking main";
        }
        catch (InvocationTargetException e) {
            // uncaught exception, but this is handled by normal machinery
            return null;
        }
    }

}