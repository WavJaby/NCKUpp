package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.ProxyManager;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Lib.parseUrlEncodedForm;
import static com.wavjaby.lib.Lib.setAllowOrigin;

public class ExtractUrl implements EndpointModule {
    private static final String TAG = "[Extract]";
    private static final Logger logger = new Logger(TAG);
    private final ProxyManager proxyManager;

    public ExtractUrl(ProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }

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
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        getDefaultCookie(requestHeaders, cookieStore);

        try {
            ApiResponse apiResponse = new ApiResponse();
            String queryString = req.getRequestURI().getRawQuery();
            boolean success = false;
            if (queryString == null)
                apiResponse.addError(TAG + "No query string found");
            else {
                Map<String, String> query = parseUrlEncodedForm(queryString);
                if (query.containsKey("m"))
                    success = getMoodle(query.get("m"), cookieStore, apiResponse);
                else if (query.containsKey("l"))
                    success = getLocation(query.get("l"), cookieStore, apiResponse);
            }

            Headers responseHeader = req.getResponseHeaders();
            byte[] dataByte = apiResponse.toString().getBytes(StandardCharsets.UTF_8);
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
        logger.log("Extract url " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private boolean getMoodle(String requestData, CookieStore cookieStore, ApiResponse response) {
        String[] query = requestData.split(",");
        if (query.length != 3) {
            response.addError(TAG + "Invalid query data");
            return false;
        }

        try {
            String body = HttpConnection.connect(courseNckuOrg + "/index.php?c=portal&m=moodle")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .method(Connection.Method.POST)
                    .requestBody((System.currentTimeMillis() / 1000) +
                            "&syear=" + query[0] + "&sem=" + query[1] + "&course=" + query[2])
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .execute().body();
            response.setData(body);
            return true;
        } catch (IOException e) {
            response.addError(TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return false;
    }

    private boolean getLocation(String requestData, CookieStore cookieStore, ApiResponse response) {
        String[] query = requestData.split(",");
        if (query.length != 2) {
            response.addError(TAG + "Invalid query data");
            return false;
        }

        try {
            String body = HttpConnection.connect(courseNckuOrg + "/index.php?c=portal&m=maps")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .method(Connection.Method.POST)
                    .requestBody((System.currentTimeMillis() / 1000) +
                            "&location=" + query[0] + "&room_no=" + query[1])
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .execute().body();
            response.setData(body);
            return true;
        } catch (IOException e) {
            response.addError(TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return false;
    }
}
