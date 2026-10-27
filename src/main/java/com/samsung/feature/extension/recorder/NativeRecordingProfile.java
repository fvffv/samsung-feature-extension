package com.samsung.feature.extension.recorder;

import android.media.*;
import android.os.Build;
import java.util.*;

/** Chooses the highest supported hardware AVC frame rate, then bitrate, at native size. */
public final class NativeRecordingProfile {
    public final String codec;
    public final int width,height,fps,bitrate,bitrateMode;
    private NativeRecordingProfile(String c,int w,int h,int f,int b,int mode) {
        codec=c; width=w; height=h; fps=f; bitrate=b; bitrateMode=mode;
    }
    public static int aligned(int value,int alignment) {
        return Math.max(alignment,(value/alignment)*alignment);
    }
    public static NativeRecordingProfile choose(int width,int height,float displayMax) {
        NativeRecordingProfile best=null;
        for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if(!info.isEncoder()||(Build.VERSION.SDK_INT>=29&&!info.isHardwareAccelerated())) continue;
            if(Build.VERSION.SDK_INT<29&&(info.getName().startsWith("OMX.google.")||info.getName().startsWith("c2.android."))) continue;
            for(String type:info.getSupportedTypes()) {
                if(!"video/avc".equalsIgnoreCase(type)) continue;
                try {
                    MediaCodecInfo.CodecCapabilities caps=info.getCapabilitiesForType(type);
                    MediaCodecInfo.VideoCapabilities video=caps.getVideoCapabilities();
                    int w=aligned(width,Math.max(2,video.getWidthAlignment()));
                    int h=aligned(height,Math.max(2,video.getHeightAlignment()));
                    if(!video.isSizeSupported(w,h)) continue;
                    int fps=(int)Math.floor(Math.min(displayMax+.01f,video.getSupportedFrameRatesFor(w,h).getUpper()));
                    if(fps<1) continue;
                    int bitrate=video.getBitrateRange().getUpper();
                    MediaCodecInfo.EncoderCapabilities encoder=caps.getEncoderCapabilities();
                    int mode=encoder.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                        ?MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR:MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
                    MediaFormat test=MediaFormat.createVideoFormat(type,w,h);
                    test.setInteger(MediaFormat.KEY_FRAME_RATE,fps); test.setInteger(MediaFormat.KEY_BIT_RATE,bitrate);
                    test.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                    if(!caps.isFormatSupported(test)) continue;
                    if(best==null||fps>best.fps||(fps==best.fps&&bitrate>best.bitrate))
                        best=new NativeRecordingProfile(info.getName(),w,h,fps,bitrate,mode);
                } catch(RuntimeException ignored) {}
            }
        }
        if(best==null) throw new IllegalArgumentException("No hardware AVC profile for "+width+"x"+height);
        return best;
    }
    public void apply(MediaFormat format) {
        format.setInteger(MediaFormat.KEY_WIDTH,width); format.setInteger(MediaFormat.KEY_HEIGHT,height);
        format.setInteger(MediaFormat.KEY_FRAME_RATE,fps); format.setInteger(MediaFormat.KEY_BIT_RATE,bitrate);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE,bitrateMode);
        format.setInteger(MediaFormat.KEY_OPERATING_RATE,fps);
        // Samsung pins AVC Main / Level 4, which excludes native QHD high-frame-rate capture.
        format.removeKey(MediaFormat.KEY_PROFILE); format.removeKey(MediaFormat.KEY_LEVEL);
    }
    @Override public String toString() { return codec+" "+width+"x"+height+" @"+fps+"fps "+bitrate+"bit/s"; }
}

