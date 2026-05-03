package com.xposed.ipfilter;

/**
 * LogWriter - 已清空，所有方法为空操作
 */
public class LogWriter {
    private LogWriter() {}

    public static void init() {}
    public static void i(@SuppressWarnings("unused") String msg) {}
    public static void d(@SuppressWarnings("unused") String msg) {}
    public static void w(@SuppressWarnings("unused") String msg) {}
    public static void e(@SuppressWarnings("unused") String msg, @SuppressWarnings("unused") Throwable t) {}
    public static void sep() {}
    public static void close() {}
}