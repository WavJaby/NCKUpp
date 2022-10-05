package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.json.JsonBuilder;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

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
import static com.wavjaby.Main.pool;

public class ExtractUrl implements HttpHandler {
    @Override
    public void handle(HttpExchange req) {
        pool.submit(() -> {
            long startTime = System.currentTimeMillis();
            CookieManager cookieManager = new CookieManager();
            CookieStore cookieStore = cookieManager.getCookieStore();
            Headers requestHeaders = req.getRequestHeaders();
            getDefaultCookie(requestHeaders, cookieManager);

            try {
                JsonBuilder data = new JsonBuilder();
                String queryString = req.getRequestURI().getQuery();
                boolean success = false;
                if (queryString == null)
                    data.append("err", "[Extract] no query string found");
                else {
                    Map<String, String> query = parseUrlEncodedForm(queryString);
                    if (query.containsKey("m"))
                        success = getMoodle(query.get("m"), cookieStore, data);
                    else if (query.containsKey("l"))
                        success = getLocation(query.get("l"), cookieStore, data);
                }

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
            System.out.println("[Extract] Extract url " + (System.currentTimeMillis() - startTime) + "ms");
        });
    }

    private boolean getMoodle(String requestData, CookieStore cookieStore, JsonBuilder data) {
        String[] query = requestData.split(",");
        if (query.length != 3) {
            data.append("err", "[Extract] invalid query data");
            return false;
        }

        try {
            String body = Jsoup.connect(courseNckuOrg + "/index.php?c=portal&m=moodle")
                    .cookieStore(cookieStore)
                    .method(Connection.Method.POST)
                    .requestBody((System.currentTimeMillis() / 1000) +
                            "&syear=" + query[0] + "&sem=" + query[1] + "&course=" + query[2])
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .execute().body();
            data.append("data", body, true);
            return true;
        } catch (IOException e) {
            data.append("err", "[Moodle] Unknown error: " + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return false;
    }

    private boolean getLocation(String requestData, CookieStore cookieStore, JsonBuilder data) {
        String[] query = requestData.split(",");
        if (query.length != 2) {
            data.append("err", "[Extract] invalid query data");
            return false;
        }

        try {
            String body = Jsoup.connect(courseNckuOrg + "/index.php?c=portal&m=maps")
                    .cookieStore(cookieStore)
                    .method(Connection.Method.POST)
                    .requestBody((System.currentTimeMillis() / 1000) +
                            "&location=" + query[0] + "&room_no=" + query[1])
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .execute().body();
            data.append("data", body, true);
            return true;
        } catch (IOException e) {
            data.append("err", "[Moodle] Unknown error: " + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return false;
    }
}
