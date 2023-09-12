package com.wavjaby.api;

import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.Lib;
import com.wavjaby.logger.Logger;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ClientDebugLog implements EndpointModule {
    private static final String TAG = "[ClientDebugLog]";
    private static final Logger logger = new Logger(TAG);

    FileOutputStream logFileOut;

    @Override
    public void start() {
        try {
            logFileOut = new FileOutputStream("clientLog.txt", true);
        } catch (FileNotFoundException e) {
            logger.errTrace(e);
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

        ApiResponse apiResponse = new ApiResponse();

        if (req.getRequestMethod().equalsIgnoreCase("POST")) {
            String time = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now());
            String log = time + ":  " + Lib.readRequestBody(req, StandardCharsets.UTF_8).trim() + "\n\n";
            logFileOut.write(log.getBytes(StandardCharsets.UTF_8));
            logFileOut.flush();
        }

        apiResponse.sendResponse(req);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
