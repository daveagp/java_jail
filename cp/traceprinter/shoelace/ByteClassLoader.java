package traceprinter.shoelace;
import java.util.*;
public class ByteClassLoader extends ClassLoader {

/*** 
 A class loader that uses bytecode directly
 Based on: http://stackoverflow.com/questions/1781091/
***/

    static ByteClassLoader instance;

    private TreeMap<String, byte[]> definitions = new TreeMap<>();

    public ByteClassLoader() {
        instance = this;
    }

    public void define(String className, byte[] bytecode) {
        definitions.put(className, bytecode);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytecode = definitions.get(name);
        return defineClass(name, bytecode, 0, bytecode.length); 
    }

    public static Class<?> publicFindClass(final String name) throws ClassNotFoundException {
        return instance.findClass(name);
    }

}