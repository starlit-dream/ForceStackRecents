import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public class ForceStackRecents implements IXposedHookLoadPackage {
    private static final String TAG = "FSR: ";

    // Runtime toggles. Defaults are conservative: low-risk exact hooks are enabled, high-risk
    // animation/split experiments are opt-in via adb shell setprop.
    private static final String PROP_STACK_PAGE_BOUNDARY = "persist.fsr.fix_stack_boundary";
    private static final String PROP_STACK_MENU_BUTTON = "persist.fsr.fix_stack_menu";
    private static final String PROP_HOME_ANIMATION = "persist.fsr.fix_home_animation";
    private static final String PROP_SPLIT_FREEFORM = "persist.fsr.fix_split_freeform";

    private static final int VISIBLE = 0;

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!"com.android.launcher".equals(p.packageName)) {
            return;
        }

        try {
            Class<?> ctxClass = p.classLoader.loadClass("android.content.Context");
            hookCurrentForceStackSwitches(p.classLoader, ctxClass);
            if (isPropEnabled(PROP_STACK_PAGE_BOUNDARY, true)) {
                hookStackPageBoundary(p.classLoader);
            }
            if (isPropEnabled(PROP_STACK_MENU_BUTTON, true)) {
                hookStackMenuButton(p.classLoader);
            }
            if (isPropEnabled(PROP_HOME_ANIMATION, false)) {
                hookHomeAnimationOptIn(p.classLoader);
            }
            if (isPropEnabled(PROP_SPLIT_FREEFORM, false)) {
                hookSplitFreeformOptIn(p.classLoader);
            }
        } catch (Throwable t) {
            log("init", t);
        }
    }

    private static void hookCurrentForceStackSwitches(ClassLoader loader, Class<?> ctxClass) {
        try {
            Class<?> cl = loader.loadClass("com.oplus.quickstep.layout.stack.OplusStackRecentsConfig$mEnable$1");
            XposedHelpers.findAndHookMethod(cl, "invoke", ctxClass, new XC_MethodReplacement() {
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return Boolean.TRUE;
                }
            });
            log("hooked mEnable$1.invoke(Context) -> true");
        } catch (Throwable t) {
            log("mEnable$1", t);
        }

        try {
            Class<?> cl = loader.loadClass("com.oplus.quickstep.locksetting.ui.RecentStyleLayoutPreference");
            XposedHelpers.findAndHookMethod(cl, "getCurrentRecentStyle", ctxClass, new XC_MethodReplacement() {
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return "stacked";
                }
            });
            log("hooked getCurrentRecentStyle(Context) -> stacked");
        } catch (Throwable t) {
            log("getCurrentRecentStyle", t);
        }

        try {
            Class<?> cl = loader.loadClass("com.oplus.quickstep.layout.stack.OplusStackRecentsConfig");
            XposedHelpers.findAndHookMethod(cl, "isStackSupported", new XC_MethodReplacement() {
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return Boolean.TRUE;
                }
            });
            log("hooked isStackSupported() -> true");
        } catch (Throwable t) {
            log("isStackSupported", t);
        }
    }

    /**
     * JADX: com.oplus.quickstep.delegates.StackRecentsViewDelegate.getScrollForPage(int)
     * returns 0 when index is < 0 or >= mPageLayoutOffsets.length. In stack mode that makes an
     * invalid edge position resolve to the first page's scroll, which can show a blank virtual page.
     */
    private static void hookStackPageBoundary(ClassLoader loader) {
        try {
            Class<?> cl = loader.loadClass("com.oplus.quickstep.delegates.StackRecentsViewDelegate");
            XposedHelpers.findAndHookMethod(cl, "getScrollForPage", Integer.TYPE, new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam param) {
                    int pageCount = getRecentsPageCount(param.thisObject);
                    if (pageCount <= 0 || !(param.args[0] instanceof Integer)) {
                        return;
                    }
                    int index = ((Integer) param.args[0]).intValue();
                    int clamped = clamp(index, 0, pageCount - 1);
                    if (clamped != index) {
                        param.args[0] = Integer.valueOf(clamped);
                    }
                }
            });
            log("hooked StackRecentsViewDelegate.getScrollForPage(int) boundary clamp");
        } catch (Throwable t) {
            log("StackRecentsViewDelegate.getScrollForPage", t);
        }

        try {
            Class<?> cl = loader.loadClass("com.oplus.quickstep.delegates.StackRecentsViewDelegate");
            XposedHelpers.findAndHookMethod(cl, "getPageNearestToAnchorOfScreen", Integer.TYPE, new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) {
                    int pageCount = getRecentsPageCount(param.thisObject);
                    if (pageCount <= 0 || !(param.getResult() instanceof Integer)) {
                        return;
                    }
                    int result = ((Integer) param.getResult()).intValue();
                    param.setResult(Integer.valueOf(clamp(result, 0, pageCount - 1)));
                }
            });
            log("hooked StackRecentsViewDelegate.getPageNearestToAnchorOfScreen(int) result clamp");
        } catch (Throwable t) {
            log("StackRecentsViewDelegate.getPageNearestToAnchorOfScreen", t);
        }
    }

    /**
     * JADX: OplusTaskViewImpl.onFinishInflate() assigns mStackMenuBtn from
     * R.id.oplus_task_header_menu_button_stack. OplusStackTaskView.setStableAlpha(float) can set
     * this button alpha to 0 in stack mode while the menu click path still works.
     */
    private static void hookStackMenuButton(ClassLoader loader) {
        hookRevealAfterNoArg(loader, "com.android.quickstep.views.OplusTaskViewImpl", "onFinishInflate");
        hookRevealAfterNoArg(loader, "com.android.quickstep.views.OplusStackTaskView", "onFinishInflate");
        hookRevealAfterNoArg(loader, "com.android.quickstep.views.OplusStackTaskView", "applySnapshotTransX");
        hookRevealAfterNoArg(loader, "com.android.quickstep.views.OplusStackTaskView", "applySnapshotTransY");

        try {
            Class<?> cl = loader.loadClass("com.android.quickstep.views.OplusStackTaskView");
            XposedHelpers.findAndHookMethod(cl, "setStableAlpha", Float.TYPE, new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args[0] instanceof Float && ((Float) param.args[0]).floatValue() > 0.0f) {
                        revealStackMenuButton(param.thisObject);
                    }
                }
            });
            log("hooked OplusStackTaskView.setStableAlpha(float) menu reveal");
        } catch (Throwable t) {
            log("OplusStackTaskView.setStableAlpha", t);
        }
    }

    private static void hookRevealAfterNoArg(ClassLoader loader, String className, String methodName) {
        try {
            Class<?> cl = loader.loadClass(className);
            XposedHelpers.findAndHookMethod(cl, methodName, new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) {
                    revealStackMenuButton(param.thisObject);
                }
            });
            log("hooked " + className + "." + methodName + " menu reveal");
        } catch (Throwable t) {
            log(className + "." + methodName, t);
        }
    }

    /**
     * JADX: RecentsActivity.startHome() uses switchToScreenshot(... finishRecentsAnimation(...))
     * when ENABLE_QUICKSTEP_LIVE_TILE is true; startHomeInternal() uses the direct remote Home
     * transition. This opt-in hook forces the direct path only for explicit testing.
     */
    private static void hookHomeAnimationOptIn(ClassLoader loader) {
        try {
            Class<?> cl = loader.loadClass("com.android.quickstep.RecentsActivity");
            XposedHelpers.findAndHookMethod(cl, "startHome", new XC_MethodReplacement() {
                protected Object replaceHookedMethod(MethodHookParam param) {
                    callNoArgs(param.thisObject, "startHomeInternal");
                    return null;
                }
            });
            log("hooked RecentsActivity.startHome() -> startHomeInternal() opt-in");
        } catch (Throwable t) {
            log("RecentsActivity.startHome", t);
        }
    }

    /**
     * JADX: OplusBaseRecentsAnimationController.finishSplitScreenController(...) sets
     * mFinishTargetIsLauncher=true and delegates to finishSplitScreenCore(...), which calls
     * FlexibleWindowManager.notifyMinimizedReady(...). Keep this as a diagnostic hook only: the
     * method is exact, opt-in, and does not modify arguments/results.
     */
    private static void hookSplitFreeformOptIn(ClassLoader loader) {
        try {
            Class<?> cl = loader.loadClass("com.android.quickstep.OplusBaseRecentsAnimationController");
            XposedBridge.hookAllMethods(cl, "finishSplitScreenController", new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam param) {
                    setBooleanField(param.thisObject, "mFinishTargetIsLauncher", true);
                    log("finishSplitScreenController opt-in observed");
                }
            });
            log("hooked OplusBaseRecentsAnimationController.finishSplitScreenController(...) opt-in");
        } catch (Throwable t) {
            log("OplusBaseRecentsAnimationController.finishSplitScreenController", t);
        }
    }

    private static int getRecentsPageCount(Object delegate) {
        Object recentsView = callNoArgs(delegate, "getMRecentsView");
        int pageCount = getInt(callNoArgs(recentsView, "getPageCount"), -1);
        if (pageCount > 0) {
            return pageCount;
        }
        int taskViewCount = getInt(callNoArgs(recentsView, "getTaskViewCount"), -1);
        if (taskViewCount > 0) {
            return taskViewCount;
        }
        return getInt(callNoArgs(recentsView, "getChildCount"), 0);
    }

    private static void revealStackMenuButton(Object taskView) {
        Object button = getFieldObject(taskView, "mStackMenuBtn");
        if (button == null) {
            Object header = getFieldObject(taskView, "mHeader");
            button = callNoArgs(header, "getMenuBtn");
        }
        if (button == null) {
            return;
        }
        call(button, "setVisibility", new Class[]{Integer.TYPE}, new Object[]{Integer.valueOf(VISIBLE)});
        call(button, "setAlpha", new Class[]{Float.TYPE}, new Object[]{Float.valueOf(1.0f)});
        call(button, "setEnabled", new Class[]{Boolean.TYPE}, new Object[]{Boolean.TRUE});
    }

    private static Object getFieldObject(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> cl = target.getClass();
        while (cl != null) {
            try {
                Field field = cl.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
                cl = cl.getSuperclass();
            }
        }
        return null;
    }

    private static void setBooleanField(Object target, String fieldName, boolean value) {
        if (target == null) {
            return;
        }
        Class<?> cl = target.getClass();
        while (cl != null) {
            try {
                Field field = cl.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.setBoolean(target, value);
                return;
            } catch (Throwable ignored) {
                cl = cl.getSuperclass();
            }
        }
    }

    private static Object callNoArgs(Object target, String methodName) {
        return call(target, methodName, new Class[]{}, new Object[]{});
    }

    private static Object call(Object target, String methodName, Class[] parameterTypes, Object[] args) {
        if (target == null) {
            return null;
        }
        Class<?> cl = target.getClass();
        while (cl != null) {
            try {
                Method method = cl.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (Throwable ignored) {
                cl = cl.getSuperclass();
            }
        }
        return null;
    }

    private static int getInt(Object value, int fallback) {
        return value instanceof Integer ? ((Integer) value).intValue() : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isPropEnabled(String key, boolean defaultValue) {
        Object value = callStatic("android.os.SystemProperties", "get", new Class[]{String.class}, new Object[]{key});
        if (value == null) {
            return defaultValue;
        }
        String stringValue = String.valueOf(value).toLowerCase(Locale.US);
        if ("1".equals(stringValue) || "true".equals(stringValue) || "yes".equals(stringValue) || "on".equals(stringValue)) {
            return true;
        }
        if ("0".equals(stringValue) || "false".equals(stringValue) || "no".equals(stringValue) || "off".equals(stringValue)) {
            return false;
        }
        return defaultValue;
    }

    private static Object callStatic(String className, String methodName, Class[] parameterTypes, Object[] args) {
        try {
            Class<?> cl = Class.forName(className);
            Method method = cl.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + message);
    }

    private static void log(String where, Throwable t) {
        String reason = t == null ? "null" : t.getClass().getSimpleName() + ": " + t.getMessage();
        XposedBridge.log(TAG + where + ": " + reason);
    }
}
