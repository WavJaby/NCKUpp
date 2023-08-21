package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonException;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
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
            Map<String, String> query = parseUrlEncodedForm(queryString);
            if (query.containsKey("moodle"))
                getMoodle(query.get("moodle"), cookieStore, apiResponse);
            else if (query.containsKey("location"))
                getLocation(query.get("location"), cookieStore, apiResponse);
            else
                apiResponse.errorBadQuery("Query require one of \"moodle\" or \"location\"");

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
        logger.log("Extract url " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private void getMoodle(String requestData, CookieStore cookieStore, ApiResponse response) {
        String[] query = requestData.split(",");
        if (query.length != 3) {
            response.errorParse("Invalid query data");
            return;
        }

        try {
            String body = HttpConnection.connect(courseNckuOrg + "/index.php?c=portal&m=moodle")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .method(Connection.Method.POST)
                    .requestBody("time=" + (System.currentTimeMillis() / 1000) + "&syear=" + query[0] + "&sem=" + query[1] + "&course=" + query[2])
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .execute().body();
            parseResponse(body, true, response);
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
    }

    private void getLocation(String requestData, CookieStore cookieStore, ApiResponse response) {
        String[] query = requestData.split(",");
        if (query.length != 2) {
            response.errorParse("Invalid query data");
            return;
        }

        try {
            String body = HttpConnection.connect(courseNckuOrg + "/index.php?c=portal&m=maps")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .method(Connection.Method.POST)
                    .requestBody("time=" + (System.currentTimeMillis() / 1000) + "&location=" + query[0] + "&room_no=" + query[1])
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .execute().body();
            parseResponse(body, false, response);
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
    }

    private void parseResponse(String body, boolean moodle, ApiResponse response) {
        JsonObject data;
        try {
            data = new JsonObject(body);
        } catch (JsonException e) {
            logger.errTrace(e);
            response.errorParse("Response Json parse error: " + e.getMessage());
            return;
        }
        // Get server response message
        String message = data.getString("msg");
        if (message.trim().isEmpty())
            message = null;
        // Check success
        if (!data.getBoolean("status")) {
            response.errorCourseNcku();
            if (message == null)
                message = "Unknown error";
            response.setMessageDisplay(message);
            return;
        }
        // Get url
        String url = data.getString("url");
        // If url invalid
        if (url == null) {
            response.errorParse("Url invalid");
            return;
        }
        url = url.trim();
        if (moodle && url.endsWith("?id=")) {
            response.errorBadQuery("Query \"moodle\" invalid");
            return;
        }

        response.setData(new JsonObjectStringBuilder().append("url", url).toString());
    }
}
