import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public class ForceStackRecents implements IXposedHookLoadPackage {
    private static final String TAG = "FSR: ";

    // Runtime toggles. All fixes are enabled by default, but each can be disabled with adb shell
    // setprop if a device-specific regression appears.
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
            if (isPropEnabled(PROP_HOME_ANIMATION, true)) {
                hookHomeAnimationFix(p.classLoader);
            }
            if (isPropEnabled(PROP_SPLIT_FREEFORM, true)) {
                hookSplitFreeformFix(p.classLoader);
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
                    revealStackMenuButton(param.thisObject);
                }
            });
            log("hooked OplusStackTaskView.setStableAlpha(float) menu reveal");
        } catch (Throwable t) {
            log("OplusStackTaskView.setStableAlpha", t);
        }

        hookRevealAfterNoArg(loader, "com.android.quickstep.views.OplusTaskViewImpl", "updateTaskMenuBtnSrc");
        try {
            Class<?> cl = loader.loadClass("com.android.quickstep.views.OplusTaskViewImpl");
            XposedHelpers.findAndHookMethod(cl, "setTaskMenuBtnSrc", Integer.TYPE, String.class, new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) {
                    revealStackMenuButton(param.thisObject);
                }
            });
            log("hooked OplusTaskViewImpl.setTaskMenuBtnSrc(int,String) menu reveal");
        } catch (Throwable t) {
            log("OplusTaskViewImpl.setTaskMenuBtnSrc", t);
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
     * Keep the OEM Home transition intact. Replacing startHome() bypasses cleanup in some builds and
     * can desync the launcher/app layers, so this hook only hides stale fallback/overlay views after
     * the normal lifecycle has run.
     */
    private static void hookHomeAnimationFix(ClassLoader loader) {
        try {
            Class<?> cl = loader.loadClass("com.android.quickstep.RecentsActivity");
            XposedHelpers.findAndHookMethod(cl, "startHome", new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) {
                    hideRecentsDecor(param.thisObject);
                }
            });
            hookHideRecentsDecorAfterNoArg(cl, "onPause");
            hookHideRecentsDecorAfterNoArg(cl, "onStop");
            hookHideRecentsDecorAfterNoArg(cl, "onDestroy");
            log("hooked RecentsActivity lifecycle stale decor cleanup");
        } catch (Throwable t) {
            log("RecentsActivity lifecycle cleanup", t);
        }
    }

    private static void hookHideRecentsDecorAfterNoArg(Class<?> cl, String methodName) {
        try {
            XposedHelpers.findAndHookMethod(cl, methodName, new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) {
                    hideRecentsDecor(param.thisObject);
                }
            });
        } catch (Throwable t) {
            log("RecentsActivity." + methodName, t);
        }
    }

    /**
     * JADX: OplusBaseSwipeUpHandler.finishRecentsControllerToSplitScreen(...) is the exact drag
     * edge path. It delegates to RecentsAnimationController.finishSplitScreenController(...) and
     * then releases transition surfaces. The fix keeps this path alive by marking the controller as
     * launcher-target before the OEM code enters finishSplitScreenController, without changing args.
     */
    private static void hookSplitFreeformFix(ClassLoader loader) {
        try {
            Class<?> cl = loader.loadClass("com.oplus.quickstep.gesture.OplusBaseSwipeUpHandler");
            XposedBridge.hookAllMethods(cl, "finishRecentsControllerToSplitScreen", new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object controller = getFieldObject(param.thisObject, "mRecentsAnimationController");
                    setBooleanField(controller, "mFinishTargetIsLauncher", true);
                }
            });
            log("hooked OplusBaseSwipeUpHandler.finishRecentsControllerToSplitScreen(...) controller target fix");
        } catch (Throwable t) {
            log("OplusBaseSwipeUpHandler.finishRecentsControllerToSplitScreen", t);
        }

        try {
            Class<?> cl = loader.loadClass("com.android.quickstep.OplusBaseRecentsAnimationController");
            XposedBridge.hookAllMethods(cl, "finishSplitScreenController", new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam param) {
                    setBooleanField(param.thisObject, "mFinishTargetIsLauncher", true);
                }
            });
            log("hooked OplusBaseRecentsAnimationController.finishSplitScreenController(...) launcher target fix");
        } catch (Throwable t) {
            log("OplusBaseRecentsAnimationController.finishSplitScreenController", t);
        }
    }

    private static int getRecentsPageCount(Object delegate) {
        int layoutOffsetCount = getArrayLength(getFieldObject(delegate, "mPageLayoutOffsets"));
        Object recentsView = callNoArgs(delegate, "getMRecentsView");
        int taskViewCount = getInt(callNoArgs(recentsView, "getTaskViewCount"), -1);
        if (layoutOffsetCount > 0 && taskViewCount > 0) {
            return Math.min(layoutOffsetCount, taskViewCount);
        }
        if (taskViewCount > 0) {
            return taskViewCount;
        }
        if (layoutOffsetCount > 0) {
            return layoutOffsetCount;
        }
        int pageCount = getInt(callNoArgs(recentsView, "getPageCount"), -1);
        if (pageCount > 0) {
            return pageCount;
        }
        return getInt(callNoArgs(recentsView, "getChildCount"), 0);
    }

    private static void revealStackMenuButton(Object taskView) {
        Object button = findStackMenuButton(taskView);
        if (button == null) {
            return;
        }
        call(button, "setVisibility", new Class[]{Integer.TYPE}, new Object[]{Integer.valueOf(VISIBLE)});
        call(button, "setAlpha", new Class[]{Float.TYPE}, new Object[]{Float.valueOf(1.0f)});
        call(button, "setEnabled", new Class[]{Boolean.TYPE}, new Object[]{Boolean.TRUE});
    }

    private static Object findStackMenuButton(Object taskView) {
        Object button = getFieldObject(taskView, "mStackMenuBtn");
        if (button != null) {
            return button;
        }
        Object header = getFieldObject(taskView, "mHeader");
        button = callNoArgs(header, "getMenuBtn");
        if (button != null) {
            return button;
        }
        return findMenuLikeChild(taskView, 0);
    }

    private static Object findMenuLikeChild(Object view, int depth) {
        if (view == null || depth > 5) {
            return null;
        }
        CharSequence contentDescription = getCharSequence(callNoArgs(view, "getContentDescription"));
        String className = view.getClass().getName().toLowerCase(Locale.US);
        String description = contentDescription == null ? "" : contentDescription.toString().toLowerCase(Locale.US);
        if ((className.contains("image") || className.contains("button"))
                && (description.contains("menu") || description.contains("more") || description.contains("更多"))) {
            return view;
        }
        int childCount = getInt(callNoArgs(view, "getChildCount"), 0);
        for (int i = 0; i < childCount; i++) {
            Object child = call(view, "getChildAt", new Class[]{Integer.TYPE}, new Object[]{Integer.valueOf(i)});
            Object result = findMenuLikeChild(child, depth + 1);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static void hideRecentsDecor(Object activity) {
        hideView(getFieldObject(activity, "mFallbackRecentsView"));
        hideView(firstNonNull(
                getFieldObject(activity, "mOverlayView"),
                getFieldObject(activity, "mScrimView"),
                getFieldObject(activity, "mScreenshotView")));
    }

    private static void hideView(Object view) {
        if (view == null) {
            return;
        }
        call(view, "setContentAlpha", new Class[]{Float.TYPE}, new Object[]{Float.valueOf(0.0f)});
        call(view, "setAlpha", new Class[]{Float.TYPE}, new Object[]{Float.valueOf(0.0f)});
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

    private static int getArrayLength(Object value) {
        if (value == null || !value.getClass().isArray()) {
            return -1;
        }
        return Array.getLength(value);
    }

    private static CharSequence getCharSequence(Object value) {
        return value instanceof CharSequence ? (CharSequence) value : null;
    }

    private static Object firstNonNull(Object first, Object second, Object third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
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
