package com.wavjaby.logger;

import static org.fusesource.jansi.Ansi.Color.*;

public enum LogLevel {
    INFO("\33[" + GREEN.fg() + "m"),
    WARN("\33[" + YELLOW.fg() + "m"),
    ERROR("\33[" + RED.fg() + "m"),
    DEBUG("\33[" + CYAN.fg() + "m");

    public final String nameWithColor;

    LogLevel(String style) {
        this.nameWithColor = style + name();
    }
}
