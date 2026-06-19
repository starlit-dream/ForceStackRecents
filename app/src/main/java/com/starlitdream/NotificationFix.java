import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Fix QQ conversation notifications on ColorOS 16 (OnePlus Ace 6T):
 * 1. Show sender avatar in heads-up and notification panel (not just status bar)
 * 2. Fix duplicate sender name when conversation is set to "Important"
 */
public class NotificationFix implements IXposedHookLoadPackage {

    private static final String SYSTEMUI = "com.android.systemui";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(SYSTEMUI)) return;

        XposedBridge.log("[NotifFix] loaded in SystemUI");

        hookOplusShouldShowIcon(lpparam);
        hookResolveHeaderViewsPlus(lpparam);
    }

    /**
     * Hook oplusShouldShowIcon — force false for conversation notifications.
     * This makes heads-up and panel show avatar instead of small icon.
     */
    private void hookOplusShouldShowIcon(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = lpparam.classLoader.loadClass(
                "com.oplus.systemui.statusbar.icon.ui.binder.OplusNotificationIconAreaBinder"
            );
            XposedHelpers.findAndHookMethod(clazz, "oplusShouldShowIcon",
                Object.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Object entry = param.args[0];
                        if (entry == null) return;
                        try {
                            // Get StatusBarNotification reflectively
                            Object sbn = XposedHelpers.callMethod(entry, "getSbn");
                            if (sbn == null) return;

                            // Get Notification object
                            Object notif = XposedHelpers.callMethod(sbn, "getNotification");
                            if (notif == null) return;

                            // Check extras for conversation display name
                            Object extras = XposedHelpers.getObjectField(notif, "extras");
                            if (extras == null) return;

                            // "android.selfDisplayName" indicates a conversation-style notification
                            Object selfName = XposedHelpers.callMethod(extras, "get",
                                "android.selfDisplayName");
                            if (selfName != null) {
                                // Conversation notification — don't force icon
                                param.setResult(Boolean.FALSE);
                            }
                        } catch (Exception e) {
                            // Silently skip on reflection errors
                        }
                    }
                }
            );
            XposedBridge.log("[NotifFix] hooked oplusShouldShowIcon OK");
        } catch (Exception e) {
            XposedBridge.log("[NotifFix] hook oplusShouldShowIcon FAILED: " + e.getMessage());
        }
    }

    /**
     * Hook resolveHeaderViewsPlus — skip duplicate conversation title
     * when notification uses MessagingStyle (e.g. QQ after module modification).
     */
    private void hookResolveHeaderViewsPlus(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = lpparam.classLoader.loadClass(
                "com.android.systemui.statusbar.notification.row.wrapper.OplusNotificationHeaderViewWrapperEx"
            );
            XposedHelpers.findAndHookMethod(clazz, "resolveHeaderViewsPlus",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object base = XposedHelpers.callMethod(param.thisObject, "getBase");
                            if (base == null) return;

                            Object row = XposedHelpers.getObjectField(base, "mRow");
                            if (row == null) return;

                            Object entry = XposedHelpers.callMethod(row, "getEntry");
                            if (entry == null) return;

                            Object sbn = XposedHelpers.callMethod(entry, "getSbn");
                            if (sbn == null) return;

                            Object notif = XposedHelpers.callMethod(sbn, "getNotification");
                            if (notif == null) return;

                            Object extras = XposedHelpers.getObjectField(notif, "extras");
                            if (extras == null) return;

                            // Check if notification has a conversation display name
                            Object selfName = XposedHelpers.callMethod(extras, "get",
                                "android.selfDisplayName");
                            if (selfName != null) {
                                // MessagingStyle notification — skip extra header label
                                XposedHelpers.callMethod(param.thisObject,
                                    "setUpdateExpandability", true);
                            }
                        } catch (Exception e) {
                            // Silently skip on reflection errors
                        }
                    }
                }
            );
            XposedBridge.log("[NotifFix] hooked resolveHeaderViewsPlus OK");
        } catch (Exception e) {
            XposedBridge.log("[NotifFix] hook resolveHeaderViewsPlus FAILED: " + e.getMessage());
        }
    }
}
