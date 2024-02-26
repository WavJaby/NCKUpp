package com.wavjaby.logger;

import com.wavjaby.lib.ThreadFactory;

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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.wavjaby.logger.LogLevel.*;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.time.temporal.ChronoField.*;

public class Logger {
    public static final String RESET = "\33[0m";
    public static final String RED = "\33[31m";
    public static final String MAGENTA = "\33[35m";
    private static final List<Progressbar> PROGRESSBAR = new ArrayList<>();
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
    private static ReadWriteLock logFileOutLock;
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
            logFileOutLock = new ReentrantReadWriteLock();
            if (scheduledExecutor == null) {
                scheduledExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory("Logger"));
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
        logFileOutLock.readLock().lock();
        try {
            logFileOut.write(logFileOutBuff.toByteArray());
            logFileOutBuff.reset();
        } catch (IOException ignore) {
        } finally {
            logFileOutLock.readLock().unlock();
        }
    }

    public static void stopAndFlushLog() {
        logFileOutLock.readLock().lock();
        try {
            logFileOut.write(logFileOutBuff.toByteArray());
            logFileOutBuff.close();
            logFileOut.close();
            scheduledExecutor.shutdown();
            if (!scheduledExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS))
                scheduledExecutor.shutdownNow();
        } catch (InterruptedException | IOException ignore) {
        } finally {
            logFileOutLock.readLock().unlock();
        }
    }

    public <T> void debug(final T message) {
        writeLog(DEBUG, tag, String.valueOf(message), true);
    }

    public <T> void log(final T message) {
        writeLog(INFO, tag, String.valueOf(message), true);
    }

    public <T> void warn(final T message) {
        writeLog(WARN, tag, String.valueOf(message), true);
    }

    public <T> void err(final T message) {
        writeLog(ERROR, tag, String.valueOf(message), true);
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

        writeLog(ERROR, tag, builder.toString(), true);
    }

    public static Progressbar addProgressbar(final String tag) {
        Progressbar progressbar = new Progressbar(tag, PROGRESS_EVENT);
        PROGRESSBAR.add(progressbar);
        return progressbar;
    }

    public static void removeProgressbar(final Progressbar progressbar) {
        System.out.print("\33[2K\r" + getTimeStamp() + " [" + progressbar.tag + "] finish" + "\n>  ");
        PROGRESSBAR.remove(progressbar);
    }

    public static void setTraceClassFilter(String traceClassFilters) {
        if (traceClassFilters.isEmpty())
            return;
        traceClassFilter = traceClassFilters.split(" *, *");
    }

    private static void writeLog(LogLevel level, String tag, String message, boolean inputPrefix) {
        String time = getTimeStamp() + ' ';
        if (level == ERROR) {
            System.err.print("\33[2K\r" + time + level.nameWithColor + ' ' + MAGENTA + tag + RESET + ' ' + message + '\n');
            if (inputPrefix)
                System.out.print("> ");
        } else
            System.out.print("\33[2K\r" + time + level.nameWithColor + ' ' + MAGENTA + tag + RESET + ' ' + message +
                    (inputPrefix ? "\n> " : '\n'));

        if (logFileOutBuff != null) {
            logFileOutLock.writeLock().lock();
            try {
                logFileOutBuff.write((time + level.name() + ' ' + tag + ' ' + message + '\n').getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignore) {
            } finally {
                logFileOutLock.writeLock().unlock();
            }
        }
    }

    private static void renderProgressbar() {
        StringBuilder builder = new StringBuilder();
        for (Progressbar progressbar : PROGRESSBAR) {
            builder.append('[').append(progressbar.tag).append("] ");
            if (progressbar.message != null)
                builder.append(progressbar.message).append(' ');
            builder.append(format.format(progressbar.progress)).append("% ");
        }
        System.out.print("\33[2K\r" + builder + ">  ");
    }

    private static String getTimeStamp() {
        return OffsetDateTime.now().format(formatter);
    }
}
