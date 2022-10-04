package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;

import static com.wavjaby.Cookie.getDefaultCookie;
import static com.wavjaby.Lib.setAllowOrigin;
import static com.wavjaby.Main.pool;

public class Moodle implements HttpHandler {
    @Override
    public void handle(HttpExchange req) {
        pool.submit(() -> {
            long startTime = System.currentTimeMillis();
            CookieManager cookieManager = new CookieManager();
            Headers requestHeaders = req.getRequestHeaders();
            getDefaultCookie(requestHeaders, cookieManager);


            try {
                boolean success = false;

                Headers responseHeader = req.getResponseHeaders();
                byte[] dataByte = "".getBytes(StandardCharsets.UTF_8);
                responseHeader.set("Content-Type", "application/json; charset=utf-8");

                // send response
                setAllowOrigin(requestHeaders, responseHeader);
                req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
                OutputStream response = req.getResponseBody();
                response.write(dataByte);
                response.flush();
                req.close();
            } catch (Exception e) {
                req.close();
                e.printStackTrace();
            }
            System.out.println("[Moodle] Get moodle " + (System.currentTimeMillis() - startTime) + "ms");
        });
    }
}
