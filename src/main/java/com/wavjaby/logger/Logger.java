package com.wavjaby.logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.wavjaby.logger.AnsiText.*;
import static com.wavjaby.logger.LogLevel.*;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.time.temporal.ChronoField.*;

public class Logger {
    private static final List<Progressbar> PROGRESSBARS = new ArrayList<>();
    private static final DecimalFormat format = new DecimalFormat("#.##");
    private static final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .appendValue(MONTH_OF_YEAR, 2).appendLiteral('-').appendValue(DAY_OF_MONTH, 2)
            .appendLiteral('T').appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':').appendValue(MINUTE_OF_HOUR, 2)
            .appendLiteral(':').appendValue(SECOND_OF_MINUTE, 2)
            .appendFraction(MILLI_OF_SECOND, 3, 3, true)
            .toFormatter();
    private static final Progressbar.ProgressEvent PROGRESS_EVENT = new Progressbar.ProgressEvent() {
        @Override
        public void onChange() {
            Logger.renderProgressbar();
        }

        @Override
        public void onFinish(Progressbar progressbar) {
            Logger.removeProgressbar(progressbar);
        }
    };
    private final String tag;
    private static OutputStream logFileOut;
    private static ByteArrayOutputStream logFileOutBuff;
    private static String[] traceClassFilter = null;
    private static ScheduledExecutorService scheduledExecutor;
    private static File logFile;

    public Logger(String tag) {
        this.tag = '[' + tag + ']';
    }

    public static void setLogFile(String logFilePath) {
        if (logFilePath == null) {
            return;
        }
        logFile = new File(logFilePath);
        try {
            if (!logFile.exists())
                if (!logFile.createNewFile())
                    writeLog(ERROR, "Logger", "Failed to create log file", true);
            logFileOut = Files.newOutputStream(logFile.toPath(), CREATE, APPEND);
            logFileOutBuff = new ByteArrayOutputStream();
            if (scheduledExecutor == null) {
                scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
                scheduledExecutor.scheduleWithFixedDelay(Logger::flushLog, 100, 100, TimeUnit.MILLISECONDS);
            }
        } catch (IOException e) {
            writeLog(ERROR, "Logger", "Failed to open log file", true);
        }
    }

    public static File getLogFile() {
        return logFile;
    }

    public static void flushLog() {
        try {
            logFileOut.write(logFileOutBuff.toByteArray());
            logFileOutBuff.reset();
        } catch (IOException ignore) {
        }
    }

    public static void stopAndFlushLog() {
        try {
            logFileOut.write(logFileOutBuff.toByteArray());
            logFileOutBuff.close();
            logFileOut.close();
            scheduledExecutor.shutdown();
            if (!scheduledExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS))
                scheduledExecutor.shutdownNow();
        } catch (InterruptedException | IOException ignore) {
        }
    }

    public <T> void debug(final T message) {
        writeLog(DEBUG, tag, String.valueOf(message), true);
    }

    public <T> void log(final T message) {
        writeLog(INFO, tag, String.valueOf(message), true);
    }

    public <T> void log(final T message, final boolean inputPrefix) {
        writeLog(INFO, tag, String.valueOf(message), inputPrefix);
    }

    public <T> void warn(final T message) {
        writeLog(WARN, tag, String.valueOf(message), true);
    }

    public <T> void err(final T message) {
        writeErr(tag, String.valueOf(message), true);
    }

    public void errTrace(final Throwable e) {
        errTrace(tag, e);
    }

    public static void errTrace(final String tag, final Throwable e) {
        StringBuilder builder = new StringBuilder(e.getClass().getName());
        String msg = e.getLocalizedMessage();
        if (msg != null)
            builder.append(": ").append(msg);
        builder.append('\n');

        StackTraceElement[] trace = e.getStackTrace();
        for (StackTraceElement traceElement : trace) {
            if (traceClassFilter != null) {
                String name = traceElement.getClassName();
                boolean notfound = true;
                for (String s : traceClassFilter)
                    if (name.startsWith(s)) {
                        notfound = false;
                        break;
                    }
                if (notfound)
                    continue;
            }
            builder.append("\tat ").append(traceElement).append('\n');
        }

        writeErr(tag, builder.toString(), true);
    }

    public static Progressbar addProgressbar(final String tag) {
        Progressbar progressbar = new Progressbar(tag, PROGRESS_EVENT);
        PROGRESSBARS.add(progressbar);
        return progressbar;
    }

    public static void setTraceClassFilter(String traceClassFilters) {
        if (traceClassFilters.isEmpty())
            return;
        traceClassFilter = traceClassFilters.split(" *, *");
    }

    private static void writeLog(LogLevel level, String tag, String message, boolean inputPrefix) {
        String time = getTimeStamp() + ' ';
        System.out.print("\33[2K\r" + time + level.nameWithColor + ' ' + MAGENTA + tag + RESET + ' ' + message +
                (inputPrefix ? "\n> " : '\n'));

        if (logFileOutBuff != null) {
            try {
                logFileOutBuff.write((time + level.name() + ' ' + tag + ' ' + message + '\n').getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignore) {
            }
        }
    }

    private static void writeErr(String tag, String message, boolean inputPrefix) {
        String time = getTimeStamp() + ' ';
        System.err.print("\33[2K\r" + time + ERROR.nameWithColor + ' ' + MAGENTA + tag + RED + ' ' + message + RESET +
                (inputPrefix ? "\n> " : '\n'));

        if (logFileOutBuff != null) {
            try {
                logFileOutBuff.write((time + ERROR.name() + ' ' + tag + ' ' + message + '\n').getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignore) {
            }
        }
    }

    private static void renderProgressbar() {
        StringBuilder builder = new StringBuilder();
        for (Progressbar progressbar : PROGRESSBARS) {
            builder.append('[').append(progressbar.tag).append("] ");
            if (progressbar.message != null)
                builder.append(progressbar.message).append(' ');
            builder.append(format.format(progressbar.progress)).append("% ");
        }
        System.out.print("\33[2K\r" + builder + ">  ");
    }

    private static void removeProgressbar(final Progressbar progressbar) {
        System.out.print("\33[2K\r" + getTimeStamp() + " [" + progressbar.tag + "] finish" + "\n>  ");
        PROGRESSBARS.remove(progressbar);
    }

    private static String getTimeStamp() {
        return OffsetDateTime.now().format(formatter);
    }
}
