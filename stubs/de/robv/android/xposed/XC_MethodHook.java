package de.robv.android.xposed;

public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;

        public Object getResult() {
            return null;
        }

        public Throwable getThrowable() {
            return null;
        }

        public void setResult(Object result) {
        }
    }

    public final class Unhook {
        public void unhook() {
        }
    }
}
