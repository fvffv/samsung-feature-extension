package com.samsung.feature.extension.freeform;

/** Uniform density scaling; no global display-density changes. */
public final class FreeformScaleMath {
    private FreeformScaleMath() {}
    public static int density(int baseDpi,int displayWidth,int displayHeight,int width,int height) {
        if(baseDpi<=0||displayWidth<=0||displayHeight<=0||width<=0||height<=0) return 0;
        int portraitWidth=Math.min(displayWidth,displayHeight);
        int landscapeWidth=Math.max(displayWidth,displayHeight);
        int referenceWidth=width>height?landscapeWidth:portraitWidth;
        double ratio=Math.min(1.0,(double)width/referenceWidth);
        return Math.min(baseDpi,Math.max(72,(int)Math.round(baseDpi*ratio)));
    }
}

