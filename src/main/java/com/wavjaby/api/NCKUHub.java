package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static com.wavjaby.Cookie.getDefaultCookie;
import static com.wavjaby.Lib.parseUrlEncodedForm;
import static com.wavjaby.Lib.setAllowOrigin;
import static com.wavjaby.Main.pool;

public class NCKUHub implements HttpHandler {
    private static final String TAG = "[NCKU Hub] ";

    private JsonObject nckuHubCourseID;
    private final long courseIDUpdateInterval = 5 * 60 * 1000;
    private long lastCourseIDUpdateTime;

    public NCKUHub() {
        updateNckuHubCourseID();
    }

    @Override
    public void handle(HttpExchange req) {
        pool.submit(() -> {
            long startTime = System.currentTimeMillis();
            CookieManager cookieManager = new CookieManager();
            Headers requestHeaders = req.getRequestHeaders();
            getDefaultCookie(requestHeaders, cookieManager);


            try {
                JsonBuilder data = new JsonBuilder();

                String queryString = req.getRequestURI().getQuery();
                boolean success = true;
                if (queryString == null) {
                    // get courseID
                    if (System.currentTimeMillis() - lastCourseIDUpdateTime > courseIDUpdateInterval)
                        success = updateNckuHubCourseID();
                    if (success)
                        data.append("data", nckuHubCourseID.toString(), true);
                    else
                        data.append("data", TAG + "Update course id failed");
                } else {
                    // get course info
                    success = getNckuHubCourseInfo(queryString, data);
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
            }
            Logger.log(TAG, "Get NCKU Hub " + (System.currentTimeMillis() - startTime) + "ms");
        });
    }

    private boolean getNckuHubCourseInfo(String queryString, JsonBuilder outData) {
        Map<String, String> query = parseUrlEncodedForm(queryString);
        String nckuID = query.get("id");
        if (nckuID == null) {
            outData.append("err", TAG + "Query id not found");
            return false;
        }
        String[] nckuIDs = nckuID.split(",");

        try {
            StringBuilder builder = new StringBuilder();
            for (String id : nckuIDs) {
                Connection.Response nckuhubCourse = HttpConnection.connect("https://nckuhub.com/course/" + id)
                        .ignoreContentType(true)
                        .execute();
                builder.append(',').append(nckuhubCourse.body());
            }
            if (builder.length() > 0)
                builder.setCharAt(0, '[');
            else
                builder.append('[');
            builder.append(']');

            outData.append("data", builder.toString(), true);
        } catch (IOException e) {
            e.printStackTrace();
            outData.append("err", TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
            return false;
        }

        return true;
    }

    private boolean updateNckuHubCourseID() {
        try {
            Logger.log(TAG, "Update course id");
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
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Logger.err(TAG, "Update course id failed");
            return false;
        }
    }
}
