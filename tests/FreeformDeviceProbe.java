import android.app.ActivityTaskManager;
import android.os.Looper;
import android.window.WindowContainerTransaction;
import android.window.WindowOrganizer;
import java.lang.reflect.*;

/** Root-only diagnostic: may adjust only the module's own test window. */
public class FreeformDeviceProbe {
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        if(args.length==0) {
            ClassLoader loader=FreeformDeviceProbe.class.getClassLoader();
            Class<?> service=Class.forName("com.android.server.wm.ActivityTaskManagerService",false,loader);
            System.out.println("Ready: "+service.getDeclaredMethod("onSystemReady"));
            for(String name:new String[]{"mContext","mH","mGlobalLock","mRootWindowContainer"}) System.out.println("Field: "+service.getDeclaredField(name));
            Class<?> fragment=Class.forName("com.android.server.wm.TaskFragment",false,loader);
            for(Method m:fragment.getDeclaredMethods()) if(m.getName().equals("computeConfigResourceOverrides")) System.out.println("Compute: "+m);
        } else {
            int id=Integer.parseInt(args[0]),dpi=Integer.parseInt(args[1]);
            boolean found=false;
            for(ActivityTaskManager.RootTaskInfo task:ActivityTaskManager.getService().getAllRootTaskInfos()) {
                if(task.taskId!=id) continue;
                if(task.baseActivity==null||!task.baseActivity.getPackageName().equals("com.samsung.feature.extension")||task.getWindowingMode()!=5) throw new SecurityException("Module freeform task only");
                new WindowOrganizer().applyTransaction(new WindowContainerTransaction().setDensityDpi(task.token,dpi));
                System.out.println("Applied module task "+id+" density="+dpi); found=true;
            }
            if(!found) throw new IllegalArgumentException("Module task not found");
        }
        System.exit(0);
    }
}
