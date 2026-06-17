import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ForceStackRecents implements IXposedHookLoadPackage {
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        String pkg = p.packageName;

        // Hook OplusLauncher — all recents style decisions are here in Android 16 ColorOS
        if (pkg.equals("com.android.launcher")) {
            try {
                // Load Context class at runtime (no android.jar at compile time)
                Class<?> ctxClass = p.classLoader.loadClass("android.content.Context");

                // Hook 1: OplusStackRecentsConfig$mEnable$1.invoke(Context) -> return true
                // This lambda (Function1<Context, Boolean>) checks 3 conditions:
                //   isStackSupported() && !OplusGridRecentsConfig.isEnable() && getCurrentRecentStyle()=="stacked"
                try {
                    Class<?> cl = p.classLoader.loadClass(
                        "com.oplus.quickstep.layout.stack.OplusStackRecentsConfig$mEnable$1"
                    );
                    XposedHelpers.findAndHookMethod(cl, "invoke",
                        ctxClass,
                        new XC_MethodReplacement() {
                            protected Object replaceHookedMethod(MethodHookParam param) {
                                return Boolean.TRUE;
                            }
                        }
                    );
                    XposedBridge.log("FSR: hooked mEnable$1.invoke(Context) -> true");
                } catch (Exception e) {
                    XposedBridge.log("FSR mEnable$1: " + e.getMessage());
                }

                // Hook 2: RecentStyleLayoutPreference.getCurrentRecentStyle(Context) -> return "stacked"
                try {
                    Class<?> cl = p.classLoader.loadClass(
                        "com.oplus.quickstep.locksetting.ui.RecentStyleLayoutPreference"
                    );
                    XposedHelpers.findAndHookMethod(cl, "getCurrentRecentStyle",
                        ctxClass,
                        new XC_MethodReplacement() {
                            protected Object replaceHookedMethod(MethodHookParam param) {
                                return "stacked";
                            }
                        }
                    );
                    XposedBridge.log("FSR: hooked getCurrentRecentStyle -> stacked");
                } catch (Exception e) {
                    XposedBridge.log("FSR getCurrentRecentStyle: " + e.getMessage());
                }

                // Hook 3: OplusStackRecentsConfig.isStackSupported() -> return true
                try {
                    Class<?> cl = p.classLoader.loadClass(
                        "com.oplus.quickstep.layout.stack.OplusStackRecentsConfig"
                    );
                    XposedHelpers.findAndHookMethod(cl, "isStackSupported",
                        new XC_MethodReplacement() {
                            protected Object replaceHookedMethod(MethodHookParam param) {
                                return Boolean.TRUE;
                            }
                        }
                    );
                    XposedBridge.log("FSR: hooked isStackSupported -> true");
                } catch (Exception e) {
                    XposedBridge.log("FSR isStackSupported: " + e.getMessage());
                }

            } catch (Exception e) {
                XposedBridge.log("FSR init: " + e.getMessage());
            }
        }
    }
}
