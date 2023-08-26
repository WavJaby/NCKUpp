package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static com.wavjaby.lib.Lib.setAllowOrigin;

public class UsefulWebsite implements EndpointModule {
    private static final String TAG = "[UsefulWebsite]";
    private static final Logger logger = new Logger(TAG);

    @Override
    public void start() {
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
            String method = req.getRequestMethod();
            if (method.equalsIgnoreCase("GET"))
                getLinks(apiResponse);
            else
                apiResponse.errorUnsupportedHttpMethod(method);

            Headers responseHeader = req.getResponseHeaders();
            byte[] dataByte = apiResponse.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(apiResponse.getResponseCode(), dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            logger.errTrace(e);
            req.close();
        }
        logger.log("Get template " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private void getLinks(ApiResponse response) {

    }
}
