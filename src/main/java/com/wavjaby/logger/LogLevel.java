package com.wavjaby.logger;

public enum LogLevel {
    INFO("\33[32m"),
    WARN("\33[33m"),
    ERROR("\33[31m"),
    DEBUG("\33[36m");

    public final String nameWithColor;

    LogLevel(String style) {
        this.nameWithColor = style + name();
    }
}
