import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ForceStackRecents implements IXposedHookLoadPackage {
    private static final String TAG = "FSR: ";
    private static final int VISIBLE = 0;
    private static final int GONE = 8;

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!"com.android.launcher".equals(p.packageName)) {
            return;
        }

        try {
            Class<?> ctxClass = p.classLoader.loadClass("android.content.Context");
            hookCurrentForceStackSwitches(p.classLoader, ctxClass);
            hookRecentsOverlayCleanup(p.classLoader);
            hookStackBoundaryClamps(p.classLoader);
            hookSplitAndFreeformGates(p.classLoader);
            hookHomeAnimationPath(p.classLoader);
            hookStackTaskMenuVisibility(p.classLoader);
        } catch (Throwable t) {
            log("init failed", t);
        }
    }

    private static void hookCurrentForceStackSwitches(ClassLoader loader, Class<?> ctxClass) {
        hookReturnConstant(loader,
                "com.oplus.quickstep.layout.stack.OplusStackRecentsConfig$mEnable$1",
                "invoke",
                new Object[]{ctxClass},
                Boolean.TRUE,
                "mEnable$1.invoke(Context) -> true");

        hookReturnConstant(loader,
                "com.oplus.quickstep.locksetting.ui.RecentStyleLayoutPreference",
                "getCurrentRecentStyle",
                new Object[]{ctxClass},
                "stacked",
                "getCurrentRecentStyle(Context) -> stacked");

        hookReturnConstant(loader,
                "com.oplus.quickstep.layout.stack.OplusStackRecentsConfig",
                "isStackSupported",
                new Object[]{},
                Boolean.TRUE,
                "isStackSupported() -> true");
    }

    private static void hookRecentsOverlayCleanup(ClassLoader loader) {
        String[] activityClasses = new String[]{
                "com.oplus.quickstep.RecentsActivity",
                "com.oplus.quickstep.OplusRecentsActivity",
                "com.android.quickstep.RecentsActivity",
                "com.oplus.quickstep.views.RecentsActivity"
        };

        for (String className : activityClasses) {
            Class<?> clazz = findClass(className, loader);
            if (clazz == null) {
                continue;
            }
            hookAfterAll(clazz, "onStop", new AfterHook() {
                public void run(XC_MethodHook.MethodHookParam param) {
                    hideActivityDecor(param.thisObject);
                }
            }, "cleanup recents overlay after onStop: " + className);
            hookAfterAll(clazz, "onDestroy", new AfterHook() {
                public void run(XC_MethodHook.MethodHookParam param) {
                    hideActivityDecor(param.thisObject);
                }
            }, "cleanup recents overlay after onDestroy: " + className);
        }

        String[] viewClasses = new String[]{
                "com.android.quickstep.views.LauncherRecentsView",
                "com.android.quickstep.views.RecentsView",
                "com.oplus.quickstep.RecentsView",
                "com.oplus.quickstep.views.RecentsView",
                "com.oplus.quickstep.layout.stack.OplusStackRecentsView",
                "com.oplus.quickstep.layout.stack.StackRecentsView"
        };
        for (String className : viewClasses) {
            Class<?> clazz = findClass(className, loader);
            if (clazz == null) {
                continue;
            }
            hookMethodsContaining(clazz, new MethodMatcher() {
                public boolean matches(Method method) {
                    String name = method.getName().toLowerCase(Locale.US);
                    return method.getReturnType() == Void.TYPE
                            && (name.equals("onrecentsanimationfinished")
                            || name.equals("cleanuprecentsanimation")
                            || name.equals("finishrecentsanimation")
                            || name.equals("reset"));
                }
            }, new AfterHook() {
                public void run(XC_MethodHook.MethodHookParam param) {
                    hideView(param.thisObject);
                }
            }, "hide stale recents view when animation finishes: " + className);
        }
    }

    private static void hookStackBoundaryClamps(ClassLoader loader) {
        String[] classNames = new String[]{
                "com.oplus.quickstep.delegates.StackRecentsViewDelegate",
                "com.android.quickstep.views.OplusRecentsViewImpl",
                "com.android.quickstep.views.OplusStackRecentsView",
                "com.android.quickstep.views.LauncherRecentsView",
                "com.oplus.quickstep.layout.stack.OplusStackRecentsView",
                "com.oplus.quickstep.layout.stack.StackRecentsView",
                "com.oplus.quickstep.layout.stack.OplusStackPagedView",
                "com.oplus.quickstep.layout.stack.StackPagedView",
                "com.oplus.quickstep.layout.stack.OplusStackTaskViewPager",
                "com.oplus.quickstep.layout.stack.StackTaskViewPager"
        };

        for (String className : classNames) {
            final Class<?> clazz = findClass(className, loader);
            if (clazz == null) {
                continue;
            }
            hookMethodsContaining(clazz, new MethodMatcher() {
                public boolean matches(Method method) {
                    String name = method.getName().toLowerCase(Locale.US);
                    Class<?> returnType = method.getReturnType();
                    if (returnType == Integer.TYPE) {
                        return name.contains("count")
                                || name.contains("page")
                                || name.contains("index")
                                || name.contains("position");
                    }
                    return returnType.isArray() && returnType.getComponentType() == Integer.TYPE
                            && (name.contains("range") || name.contains("bound"));
                }
            }, new AfterHook() {
                public void run(XC_MethodHook.MethodHookParam param) {
                    clampStackBoundaryResult(param);
                }
            }, "clamp stack page/task boundaries: " + className);
        }
    }

    private static void hookSplitAndFreeformGates(ClassLoader loader) {
        String[] classNames = new String[]{
                "com.android.wm.shell.splitscreen.SplitScreenController",
                "com.android.wm.shell.splitscreen.SplitScreenControllerExt",
                "com.android.quickstep.views.OplusStackRecentsView",
                "com.android.quickstep.views.LauncherRecentsView",
                "com.oplus.quickstep.splitScreen.SplitScreenController",
                "com.oplus.quickstep.splitScreen.OplusSplitScreenController",
                "com.oplus.quickstep.splitScreen.SplitSelectStateController",
                "com.oplus.quickstep.util.SplitScreenUtils",
                "com.oplus.quickstep.util.OplusSplitScreenUtils",
                "com.oplus.quickstep.util.OplusLauncherUtils",
                "com.oplus.quickstep.util.LauncherUtils"
        };

        for (String className : classNames) {
            Class<?> clazz = findClass(className, loader);
            if (clazz == null) {
                continue;
            }
            hookBooleanMethods(clazz, new MethodMatcher() {
                public boolean matches(Method method) {
                    String name = method.getName().toLowerCase(Locale.US);
                    if (name.contains("defaultlauncher") || name.contains("launcherdefault") || name.equals("islauncherdefault")) {
                        return true;
                    }
                    boolean isWindowFeature = name.contains("split")
                            || name.contains("freeform")
                            || name.contains("flexible")
                            || name.contains("smallwindow")
                            || name.contains("float");
                    boolean isGate = name.contains("support")
                            || name.contains("enable")
                            || name.contains("allow")
                            || name.startsWith("can")
                            || name.startsWith("should");
                    return isWindowFeature && isGate;
                }
            }, Boolean.TRUE, "allow split/freeform gates: " + className);
        }
    }

    private static void hookHomeAnimationPath(ClassLoader loader) {
        String[] classNames = new String[]{
                "com.android.quickstep.OplusBaseRecentsAnimationController",
                "com.android.quickstep.RecentsAnimationController",
                "com.android.quickstep.RecentsAnimationCallbacks",
                "com.android.quickstep.RecentsAnimationDeviceState",
                "com.android.quickstep.AbsSwipeUpHandler",
                "com.android.quickstep.OplusBaseSwipeUpHandler",
                "com.oplus.quickstep.gesture.OplusBaseSwipeUpHandler",
                "com.oplus.quickstep.animation.RecentsAnimationController",
                "com.oplus.quickstep.animation.RecentsAnimationRunner",
                "com.oplus.quickstep.animation.OplusRecentsAnimationController",
                "com.oplus.quickstep.animation.OplusRecentsAnimationRunner",
                "com.oplus.quickstep.layout.stack.OplusStackRecentsAnimationHelper",
                "com.oplus.quickstep.layout.stack.StackRecentsAnimationHelper"
        };

        for (String className : classNames) {
            Class<?> clazz = findClass(className, loader);
            if (clazz == null) {
                continue;
            }
            hookBooleanMethods(clazz, new MethodMatcher() {
                public boolean matches(Method method) {
                    String name = method.getName().toLowerCase(Locale.US);
                    boolean mentionsStack = name.contains("stack") || name.contains("oplusrecents");
                    boolean mentionsAnimation = name.contains("anim")
                            || name.contains("transition")
                            || name.contains("runner")
                            || name.contains("remote");
                    return mentionsStack && mentionsAnimation;
                }
            }, Boolean.FALSE, "prefer non-stacked home animation path: " + className);
        }
    }

    private static void hookStackTaskMenuVisibility(ClassLoader loader) {
        String[] classNames = new String[]{
                "com.android.quickstep.views.OplusTaskViewImpl",
                "com.android.quickstep.views.OplusStackTaskView",
                "com.android.quickstep.views.OplusStackTaskView$MenuOverlay",
                "com.android.quickstep.views.TaskView",
                "com.android.quickstep.views.TaskMenuView",
                "com.oplus.quickstep.layout.stack.StackTaskView",
                "com.oplus.quickstep.layout.stack.OplusStackTaskView",
                "com.oplus.quickstep.layout.stack.StackTaskThumbnailView",
                "com.oplus.quickstep.layout.stack.OplusStackTaskThumbnailView",
                "com.oplus.quickstep.views.TaskView"
        };

        for (String className : classNames) {
            Class<?> clazz = findClass(className, loader);
            if (clazz == null) {
                continue;
            }
            hookMethodsContaining(clazz, new MethodMatcher() {
                public boolean matches(Method method) {
                    String name = method.getName().toLowerCase(Locale.US);
                    return method.getReturnType() == Void.TYPE
                            && (name.equals("onfinishinflate")
                            || name.equals("bind")
                            || name.equals("bindtask")
                            || name.equals("ontaskbound")
                            || name.equals("settask")
                            || name.equals("settaskviewinfo")
                            || name.equals("onlayout"));
                }
            }, new AfterHook() {
                public void run(XC_MethodHook.MethodHookParam param) {
                    revealOverflowMenuButtons(param.thisObject, 0);
                }
            }, "force task overflow button visible: " + className);
        }
    }

    private static void hookReturnConstant(ClassLoader loader, String className, String methodName,
                                           Object[] parameterTypes, final Object value, String label) {
        try {
            Class<?> clazz = loader.loadClass(className);
            Object[] args = new Object[parameterTypes.length + 1];
            for (int i = 0; i < parameterTypes.length; i++) {
                args[i] = parameterTypes[i];
            }
            args[args.length - 1] = new XC_MethodReplacement() {
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return value;
                }
            };
            XposedHelpers.findAndHookMethod(clazz, methodName, args);
            log("hooked " + label);
        } catch (Throwable t) {
            log("skip " + label, t);
        }
    }

    private static void hookAfterAll(Class<?> clazz, String methodName, final AfterHook hook, String label) {
        try {
            XposedBridge.hookAllMethods(clazz, methodName, new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    hook.run(param);
                }
            });
            log("hooked " + label + "." + methodName);
        } catch (Throwable t) {
            log("skip " + label + "." + methodName, t);
        }
    }

    private static void hookMethodsContaining(Class<?> clazz, MethodMatcher matcher, final AfterHook hook, String label) {
        Set<String> hookedNames = new HashSet<String>();
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (!matcher.matches(method) || hookedNames.contains(method.getName())) {
                continue;
            }
            hookedNames.add(method.getName());
            hookAfterAll(clazz, method.getName(), hook, label);
        }
    }

    private static void hookBooleanMethods(Class<?> clazz, MethodMatcher matcher, final Boolean value, String label) {
        Set<String> hookedNames = new HashSet<String>();
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getReturnType() != Boolean.TYPE || !matcher.matches(method) || hookedNames.contains(method.getName())) {
                continue;
            }
            hookedNames.add(method.getName());
            try {
                XposedBridge.hookAllMethods(clazz, method.getName(), new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(value);
                    }
                });
                log("hooked " + label + "." + method.getName() + " -> " + value);
            } catch (Throwable t) {
                log("skip " + label + "." + method.getName(), t);
            }
        }
    }

    private static Class<?> findClass(String className, ClassLoader loader) {
        try {
            return loader.loadClass(className);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void clampStackBoundaryResult(XC_MethodHook.MethodHookParam param) {
        Object result = param.getResult();
        int taskCount = countTaskChildren(param.thisObject);
        if (result instanceof Integer) {
            int value = ((Integer) result).intValue();
            String methodName = param.method == null ? "" : param.method.getName().toLowerCase(Locale.US);
            if (value < 0) {
                value = 0;
            }
            if (taskCount > 0) {
                if (methodName.contains("count")) {
                    int visibleChildren = countChildren(param.thisObject);
                    if (visibleChildren > 0 && value > visibleChildren) {
                        value = visibleChildren;
                    }
                } else if (value >= taskCount) {
                    value = taskCount - 1;
                }
            }
            param.setResult(Integer.valueOf(value));
        } else if (result != null && result.getClass().isArray() && result.getClass().getComponentType() == Integer.TYPE && taskCount > 0) {
            int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                int value = Array.getInt(result, i);
                if (value < 0) {
                    value = 0;
                } else if (value >= taskCount) {
                    value = taskCount - 1;
                }
                Array.setInt(result, i, value);
            }
            param.setResult(result);
        }
    }

    private static int countTaskChildren(Object view) {
        if (view == null) {
            return 0;
        }
        Object taskViews = callNoArgs(view, "getTaskViews");
        int collectionSize = sizeOf(taskViews);
        if (collectionSize > 0) {
            return collectionSize;
        }
        int directChildren = countChildrenMatching(view, 0);
        if (directChildren > 0) {
            return directChildren;
        }
        return countChildren(view);
    }

    private static int countChildrenMatching(Object view, int depth) {
        if (view == null || depth > 3) {
            return 0;
        }
        int childCount = countChildren(view);
        int result = 0;
        for (int i = 0; i < childCount; i++) {
            Object child = call(view, "getChildAt", new Class[]{Integer.TYPE}, new Object[]{Integer.valueOf(i)});
            if (child == null) {
                continue;
            }
            if (isTaskLikeView(child)) {
                result++;
            } else {
                result += countChildrenMatching(child, depth + 1);
            }
        }
        return result;
    }

    private static boolean isTaskLikeView(Object view) {
        String className = view.getClass().getName().toLowerCase(Locale.US);
        return className.contains("taskview") || className.contains("taskthumbnail") || className.contains("cardview");
    }

    private static int countChildren(Object view) {
        Object count = callNoArgs(view, "getChildCount");
        if (count instanceof Integer) {
            return ((Integer) count).intValue();
        }
        return 0;
    }

    private static int sizeOf(Object object) {
        if (object == null) {
            return 0;
        }
        if (object instanceof Collection) {
            return ((Collection) object).size();
        }
        if (object.getClass().isArray()) {
            return Array.getLength(object);
        }
        return 0;
    }

    private static void hideActivityDecor(Object activity) {
        Object window = callNoArgs(activity, "getWindow");
        Object decor = callNoArgs(window, "getDecorView");
        hideView(decor);
    }

    private static void hideView(Object view) {
        if (view == null) {
            return;
        }
        call(view, "setAlpha", new Class[]{Float.TYPE}, new Object[]{Float.valueOf(0.0f)});
        call(view, "setVisibility", new Class[]{Integer.TYPE}, new Object[]{Integer.valueOf(GONE)});
    }

    private static void revealOverflowMenuButtons(Object view, int depth) {
        if (view == null || depth > 6) {
            return;
        }
        if (isOverflowMenuCandidate(view)) {
            call(view, "setVisibility", new Class[]{Integer.TYPE}, new Object[]{Integer.valueOf(VISIBLE)});
            call(view, "setAlpha", new Class[]{Float.TYPE}, new Object[]{Float.valueOf(1.0f)});
            call(view, "setEnabled", new Class[]{Boolean.TYPE}, new Object[]{Boolean.TRUE});
        }
        int childCount = countChildren(view);
        for (int i = 0; i < childCount; i++) {
            Object child = call(view, "getChildAt", new Class[]{Integer.TYPE}, new Object[]{Integer.valueOf(i)});
            revealOverflowMenuButtons(child, depth + 1);
        }
    }

    private static boolean isOverflowMenuCandidate(Object view) {
        String className = view.getClass().getName().toLowerCase(Locale.US);
        String resourceName = getResourceEntryName(view).toLowerCase(Locale.US);
        String description = String.valueOf(callNoArgs(view, "getContentDescription")).toLowerCase(Locale.US);
        return containsOverflowWord(className) || containsOverflowWord(resourceName) || containsOverflowWord(description);
    }

    private static boolean containsOverflowWord(String value) {
        return value.contains("more")
                || value.contains("menu")
                || value.contains("option")
                || value.contains("overflow")
                || value.contains("dot");
    }

    private static String getResourceEntryName(Object view) {
        Object id = callNoArgs(view, "getId");
        if (!(id instanceof Integer) || ((Integer) id).intValue() == -1) {
            return "";
        }
        Object resources = callNoArgs(view, "getResources");
        Object name = call(resources, "getResourceEntryName", new Class[]{Integer.TYPE}, new Object[]{id});
        return name == null ? "" : String.valueOf(name);
    }

    private static Object callNoArgs(Object target, String methodName) {
        return call(target, methodName, new Class[]{}, new Object[]{});
    }

    private static Object call(Object target, String methodName, Class[] parameterTypes, Object[] args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + message);
    }

    private static void log(String message, Throwable t) {
        XposedBridge.log(TAG + message + ": " + (t == null ? "null" : t.getClass().getSimpleName() + ": " + t.getMessage()));
    }

    private interface AfterHook {
        void run(XC_MethodHook.MethodHookParam param) throws Throwable;
    }

    private interface MethodMatcher {
        boolean matches(Method method);
    }
}
