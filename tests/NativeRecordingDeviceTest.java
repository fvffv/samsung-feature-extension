import android.media.*;
import com.samsung.feature.extension.recorder.NativeRecordingProfile;
public class NativeRecordingDeviceTest {
    public static void main(String[] args) throws Exception {
        int width=args.length>0?Integer.parseInt(args[0]):1440;
        int height=args.length>1?Integer.parseInt(args[1]):3120;
        NativeRecordingProfile plan=NativeRecordingProfile.choose(width,height,120);
        System.out.println("Plan: "+plan);
        MediaFormat f=MediaFormat.createVideoFormat("video/avc",plan.width,plan.height);
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1); plan.apply(f);
        MediaCodec codec=MediaCodec.createByCodecName(plan.codec);
        android.view.Surface surface=null;
        try {
            codec.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface=codec.createInputSurface(); codec.start(); codec.stop();
            System.out.println("Hardware codec configure/start passed");
        } finally { if(surface!=null) surface.release(); codec.release(); }
        System.exit(0);
    }
}
