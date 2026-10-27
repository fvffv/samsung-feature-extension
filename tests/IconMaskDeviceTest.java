import android.graphics.*;
import android.graphics.drawable.*;
import com.samsung.feature.extension.icons.SystemIconMask;
public class IconMaskDeviceTest {
    static void check(boolean value,String text) { if(!value) throw new AssertionError(text); }
    static Bitmap render(Drawable drawable) {
        Bitmap target=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888);
        drawable.setBounds(0,0,200,200); drawable.draw(new Canvas(target)); return target;
    }
    public static void main(String[] args) {
        Bitmap square=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888); square.eraseColor(Color.RED);
        BitmapDrawable original=new BitmapDrawable(null,square);
        original.setBounds(0,0,180,180);
        Drawable masked=SystemIconMask.normalize(original); check(masked!=original,"Opaque square must be masked");
        check(masked.getBounds().equals(new Rect(0,0,180,180)),"Existing TextView compound bounds preserved");
        check(original.getBounds().equals(masked.getBounds()),"Child bounds preserved");
        Bitmap out=render(masked);
        check(Color.alpha(out.getPixel(0,0))==0,"Outside corner transparent");
        check(out.getPixel(100,100)==Color.RED,"Center unchanged");
        check(SystemIconMask.normalize(masked)==masked,"No double wrapping");
        check(Color.alpha(render(masked.getConstantState().newDrawable()).getPixel(0,0))==0,"ConstantState retains mask");
        Bitmap round=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(round); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.BLUE);
        c.drawRoundRect(0,0,100,100,26,26,p);
        BitmapDrawable rounded=new BitmapDrawable(null,round);
        check(SystemIconMask.normalize(rounded)==rounded,"Already shaped icons unchanged");
        // Mutating the source invalidates the corner classification cache.
        square.eraseColor(Color.TRANSPARENT);
        check(SystemIconMask.normalize(original)==original,"Generation-id invalidation");
        check(SystemIconMask.normalize(null)==null,"Null supported");
        System.out.println("IconMaskDevice: all checks passed");
        System.exit(0);
    }
}
