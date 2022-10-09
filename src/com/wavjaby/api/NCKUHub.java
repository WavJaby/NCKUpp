package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonBuilder;
import com.wavjaby.json.JsonObject;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;

import static com.wavjaby.Cookie.getDefaultCookie;
import static com.wavjaby.Lib.setAllowOrigin;
import static com.wavjaby.Main.pool;

public class NCKUHub implements HttpHandler {

    private JsonObject nckuHubCourseID;
    private final long courseIDUpdateInterval = 5 * 60 * 1000;
    private long lastCourseIDUpdateTime;

    public NCKUHub() {
        updateNckuHubCourseID();
    }

    private void updateNckuHubCourseID() {
        try {
            System.out.println("[NCKU Hub] Update course id");
            Connection.Response nckuhubCourse = HttpConnection.connect("https://nckuhub.com/course/")
                    .ignoreContentType(true)
                    .execute();
            String body = nckuhubCourse.body();
            JsonObject nckuhubResponse = new JsonObject(body);
            JsonArray courseData = nckuhubResponse.getArray("courses");
            nckuHubCourseID = new JsonObject();
            for (Object i : courseData) {
                JsonObject each = (JsonObject) i;
                String deptID = each.getString("系號");
                JsonObject dept = nckuHubCourseID.getJson(deptID);
                if (dept == null)
                    nckuHubCourseID.put(deptID, dept = new JsonObject());
                dept.put(
                        each.getString("選課序號"),
                        each.getInt("id")
                );
            }
            lastCourseIDUpdateTime = System.currentTimeMillis();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[NCKU Hub] Update course id failed");
        }
    }

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
                boolean success = false;

                if (System.currentTimeMillis() - lastCourseIDUpdateTime > courseIDUpdateInterval)
                    updateNckuHubCourseID();
                data.append("data", nckuHubCourseID.toString(), true);
                success = true;


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
            System.out.println("[NCKUhub] Get nckuhub " + (System.currentTimeMillis() - startTime) + "ms");
        });
    }
}
