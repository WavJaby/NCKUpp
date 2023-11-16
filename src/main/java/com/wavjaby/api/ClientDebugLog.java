package com.wavjaby.api;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Module;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RequestMethod;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.logger.Logger;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.wavjaby.lib.Lib.readRequestBody;

@RequestMapping("/api/v0")
public class ClientDebugLog implements Module {
    private static final String TAG = "ClientDebugLog";
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

    @RequestMapping(value = "/clientDebugLog", method = RequestMethod.POST)
    public RestApiResponse clientDebugLog(HttpExchange req) {
        long startTime = System.currentTimeMillis();

        ApiResponse response = new ApiResponse();

        String time = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now());
        try {
            String log = time + ":  " + readRequestBody(req, StandardCharsets.UTF_8).trim() + "\n\n";
            logFileOut.write(log.getBytes(StandardCharsets.UTF_8));
            logFileOut.flush();
        } catch (IOException e) {
            response.errorBadPayload("Read payload failed");
            logger.errTrace(e);
        }

        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return response;
    }
}
