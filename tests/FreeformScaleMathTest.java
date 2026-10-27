import com.samsung.feature.extension.freeform.FreeformScaleMath;
public class FreeformScaleMathTest {
    static void check(boolean value) { if(!value) throw new AssertionError(); }
    public static void main(String[] args) {
        check(FreeformScaleMath.density(600,1440,3120,720,1560)==300);
        check(FreeformScaleMath.density(600,1440,3120,1080,2340)==450);
        check(FreeformScaleMath.density(600,1440,3120,1560,720)==300);
        check(FreeformScaleMath.density(600,3120,1440,720,1560)==300);
        check(FreeformScaleMath.density(600,1440,3120,1440,3120)==600);
        check(FreeformScaleMath.density(600,1440,3120,2880,6240)==600);
        check(FreeformScaleMath.density(600,1440,3120,10,20)==72);
        check(FreeformScaleMath.density(600,0,3120,720,1560)==0);
        check(FreeformScaleMath.density(600,1440,3120,0,1560)==0);
        // The same UI width in dp at each proportional size.
        for(int width=360;width<=1440;width+=120) {
            int dpi=FreeformScaleMath.density(600,1440,3120,width,width*13/6);
            check(Math.abs(width*160.0/dpi-384)<1);
        }
        System.out.println("FreeformScaleMath: all checks passed");
    }
}
