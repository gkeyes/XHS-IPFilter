package com.xposed.ipfilter;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Vector/LSPosed 现代模块入口
 * 通过 META-INF/xposed/java_init.list 注册
 */
public final class VectorHSEntry extends XposedModule {
    private static final String TAG = "IPFilter-Vector";
    private static final String TARGET_PACKAGE = "com.xingin.xhs";

    private static final AtomicBoolean ATTACH_HOOKED = new AtomicBoolean(false);
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    public VectorHSEntry() {
        super();
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        LogWriter.init();
        LogWriter.i("Module loaded in process: " + safeProcessName(param));
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String packageName = param.getPackageName();
        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }

        LogWriter.i("Target package loaded: " + packageName);

        if (!ATTACH_HOOKED.compareAndSet(false, true)) return;

        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            hook(attach)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object arg = chain.getArg(0);
                        if (arg instanceof Context) {
                            onAppAttached((Context) arg, packageName);
                        }
                        return result;
                    });
            LogWriter.i("Hooked Application.attach for " + packageName);
        } catch (Throwable t) {
            LogWriter.e("Hook Application.attach failed", t);
        }
    }

    private void onAppAttached(Context context, String packageName) {
        if (!INITIALIZED.compareAndSet(false, true)) return;

        Context appContext = context.getApplicationContext();
        if (appContext == null) appContext = context;

        try {
            SharedPreferences prefs = appContext.getSharedPreferences(
                    "ipfilter_prefs", Context.MODE_PRIVATE);
            PrefManager prefManager = new PrefManager(prefs, appContext.getFilesDir());

            ClassLoader cl = context.getClassLoader();
            if (cl == null) cl = appContext.getClassLoader();

            LogWriter.i("IPFilter v3.4.0 initializing, classloader=" + cl);

            DataLayerFilter.resolveFields(cl);
            hookDataLayerFilter(cl, prefManager);
            hookSetText(cl, prefManager);

            LogWriter.i("IPFilter initialized successfully");
        } catch (Throwable t) {
            LogWriter.e("Initialization failed", t);
        }
    }

    private void hookDataLayerFilter(ClassLoader cl, PrefManager prefManager) {
        try {
            Class<?> adapterClass = cl.loadClass("com.xingin.comment.consumer.list.CommentListAdapter");
            Method setItems = adapterClass.getDeclaredMethod("setItems", List.class);

            hook(setItems)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object arg = chain.getArg(0);
                        if (arg instanceof List) {
                            DataLayerFilter.filter((List<?>) arg, prefManager);
                        }
                        return chain.proceed();
                    });

            LogWriter.i("DataLayer: Hooked CommentListAdapter.setItems(List) ✓");
        } catch (Throwable t) {
            LogWriter.e("DataLayer: Hook setItems failed", t);
        }
    }

    private void hookSetText(ClassLoader cl, PrefManager prefManager) {
        try {
            Class<?> textViewClass = cl.loadClass("android.widget.TextView");
            IPFilterCore core = new IPFilterCore(prefManager);

            Method setTextCS = textViewClass.getDeclaredMethod("setText", CharSequence.class);
            hook(setTextCS)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object arg = chain.getArg(0);
                        if (arg instanceof CharSequence) {
                            CharSequence cs = (CharSequence) arg;
                            if (cs != null) {
                                core.handle((TextView) chain.getThisObject(), cs);
                            }
                        }
                        return chain.proceed();
                    });

            LogWriter.i("ViewLayer: Hooked TextView.setText ✓");
        } catch (Throwable t) {
            LogWriter.e("Failed to hook setText", t);
        }
    }

    private String safeProcessName(XposedModuleInterface.ModuleLoadedParam param) {
        try { return param.getProcessName(); } catch (Throwable t) { return "<unknown>"; }
    }
}