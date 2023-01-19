package com.wavjaby.logger;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class Logger {
    private static final List<ProgressBar> progressBars = new ArrayList<>();
    private static final ProgressBar.OnProgress onProgress = () -> System.out.print('\r' + renderProgressBar());

    private static final DecimalFormat format = new DecimalFormat("#.##");

    public static <T> void log(String tag, T message) {
        if (progressBars.size() > 0)
            System.out.print('\r' + tag + message + '\n' + renderProgressBar());
        else
            System.out.print('\r' + tag + message + '\n' + '>');
    }

    public static <T> void warn(String tag, T message) {
        if (progressBars.size() > 0)
            System.out.print('\r' + tag + message + '\n' + renderProgressBar());
        else
            System.out.print('\r' + tag + message + '\n' + '>');
    }

    public static <T> void err(String tag, T message) {
        if (progressBars.size() > 0)
            System.err.print('\r' + tag + message + '\n' + renderProgressBar());
        else
            System.err.print('\r' + tag + message + '\n');
        System.out.print('>');
    }

    private static String renderProgressBar() {
        StringBuilder builder = new StringBuilder();
        for (ProgressBar progressBar : progressBars)
            builder.append(progressBar.tag).append(format.format(progressBar.progress)).append('%')
                    .append(' ');
        builder.append('>');
        return builder.toString();
    }

    public static void addProgressBar(ProgressBar progressBar) {
        progressBar.setListener(onProgress);
        progressBars.add(progressBar);
    }

    public static void removeProgressBar(ProgressBar progressBar) {
        System.out.println("\r" + progressBar.tag + format.format(progressBar.progress) + "%");
        progressBars.remove(progressBar);
    }
}
