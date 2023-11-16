package com.wavjaby.logger;

import static com.wavjaby.logger.AnsiText.*;

public enum LogLevel {
    INFO(GREEN),
    WARN(YELLOW),
    ERROR(RED),
    DEBUG(CYAN);

    public final String nameWithColor;

    LogLevel(String style) {
        this.nameWithColor = style + name();
    }
}
