package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.Lib;
import com.wavjaby.logger.Logger;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.wavjaby.lib.Lib.setAllowOrigin;

public class ClientDebugLog implements EndpointModule {
    private static final String TAG = "[ClientDebugLog]";
    private static final Logger logger = new Logger(TAG);

    FileOutputStream logFileOut;

    @Override
    public void start() {
        try {
            logFileOut = new FileOutputStream("clientLog.txt", true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        Headers requestHeaders = req.getRequestHeaders();

        try {
            ApiResponse apiResponse = new ApiResponse();

            if (req.getRequestMethod().equalsIgnoreCase("POST")) {
                String time = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now());
                String log = time + ":  " + Lib.readRequestBody(req).trim() + "\n\n";
                logFileOut.write(log.getBytes(StandardCharsets.UTF_8));
                logFileOut.flush();
            }

            Headers responseHeader = req.getResponseHeaders();
            byte[] dataByte = apiResponse.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(apiResponse.isSuccess() ? 200 : 400, dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            req.close();
            e.printStackTrace();
        }
        logger.log("ClientDebugLog " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
