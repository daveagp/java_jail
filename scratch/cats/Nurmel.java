package cats;
import java.lang.reflect.Field;
public class Nurmel {
    static Object[] foo = new Object[4];
    static int foos = 0;
    public static void bar(int x) {
        final int sqr = x*x;
        final int sqrp = x*x*x;
        for (int i=0; i<2; i++) 
            foo[foos++] = new Object(){void baz() {//System.out.println
            int z = (sqr+sqrp);}}; 
    }
    public static void main(String[] args) {
        bar(1);
        bar(2);
        try {
            for (Object o : foo) {
                Field[] fs = o.getClass().getDeclaredFields();
                String e;for (Field f:fs)
                    //System.out.println
                e = (f.getName()+" "+f.isSynthetic());
                Field f = o.getClass().getDeclaredField("val$sqrp");
                //f.setAccessible(true);
                int g = (Integer) f.get(o);
                //System.out.println(g);
            }
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            //System.out.println("NSFE");
        }
    }
}