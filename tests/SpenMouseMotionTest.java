import com.samsung.feature.extension.spen.SpenMouseMotion;
public final class SpenMouseMotionTest {
    static void check(boolean value) { if(!value) throw new AssertionError(); }
    static int settle(SpenMouseMotion m,long start) {
        int x=0;
        for(long t=start;t<=start+200;t+=8) { m.advance(t); x+=m.takeX(); m.takeY(); }
        check(!m.hasPending()); return x;
    }
    public static void main(String[] args) {
        SpenMouseMotion m=new SpenMouseMotion(); m.setSmoothing(0);
        m.move(.01f,.02f,1,0); check(m.takeX()==10&&m.takeY()==-20);
        m.pressed=true; m.move(1,1,3,35); check(m.takeX()==3000&&m.takeY()==-3000&&m.pressed);
        m.move(.01f,-.02f,1,70); check(m.takeX()==10&&m.takeY()==20);
        m.move(.0005f,0,1,105); check(m.takeX()==0);
        m.move(.0005f,0,1,140); check(m.takeX()==1);
        m.move(Float.NaN,1,1,175); m.move(1,Float.POSITIVE_INFINITY,1,175);
        m.move(1,1,Float.NaN,175); check(m.takeX()==0&&m.takeY()==0);
        m.move(.1f,-.1f,.25f,210); check(m.takeX()==25&&m.takeY()==25);
        m.reset(); check(!m.pressed&&!m.hasPending()&&m.takeX()==0);
        m.setSmoothing(50); m.move(.035f,0,1,0);
        int sum=0,events=0,largest=0;
        for(long t=8;t<=200;t+=8) {
            m.advance(t); int x=m.takeX(); sum+=x;
            if(x!=0) events++; largest=Math.max(largest,x);
        }
        check(sum==35&&events>=3&&largest<35&&!m.hasPending());
        System.out.println("35-unit step: events="+events+", largest="+largest+", sum="+sum);
        // Zero-mean tremor: less total travel, no permanent drift.
        for(int strength:new int[]{50,100}) {
            m.reset(); m.setSmoothing(strength); int position=0,travel=0;
            for(int t=0;t<1400;t++) {
                if(t%35==0) m.move(t%70==0?.004f:-.004f,0,1,t);
                if(t%8==0) { m.advance(t); int x=m.takeX(); position+=x; travel+=Math.abs(x); }
            }
            position+=settle(m,1400);
            check(position==0&&travel<160);
            System.out.println("jitter strength="+strength+", travel="+travel+" (raw=160), final="+position);
        }
        // Variable timing and held-button state with both axes.
        for(int strength:new int[]{0,25,50,100}) {
            m.reset(); m.setSmoothing(strength); m.pressed=true;
            int x=0,y=0; long now=0;
            for(int i=0;i<200;i++) {
                m.move(.01f,-.005f,1,now); x+=m.takeX(); y+=m.takeY();
                now+=i%3==0?48:35;
                m.advance(now); x+=m.takeX(); y+=m.takeY();
            }
            m.flush(); x+=m.takeX(); y+=m.takeY();
            check(x==2000&&y==1000&&m.pressed&&!m.hasPending());
            check(settle(m,now+1000)==0);
        }
        // Flush before changing button state; no delayed drag after release.
        m.reset(); m.setSmoothing(100); m.pressed=true; m.move(.01f,0,1,0);
        m.flush(); check(m.takeX()==10&&m.pressed); m.pressed=false;
        check(settle(m,8)==0&&!m.pressed);
        // Tiny intended motion is conserved instead of lost in a dead zone.
        m.reset(); int tiny=0;
        for(int i=0;i<20;i++) { m.move(.0005f,0,1,i*250); tiny+=settle(m,i*250+8); }
        check(tiny==10);
        m.move(.05f,0,1,6000); m.setSmoothing(0); check(m.takeX()==50&&!m.hasPending());
        m.setSmoothing(100); m.move(.05f,0,1,6100); m.reset(); check(settle(m,6200)==0);
        System.out.println("SpenMouseMotion: all checks passed");
    }
}
