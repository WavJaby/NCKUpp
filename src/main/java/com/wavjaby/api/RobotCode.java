package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.PropertiesReader;
import com.wavjaby.logger.Logger;

import java.io.*;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.setAllowOrigin;

public class RobotCode implements EndpointModule {
    private static final String TAG = "[RobotCode]";
    private static final Logger logger = new Logger(TAG);

    private final ProxyManager proxyManager;
    private final ProcessBuilder processBuilder;
    private BufferedReader stdout;
    private OutputStream stdin;
    private Process process;

    public enum Mode {
        SINGLE,
        MULTIPLE_CHECK
    }

    public enum WordType {
        HEX,
        ALPHA
    }

    public RobotCode(PropertiesReader serverSettings, ProxyManager proxyManager) {
        this.proxyManager = proxyManager;
        String workDir = serverSettings.getProperty("ocrWorkDir", "./");

        String venvPath = serverSettings.getProperty("ocrVenvPath", "./venv/bin/activate");

        String mainPyPath = serverSettings.getProperty("ocrMainPyPath", "./main.py");

        // Create process
        processBuilder = new ProcessBuilder(venvPath, mainPyPath);
        processBuilder.directory(new File(workDir));
        processBuilder.redirectErrorStream(true);
    }

    @Override
    public void start() {
        String startMessage = null;
        try {
            // Start python
            process = processBuilder.start();
            stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
            stdin = process.getOutputStream();
            // Force stop subprocess when main program stop
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopSubprocess));

            // Read ready
            startMessage = stdout.readLine();

            // Start read thread
            stdoutRead.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (startMessage == null)
            logger.err("Start error");
        else
            logger.log("Python: " + startMessage);
    }

    @Override
    public void stop() {
        stopSubprocess();
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        String loginState = getDefaultCookie(requestHeaders, cookieStore);

        try {
            // Crack robot code
            JsonObjectStringBuilder data = new JsonObjectStringBuilder();
            boolean success = true;
            data.append("success", success);

            // Set cookie
            Headers responseHeader = req.getResponseHeaders();
            packCourseLoginStateCookie(responseHeader, loginState, cookieStore);

            byte[] dataByte = data.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // Send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            req.close();
        }
        logger.log("Search " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private synchronized void stopSubprocess() {
        if (process == null) return;
        int result = -1;
        try {
            sendStopCommand();
            result = process.waitFor();
            stdoutRead.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            process.destroy();
        }
        process = null;
        logger.log("Process exited with code " + result);
    }

    private void sendStopCommand() {
        try {
            stdin.write('\n');
            stdin.flush();
        } catch (IOException ignore) {
        }
    }

    static class Task implements Future<String> {
        private final Object lock = new Object();
        private String result;
        private boolean done = false;
        private boolean cancel = false;

        public void done(String result) {
            this.result = result;
            done = true;
            synchronized (lock) {
                lock.notify();
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return cancel;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public String get() throws InterruptedException, ExecutionException {
            synchronized (lock) {
                lock.wait();
            }
            return result;
        }

        @Override
        public String get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            if (unit == TimeUnit.MICROSECONDS || unit == TimeUnit.NANOSECONDS)
                synchronized (lock) {
                    lock.wait(unit.toMillis(timeout), (int) (unit.toNanos(timeout) % 1000000));
                }
            else
                synchronized (lock) {
                    lock.wait(unit.toMillis(timeout));
                }
            return result;
        }
    }

    Map<String, Task> tasks = new HashMap<>();

    private Task sendCommand(String command) {
        Task task = new Task();
        String uuid;
        do {
            uuid = UUID.randomUUID().toString();
        } while (tasks.containsKey(uuid));
        tasks.put(uuid, task);
        try {
            stdin.write((uuid + '|' + command + '\n').getBytes());
            stdin.flush();
        } catch (IOException ignore) {
        }
        return task;
    }

    private final Thread stdoutRead = new Thread(() -> {
        try {
            while (true) {
                String output = stdout.readLine();
                if (output == null) break;
                int split = output.indexOf(',');
                Task task;
                if (split != -1 && (task = tasks.remove(output.substring(0, split))) != null)
                    task.done(output.substring(split + 1));
                else
                    logger.err(output);
            }
        } catch (IOException ignore) {
        }
    });

    public String getCode(String url, CookieStore cookieStore, Mode mode, WordType wordType) {
        StringBuilder builder = new StringBuilder();
        builder.append(mode.toString()).append('|').append(wordType.toString()).append('|').append(url).append("|{");
        if (cookieStore != null) {
            boolean mutiple = false;
            for (HttpCookie cookie : cookieStore.getCookies()) {
                if (mutiple)
                    builder.append(',');
                mutiple = true;
                builder.append('"').append(cookie.getName()).append("\":\"").append(cookie.getValue()).append('"');
            }
        }
        builder.append("}|");
        ProxyManager.ProxyData proxy = proxyManager.getProxyData();
        if (proxy != null) {
            if (proxy.protocol.startsWith("http"))
                builder.append(proxy.toIp());
            else
                builder.append(proxy.protocol).append("://").append(proxy.toIp());
        }

        Task task = sendCommand(builder.toString());
        try {
            return task.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return null;
        }
    }
}
