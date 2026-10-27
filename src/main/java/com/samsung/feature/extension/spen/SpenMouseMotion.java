package com.samsung.feature.extension.spen;

/**
 * Time-based adaptive low-pass on integrated relative position, independent of Android.
 * Pending displacement is conserved (no dead zone or prediction), including while dragging.
 */
public final class SpenMouseMotion {
    public boolean pressed;
    private double x,y,pendingX,pendingY;
    private int smoothing=50;
    private long lastInput=-1,lastStep=-1;
    private double tauMs=22;

    public void setSmoothing(int percent) {
        smoothing=Math.max(0,Math.min(100,percent));
        if(smoothing==0) flush();
    }
    public void move(float dx,float dy,float sensitivity,long now) {
        if(!Float.isFinite(dx)||!Float.isFinite(dy)||!Float.isFinite(sensitivity)||sensitivity<=0) return;
        advance(now); // Only age displacement already received, never the new sample.
        double px=Math.max(-1,Math.min(1,dx))*1000*Math.min(3,sensitivity);
        double py=-Math.max(-1,Math.min(1,dy))*1000*Math.min(3,sensitivity);
        double dt=lastInput<0?35:Math.max(8,Math.min(100,now-lastInput));
        double speed=Math.hypot(px,py)*1000/dt;
        // More damping for small hand tremor, less lag during deliberate fast motion.
        tauMs=smoothing/100.0*(12+32/(1+speed/250));
        pendingX+=px; pendingY+=py;
        lastInput=now; lastStep=now;
        if(smoothing==0) flush();
    }
    public boolean advance(long now) {
        if(!hasPending()) { lastStep=now; return false; }
        long dt=Math.max(0,now-lastStep);
        if(dt==0) return true;
        lastStep=now;
        if(smoothing==0||now-lastInput>=160) { flush(); return false; }
        double alpha=-Math.expm1(-Math.min(dt,200)/Math.max(1,tauMs));
        double dx=pendingX*alpha,dy=pendingY*alpha;
        pendingX-=dx; pendingY-=dy; x+=dx; y+=dy;
        if(Math.abs(pendingX)<.01&&Math.abs(pendingY)<.01) flush();
        return hasPending();
    }
    public boolean hasPending() { return pendingX!=0||pendingY!=0; }
    /** Before a button edge, finish prior movement under the prior button state. */
    public void flush() { x+=pendingX; y+=pendingY; pendingX=0; pendingY=0; }
    public int takeX() { int whole=whole(x); x-=whole; return whole; }
    public int takeY() { int whole=whole(y); y-=whole; return whole; }
    private static int whole(double value) {
        // Numerical residue must not lose the final integer unit after resampling.
        return (int)(value+Math.copySign(1e-7,value));
    }
    public void reset() {
        x=0; y=0; pendingX=0; pendingY=0;
        lastInput=-1; lastStep=-1; pressed=false;
    }
}

