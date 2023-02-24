package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonObjectStringBuilder;
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

import static com.wavjaby.Cookie.getDefaultCookie;
import static com.wavjaby.Lib.parseUrlEncodedForm;
import static com.wavjaby.Lib.setAllowOrigin;
import static com.wavjaby.Main.courseNckuOrg;

public class ExtractUrl implements EndpointModule {
    private static final String TAG = "[Extract] ";


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
        getDefaultCookie(requestHeaders, cookieStore);

        try {
            JsonObjectStringBuilder data = new JsonObjectStringBuilder();
            String queryString = req.getRequestURI().getQuery();
            boolean success = false;
            if (queryString == null)
                data.append("err", TAG + "No query string found");
            else {
                Map<String, String> query = parseUrlEncodedForm(queryString);
                if (query.containsKey("m"))
                    success = getMoodle(query.get("m"), cookieStore, data);
                else if (query.containsKey("l"))
                    success = getLocation(query.get("l"), cookieStore, data);
            }
            data.append("success", success);

            Headers responseHeader = req.getResponseHeaders();
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
        Logger.log(TAG, "Extract url " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private boolean getMoodle(String requestData, CookieStore cookieStore, JsonObjectStringBuilder data) {
        String[] query = requestData.split(",");
        if (query.length != 3) {
            data.append("err", TAG + "Invalid query data");
            return false;
        }

        try {
            String body = HttpConnection.connect(courseNckuOrg + "/index.php?c=portal&m=moodle")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .method(Connection.Method.POST)
                    .requestBody((System.currentTimeMillis() / 1000) +
                            "&syear=" + query[0] + "&sem=" + query[1] + "&course=" + query[2])
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .execute().body();
            data.appendRaw("data", body);
            return true;
        } catch (IOException e) {
            data.append("err", TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return false;
    }

    private boolean getLocation(String requestData, CookieStore cookieStore, JsonObjectStringBuilder data) {
        String[] query = requestData.split(",");
        if (query.length != 2) {
            data.append("err", TAG + "Invalid query data");
            return false;
        }

        try {
            String body = HttpConnection.connect(courseNckuOrg + "/index.php?c=portal&m=maps")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .method(Connection.Method.POST)
                    .requestBody((System.currentTimeMillis() / 1000) +
                            "&location=" + query[0] + "&room_no=" + query[1])
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .execute().body();
            data.appendRaw("data", body);
            return true;
        } catch (IOException e) {
            data.append("err", TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return false;
    }
}
