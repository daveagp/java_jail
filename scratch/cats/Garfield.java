package cats;
import java.lang.reflect.Field;
public class Garfield {
    int belly;
     public class Masagna {
         int a = 3;
         int z = 100;
        Masagna() {
            belly++;
        }
     }
     public class Lasagna extends Masagna {
         String a = "three";
         Lasagna() {
             super();
             Masagna m = (Masagna)this;
             System.out.println(m.a);
             System.out.println(super.a);
         }
     }
    public static void main(String[] args) {
        final Garfield g1 = new Garfield();
        final Garfield g2 = new Garfield();
        System.out.println(new Object(){public String toString() {
            System.out.println(g1.belly+" "+g2.belly);
            Lasagna l11 = g1.new Lasagna();
            System.out.println(g1.belly+" "+g2.belly);
            Lasagna l12 = g1.new Lasagna();
            System.out.println(g1.belly+" "+g2.belly);
            Lasagna l21 = g2.new Lasagna();
            System.out.println(g1.belly+" "+g2.belly);
            Lasagna l22 = g2.new Lasagna();
            System.out.println(g1.belly+" "+g2.belly);
            return "woo";
        }});
        /*try {
            for (Lasagna l : new Lasagna[]{l11, l12, l21, l22}) {
                Field[] fs = Lasagna.class.getDeclaredFields();
                for (Field f:fs)
                    System.out.println(f.getName()+" "+f.isSynthetic());
                Field f = Lasagna.class.getDeclaredField("this$0");
                //f.setAccessible(true);
                Garfield g = (Garfield) f.get(l);
                System.out.println(g);
            }
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            System.out.println("NSFE");
        }*/
    }
}