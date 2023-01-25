package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.Module;
import com.wavjaby.json.JsonBuilder;
import com.wavjaby.logger.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;

import static com.wavjaby.Cookie.getDefaultCookie;
import static com.wavjaby.Cookie.packLoginStateCookie;
import static com.wavjaby.Lib.getRefererUrl;
import static com.wavjaby.Lib.setAllowOrigin;

@SuppressWarnings("ALL")
public class Template implements Module {
    private static final String TAG = "[Template] ";

    @Override
    public void start() {

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
            JsonBuilder data = new JsonBuilder();
            boolean success = false;
            data.append("success", success);

            Headers responseHeader = req.getResponseHeaders();
            packLoginStateCookie(responseHeader, loginState, refererUrl, cookieStore);
            byte[] dataByte = data.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            req.close();
            e.printStackTrace();
        }
        Logger.log(TAG, "Get template " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
