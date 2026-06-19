import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import android.app.Notification;
import android.service.notification.StatusBarNotification;

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
     * Hook 1: Force conversation notifications to show avatar (not icon)
     * in heads-up and notification panel.
     *
     * ColorOS's OplusNotificationIconAreaBinder.oplusShouldShowIcon()
     * returns true for non-important conversations → shows small icon.
     * We return false when the notification is a conversation → avatar shows.
     */
    private void hookOplusShouldShowIcon(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = lpparam.classLoader.loadClass(
                "com.oplus.systemui.statusbar.icon.ui.binder.OplusNotificationIconAreaBinder"
            );
            XposedHelpers.findAndHookMethod(clazz, "oplusShouldShowIcon",
                Object.class, // NotificationEntry — use Object to avoid class loading issues
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Object entry = param.args[0];
                        if (entry == null) return;
                        try {
                            // Get StatusBarNotification via reflection (avoid direct class ref)
                            Object sbn = XposedHelpers.callMethod(entry, "getSbn");
                            if (sbn == null) return;
                            Notification notif = (Notification) XposedHelpers.callMethod(sbn, "getNotification");
                            if (notif == null) return;

                            // Check if it's a conversation notification (has shortcut)
                            // Also check for MessagingStyle which QQ uses after modification
                            if (notif.isConversation() || notif.extras != null) {
                                // Cast to android.app.Notification.MessagingStyle if available
                                Object style = notif.extras.get("android.selfDisplayName");
                                if (style != null) {
                                    // Has a conversation display name → definitely a conversation
                                    param.setResult(Boolean.FALSE);
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            XposedBridge.log("[NotifFix] oplusShouldShowIcon error: " + e.getMessage());
                        }
                    }
                }
            );
            XposedBridge.log("[NotifFix] hooked oplusShouldShowIcon ✓");
        } catch (Exception e) {
            XposedBridge.log("[NotifFix] hook oplusShouldShowIcon FAILED: " + e.getMessage());
        }
    }

    /**
     * Hook 2: Fix duplicate sender name in conversation notification headers.
     *
     * ColorOS's OplusNotificationHeaderViewWrapperEx.resolveHeaderViewsPlus()
     * adds the shortcut's shortLabel as an extra header title.
     * When QQ is using MessagingStyle, the sender name already appears in each
     * message bubble → skip the extra header to avoid duplication.
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
                            // Check if the wrapped notification uses MessagingStyle
                            Object base = XposedHelpers.callMethod(param.thisObject, "getBase");
                            if (base == null) return;

                            // Try to get the row and its notification entry
                            Object row = XposedHelpers.getObjectField(base, "mRow");
                            if (row == null) return;

                            Object entry = XposedHelpers.callMethod(row, "getEntry");
                            if (entry == null) return;

                            Object sbn = XposedHelpers.callMethod(entry, "getSbn");
                            if (sbn == null) return;

                            Notification notif = (Notification) XposedHelpers.callMethod(sbn, "getNotification");

                            // If notification uses MessagingStyle (like QQ after module modification),
                            // skip the extra header title rendering to avoid duplicate sender name
                            if (notif.extras != null && notif.extras.containsKey("android.selfDisplayName")) {
                                // Already has a conversation sender display → skip extra label
                                // Setting mUpdateExpandability to true tells the renderer
                                // that the header is already complete
                                XposedHelpers.callMethod(param.thisObject, "setUpdateExpandability", true);
                            }
                        } catch (Exception e) {
                            XposedBridge.log("[NotifFix] resolveHeaderViewsPlus error: " + e.getMessage());
                        }
                    }
                }
            );
            XposedBridge.log("[NotifFix] hooked resolveHeaderViewsPlus ✓");
        } catch (Exception e) {
            XposedBridge.log("[NotifFix] hook resolveHeaderViewsPlus FAILED: " + e.getMessage());
        }
    }
}
