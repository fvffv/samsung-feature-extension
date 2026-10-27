package com.samsung.feature.extension.icons;

import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.Resources;
import java.util.WeakHashMap;

/** Clips opaque legacy square icons to Android's current adaptive-icon mask. */
public final class SystemIconMask {
    private static final WeakHashMap<Bitmap,Sample> SAMPLES=new WeakHashMap<>();
    private SystemIconMask() {}
    public static Drawable normalize(Drawable source) {
        if(!(source instanceof BitmapDrawable)||source instanceof Masked) return source;
        Bitmap bitmap=((BitmapDrawable)source).getBitmap();
        if(bitmap==null||bitmap.isRecycled()||bitmap.getWidth()<16||bitmap.getHeight()<16) return source;
        int generation=bitmap.getGenerationId();
        boolean opaque;
        synchronized(SAMPLES) {
            Sample sample=SAMPLES.get(bitmap);
            if(sample!=null&&sample.generation==generation) opaque=sample.opaque;
            else {
                opaque=hasSquareCorners(bitmap);
                SAMPLES.put(bitmap,new Sample(generation,opaque));
            }
        }
        return opaque?new Masked(source):source;
    }
    private static boolean hasSquareCorners(Bitmap bitmap) {
        Bitmap readable=bitmap;
        try {
            if(bitmap.getConfig()==Bitmap.Config.HARDWARE) {
                readable=bitmap.copy(Bitmap.Config.ARGB_8888,false);
                if(readable==null) return false;
            }
            int w=readable.getWidth(),h=readable.getHeight();
            // Sample just inside all four corners, ignoring one-pixel export borders.
            for(float f:new float[]{.02f,.04f}) {
                int x=Math.max(0,(int)(w*f)),y=Math.max(0,(int)(h*f));
                if((readable.getPixel(x,y)>>>24)<240||(readable.getPixel(w-1-x,y)>>>24)<240
                    ||(readable.getPixel(x,h-1-y)>>>24)<240||(readable.getPixel(w-1-x,h-1-y)>>>24)<240) return false;
            }
            return true;
        } catch(RuntimeException ignored) { return false; }
        finally { if(readable!=bitmap&&readable!=null) readable.recycle(); }
    }
    private static final class Sample {
        final int generation; final boolean opaque;
        Sample(int generation,boolean opaque) { this.generation=generation; this.opaque=opaque; }
    }
    private static final class Masked extends DrawableWrapper {
        private final AdaptiveIconDrawable mask=new AdaptiveIconDrawable(
            new ColorDrawable(Color.BLACK),new ColorDrawable(Color.TRANSPARENT));
        // DrawableWrapper's constructor resets its child to the wrapper's empty bounds.
        // Capture the existing icon bounds before calling super, then restore both.
        Masked(Drawable source) { this(source,new Rect(source.getBounds())); }
        private Masked(Drawable source,Rect originalBounds) { super(source); setBounds(originalBounds); }
        @Override protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds); mask.setBounds(bounds);
        }
        @Override public void draw(Canvas canvas) {
            int save=canvas.save();
            try { canvas.clipPath(mask.getIconMask()); super.draw(canvas); }
            finally { canvas.restoreToCount(save); }
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public void getOutline(Outline outline) { mask.getOutline(outline); }
        @Override public ConstantState getConstantState() {
            ConstantState inner=getDrawable().getConstantState();
            return inner==null?null:new MaskState(inner);
        }
    }
    private static final class MaskState extends Drawable.ConstantState {
        final Drawable.ConstantState inner;
        MaskState(Drawable.ConstantState inner) { this.inner=inner; }
        @Override public Drawable newDrawable() { return new Masked(inner.newDrawable()); }
        @Override public Drawable newDrawable(Resources resources) { return new Masked(inner.newDrawable(resources)); }
        @Override public int getChangingConfigurations() { return inner.getChangingConfigurations(); }
    }
}

