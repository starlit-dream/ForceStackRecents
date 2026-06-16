import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ForceStackRecents implements IXposedHookLoadPackage {
    private static final String PKG = "com.android.launcher";

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!p.packageName.equals(PKG)) return;
        XposedBridge.log("FSR: active");

        try {
            Class cl = p.classLoader.loadClass(
                "com.oplus.quickstep.common.settingsvalue.CommonSettingsValueProxy$Companion$isStackLayout$1"
            );
            XposedHelpers.findAndHookMethod(cl, "invoke", new XC_MethodReplacement() {
                protected Object replaceHookedMethod(MethodHookParam param) { return Boolean.TRUE; }
            });
            XposedBridge.log("FSR: hooked isStackLayout");
        } catch (Exception e) {
            XposedBridge.log("FSR: " + e.getMessage());
        }

        try {
            Class cl = p.classLoader.loadClass(
                "com.oplus.quickstep.common.settingsvalue.CommonSettingsValueProxy"
            );
            XposedHelpers.findAndHookMethod(cl, "getCurrentRecentStyle", new XC_MethodReplacement() {
                protected Object replaceHookedMethod(MethodHookParam param) { return "stacked"; }
            });
            XposedBridge.log("FSR: hooked getCurrentRecentStyle");
        } catch (Exception e) {
            XposedBridge.log("FSR: " + e.getMessage());
        }

        try {
            Class cl = p.classLoader.loadClass(
                "com.oplus.quickstep.layout.stack.OplusStackRecentsConfig$mEnable$1"
            );
            XposedHelpers.findAndHookMethod(cl, "invoke", new XC_MethodReplacement() {
                protected Object replaceHookedMethod(MethodHookParam param) { return Boolean.TRUE; }
            });
            XposedBridge.log("FSR: hooked mEnable");
        } catch (Exception e) {
            XposedBridge.log("FSR: " + e.getMessage());
        }
    }
}
