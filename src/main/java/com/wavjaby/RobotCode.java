package com.wavjaby;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.json.JsonBuilder;
import com.wavjaby.logger.Logger;

import java.io.*;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static com.wavjaby.Cookie.*;
import static com.wavjaby.Lib.getRefererUrl;
import static com.wavjaby.Lib.setAllowOrigin;
import static com.wavjaby.Main.pool;

public class RobotCode implements HttpHandler {
    private static final String TAG = "[RobotCode] ";

    BufferedReader stdout;
    OutputStream stdin;

    @Override
    public void handle(HttpExchange req) throws IOException {
        pool.submit(() -> {
            long startTime = System.currentTimeMillis();
            CookieManager cookieManager = new CookieManager();
            CookieStore cookieStore = cookieManager.getCookieStore();
            Headers requestHeaders = req.getRequestHeaders();
            String refererUrl = getRefererUrl(requestHeaders);

            try {
                // Unpack cookie
                String loginState = getDefaultCookie(requestHeaders, cookieManager);

                // Crack robot code
                JsonBuilder data = new JsonBuilder();
                boolean success = true;

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
        });
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
                tasks.remove(data[0]).done(data[1]);
            }
        } catch (IOException ignore) {
        }
    });

    private String getCode(String url, String PHPSESSID, String COURSE_WEB) {
        Task task = new Task();
        String uuid = UUID.randomUUID().toString();
        tasks.put(uuid, task);
        sendCommand(uuid + ',' + url + ',' + PHPSESSID + ',' + COURSE_WEB);
        task.await();
        return task.result;
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
        String connect = "&&";
        ProcessBuilder builder = new ProcessBuilder(venvPath, connect, "python", mainPyPath);
        builder.directory(new File(workDir));
        builder.redirectErrorStream(false);
        try {
            // Start python
            Process process = builder.start();
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
                System.out.printf("Process exited with code %d", result);
            }));

//            long startTime = System.currentTimeMillis();
//            String code = getCode("https://course.ncku.edu.tw/index.php?c=portal&m=robot", "1", "ffffffff8f7cbb1c45525d5f4f58455e445a4a423660");
//            Logger.log(TAG,(System.currentTimeMillis() - startTime) + "ms " + code);
        } catch (IOException ignore) {
        }
    }
}
