package cats;
import java.util.ArrayList;
public class Bella {
    private static int j;
    public static int k;
    private int jj;
    public int kk;
    static ArrayList<String> speeches = new ArrayList<>();
    private String grow(String noise) {
        return noise + "meow";
    }
    void foo() {
        String noise = "";
        for (int i=0; i<3; i++, j++, k++, jj++, kk++) {
            noise = grow(noise);
            System.out.println(noise);
        }
    }
    public static void main(String[] args) {
        String x = "x";
        System.out.println("hi");
        new Bella().foo();
        Math.sqrt(2.0);
        System.out.println("hi");
        for (int i=0; i<2; i++) x = x+x;
    }
}