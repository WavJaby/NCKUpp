package com.wavjaby.logger;

import java.text.DecimalFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.ChronoField.*;

public class Logger {
    private static final List<ProgressBar> progressBars = new ArrayList<>();
    private static final ProgressBar.OnProgress onProgress = () -> System.out.print('\r' + renderProgressBar());

    private static final DecimalFormat format = new DecimalFormat("#.##");

    public static <T> void log(String tag, T message) {
        if (progressBars.size() > 0)
            System.out.print('\r' + getTimeStamp() + tag + message + '\n' + renderProgressBar() + '>');
        else
            System.out.print('\r' + getTimeStamp() + tag + message + '\n' + '>');
    }

    public static <T> void warn(String tag, T message) {
        if (progressBars.size() > 0)
            System.out.print('\r' + getTimeStamp() + tag + message + '\n' + renderProgressBar() + '>');
        else
            System.out.print('\r' + getTimeStamp() + tag + message + '\n' + '>');
    }

    public static <T> void err(String tag, T message) {
        if (progressBars.size() > 0)
            System.err.print('\r' + getTimeStamp() + tag + message + '\n' + renderProgressBar() + '>');
        else
            System.err.print('\r' + getTimeStamp() + tag + message + '\n' + '>');
    }

    private static String renderProgressBar() {
        StringBuilder builder = new StringBuilder();
        for (ProgressBar progressBar : progressBars)
            builder.append(progressBar.tag).append(format.format(progressBar.progress)).append('%')
                    .append(' ');
        return builder.toString();
    }

    public static void addProgressBar(ProgressBar progressBar) {
        progressBar.setListener(onProgress);
        progressBars.add(progressBar);
    }

    public static void removeProgressBar(ProgressBar progressBar) {
        System.out.println('\r' + progressBar.tag + format.format(progressBar.progress) + '%');
        progressBars.remove(progressBar);
    }

    private static final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(MILLI_OF_SECOND, 3, 3, true)
            .toFormatter();

    private static String getTimeStamp() {
        return OffsetDateTime.now().format(formatter) + ' ';
    }
}
