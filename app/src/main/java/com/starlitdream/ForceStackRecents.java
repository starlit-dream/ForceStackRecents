import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ForceStackRecents implements IXposedHookLoadPackage {
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        String pkg = p.packageName;

        // Hook SystemUI - CommonSettingsValueProxy controls recents style
        if (pkg.equals("com.android.systemui")) {
            try {
                // New location on ColorOS 16 / Android 16
                Class cl = p.classLoader.loadClass(
                    "com.oplusos.systemui.common.settingsvalue.CommonSettingsValueProxy"
                );
                XposedHelpers.findAndHookMethod(cl, "getSettingsValue",
                    String.class, String.class,
                    new XC_MethodHook() {
                        protected Object afterHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[0];
                            if (key != null && (
                                key.contains("recent_style") ||
                                key.contains("recents_style") ||
                                key.contains("recents_app_style") ||
                                key.contains("oplus_recent_style")
                            )) {
                                XposedBridge.log("FSR_SystemUI: intercepted " + key);
                                // Replace the return value
                                param.setResult("stacked");
                            }
                            return null; // Use original result if not matched
                        }
                    }
                );
                XposedBridge.log("FSR_SystemUI: hooked CommonSettingsValueProxy");
            } catch (Exception e) {
                XposedBridge.log("FSR_SystemUI: " + e.getMessage());
            }

            // Also try hooking the Companion
            try {
                Class cl = p.classLoader.loadClass(
                    "com.oplusos.systemui.common.settingsvalue.CommonSettingsValueProxy$Companion"
                );
                XposedBridge.log("FSR_SystemUI: found Companion");
                // Hook any invoke method on Companion
                XposedHelpers.findAndHookMethod(cl, "invoke",
                    new XC_MethodReplacement() {
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            XposedBridge.log("FSR_SystemUI: Companion.invoke called");
                            return Boolean.TRUE;
                        }
                    }
                );
                XposedBridge.log("FSR_SystemUI: hooked Companion");
            } catch (Exception e) {
                XposedBridge.log("FSR_SystemUI Companion: " + e.getMessage());
            }
        }

        // Hook OplusLauncher - OplusStackRecentsConfig controls stack mode
        if (pkg.equals("com.android.launcher")) {
            // Force mEnable lambda
            try {
                Class cl = p.classLoader.loadClass(
                    "com.oplus.quickstep.layout.stack.OplusStackRecentsConfig$mEnable$1"
                );
                XposedHelpers.findAndHookMethod(cl, "invoke", new XC_MethodReplacement() {
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return Boolean.TRUE;
                    }
                });
                XposedBridge.log("FSR_Launcher: hooked mEnable");
            } catch (Exception e) {
                XposedBridge.log("FSR_Launcher mEnable: " + e.getMessage());
            }

            // Hook getCurrentRecentStyle to always return stacked
            try {
                Class cl = p.classLoader.loadClass(
                    "com.oplus.quickstep.layout.stack.OplusStackRecentsConfig"
                );
                XposedHelpers.findAndHookMethod(cl, "getCurrentRecentStyle",
                    new XC_MethodReplacement() {
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return "stacked";
                        }
                    }
                );
                XposedBridge.log("FSR_Launcher: hooked getCurrentRecentStyle");
            } catch (Exception e) {
                XposedBridge.log("FSR_Launcher getCurrentRecentStyle: " + e.getMessage());
            }

            // Hook RecentsLayoutManager to force stacked mode
            try {
                Class cl = p.classLoader.loadClass(
                    "com.oplus.quickstep.utils.RecentsLayoutManager"
                );
                XposedHelpers.findAndHookMethod(cl, "getSupportStackRecents",
                    new XC_MethodReplacement() {
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return Boolean.TRUE;
                        }
                    }
                );
                XposedBridge.log("FSR_Launcher: hooked getSupportStackRecents");
            } catch (Exception e) {
                XposedBridge.log("FSR_Launcher getSupportStackRecents: " + e.getMessage());
            }
        }
    }
}
