package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.logger.Logger;

import java.io.*;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static com.wavjaby.Cookie.getDefaultCookie;
import static com.wavjaby.Cookie.packLoginStateCookie;
import static com.wavjaby.Lib.getRefererUrl;
import static com.wavjaby.Lib.setAllowOrigin;

public class RobotCode implements EndpointModule {
    private static final String TAG = "[RobotCode] ";

    private final ProcessBuilder processBuilder;
    BufferedReader stdout;
    OutputStream stdin;

    public enum Mode {
        SINGLE,
        MULTIPLE_CHECK
    }

    public enum WordType {
        HEX,
        ALPHA
    }

    public RobotCode(Properties serverSettings) {
        String workDirProp = serverSettings.getProperty("ocrWorkDir");
        String workDir;
        if (workDirProp == null) {
            workDir = "./";
            Logger.warn(TAG, "OCR work dir not found, using current dir");
        } else
            workDir = workDirProp;

        String venvPathProp = serverSettings.getProperty("ocrVenvPath");
        String venvPath;
        if (venvPathProp == null) {
            venvPath = "./venv/bin/activate";
            Logger.warn(TAG, "OCR venv path not found, using default path");
        } else
            venvPath = venvPathProp;

        String mainPyPathProp = serverSettings.getProperty("ocrMainPyPath");
        String mainPyPath;
        if (mainPyPathProp == null) {
            mainPyPath = "./main.py";
            Logger.warn(TAG, "OCR main python file not found, using default path");
        } else
            mainPyPath = mainPyPathProp;

        // Create process
        processBuilder = new ProcessBuilder(venvPath, mainPyPath);
        processBuilder.directory(new File(workDir));
        processBuilder.redirectErrorStream(true);
    }

    @Override
    public void start() {
        try {
            // Start python
            Process process = processBuilder.start();
            stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
            stdin = process.getOutputStream();

            Logger.log(TAG, stdout.readLine());

            // Start read thread
            stdoutRead.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                int result;
                try {
                    sendStopCommand();
                    result = process.waitFor();
                    stdoutRead.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                Logger.log(TAG, "Process exited with code " + result);
            }));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {

    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        String refererUrl = getRefererUrl(requestHeaders);
        String loginState = getDefaultCookie(requestHeaders, cookieStore);

        try {
            // Crack robot code
            JsonObjectStringBuilder data = new JsonObjectStringBuilder();
            boolean success = true;
            data.append("success", success);

            // Set cookie
            Headers responseHeader = req.getResponseHeaders();
            packLoginStateCookie(responseHeader, loginState, refererUrl, cookieStore);

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
        Logger.log(TAG, "Search " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private void sendStopCommand() {
        try {
            stdin.write('\n');
            stdin.flush();
        } catch (IOException ignore) {
        }
    }

    private void sendCommand(String command) {
        try {
            stdin.write((command + '\n').getBytes());
            stdin.flush();
        } catch (IOException ignore) {
        }
    }

    static class Task {
        private final CountDownLatch countDown = new CountDownLatch(1);
        public String result;

        public void done(String result) {
            this.result = result;
            countDown.countDown();
        }

        public void await() {
            try {
                countDown.await();
            } catch (InterruptedException ignore) {
            }
        }
    }

    Map<String, Task> tasks = new HashMap<>();
    private final Thread stdoutRead = new Thread(() -> {
        try {
            while (true) {
                String output = stdout.readLine();
                if (output == null) break;
                String[] data = output.split(",");
                Task task = tasks.remove(data[0]);
                if (task == null)
                    Logger.err(TAG, output);
                else
                    task.done(data[1]);
            }
        } catch (IOException ignore) {
        }
    });

    public String getCode(String url, CookieStore cookieStore, Mode mode, WordType wordType) {
        Task task = new Task();
        String uuid = UUID.randomUUID().toString();
        tasks.put(uuid, task);
        String cookies = cookieStore.getCookies().stream()
                .map(i -> '"' + i.getName() + '"' + ':' + '"' + i.getValue() + '"')
                .collect(Collectors.joining(","));
        sendCommand(uuid + '|' + mode.toString() + '|' + wordType.toString() + '|' + url + '|' + '{' + cookies + '}');
        task.await();
        return task.result;
    }
}
