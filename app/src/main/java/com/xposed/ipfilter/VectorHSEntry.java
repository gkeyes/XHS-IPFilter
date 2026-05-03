package com.xposed.ipfilter;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Vector/LSPosed 现代模块入口（生存版）
 * 
 * 对比旧版的核心变化：
 * - ❌ 移除 TextView.setText hook（最大检测风险源）
 * - ❌ 移除 IPFilterCore（View层操纵，全部砍掉）
 * - ✅ 只保留 Application.attach + setItems 数据层过滤
 * - ✅ 数据层删 item + ne8.c 同步
 */
public final class VectorHSEntry extends XposedModule {
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
                            onAppAttached((Context) arg);
                        }
                        return result;
                    });
            LogWriter.i("Hooked Application.attach ✓");
        } catch (Throwable t) {
            LogWriter.e("Hook Application.attach failed", t);
        }
    }

    private void onAppAttached(Context context) {
        if (!INITIALIZED.compareAndSet(false, true)) return;

        Context appContext = context.getApplicationContext();
        if (appContext == null) appContext = context;

        try {
            SharedPreferences prefs = appContext.getSharedPreferences(
                    "ipfilter_prefs", Context.MODE_PRIVATE);
            PrefManager prefManager = new PrefManager(prefs, appContext.getFilesDir());

            ClassLoader cl = context.getClassLoader();
            if (cl == null) cl = appContext.getClassLoader();

            LogWriter.i("IPFilter survival v4.0.2 initializing, classloader=" + cl);

            // ★ 唯一Hook点：数据层 setItems
            DataLayerFilter.resolveFields(cl);
            hookDataLayerFilter(cl, prefManager);

            // setText hook 已移除。View层零干预。

            LogWriter.i("IPFilter survival mode initialized ✓");
        } catch (Throwable t) {
            LogWriter.e("Initialization failed", t);
        }
    }

    /**
     * 数据层过滤器 Hook（生存版）
     * 在 CommentListAdapter.setItems(List) 被调用时，在数据进入 RecyclerView 之前
     * 移除非白名单评论
     */
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

    // ========== 辅助 ==========

    private String safeProcessName(XposedModuleInterface.ModuleLoadedParam param) {
        try { return param.getProcessName(); } catch (Throwable t) { return "<unknown>"; }
    }
}