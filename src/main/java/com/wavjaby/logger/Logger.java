package com.wavjaby.logger;

public class Logger {
    public static <T> void log(String tag, T message) {
        System.out.println(tag + message);
    }

    public static <T> void warn(String tag, T message) {
        System.out.println(tag + message);
    }

    public static <T> void err(String tag, T message) {
        System.err.println(tag + message);
    }
}
