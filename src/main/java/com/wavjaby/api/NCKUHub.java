package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Lib.parseUrlEncodedForm;
import static com.wavjaby.lib.Lib.setAllowOrigin;

public class NCKUHub implements EndpointModule {
    private static final String TAG = "[NCKU Hub]";
    private static final Logger logger = new Logger(TAG);

    private String nckuHubCourseIdJson;
    private final long courseIDUpdateInterval = 10 * 60 * 1000;
    private long lastCourseIDUpdateTime;

    @Override
    public void start() {
        updateNckuHubCourseID();
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
        Headers requestHeaders = req.getRequestHeaders();
        getDefaultCookie(requestHeaders, cookieManager.getCookieStore());

        try {
            JsonObjectStringBuilder data = new JsonObjectStringBuilder();

            String queryString = req.getRequestURI().getRawQuery();
            boolean success = true;
            if (queryString == null) {
                // get courseID
                if (System.currentTimeMillis() - lastCourseIDUpdateTime > courseIDUpdateInterval)
                    success = updateNckuHubCourseID();
                if (success)
                    data.appendRaw("data", nckuHubCourseIdJson);
                else
                    data.append("err", TAG + "Update course id failed");
            } else {
                // get course info
                success = getNckuHubCourseInfo(queryString, data);
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
        }
        logger.log("Get NCKU Hub " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private boolean getNckuHubCourseInfo(String queryString, JsonObjectStringBuilder outData) {
        Map<String, String> query = parseUrlEncodedForm(queryString);
        String nckuID = query.get("id");
        if (nckuID == null) {
            outData.append("err", TAG + "Query id not found");
            return false;
        }
        String[] nckuIDs = nckuID.split(",");

        try {
            JsonArrayStringBuilder courses = new JsonArrayStringBuilder();
            for (String id : nckuIDs) {
                Connection.Response nckuhubCourse = HttpConnection.connect("https://nckuhub.com/course/" + id)
                        .ignoreContentType(true)
                        .execute();
                courses.appendRaw(nckuhubCourse.body());
            }
            outData.append("data", courses);
        } catch (IOException e) {
            outData.append("err", TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
            return false;
        }

        return true;
    }

    private boolean updateNckuHubCourseID() {
        try {
            logger.log("Updating course id");
            Connection.Response nckuhubCourse = HttpConnection.connect("https://nckuhub.com/course/")
                    .ignoreContentType(true)
                    .execute();
            String body = nckuhubCourse.body();
            JsonObject nckuhubResponse = new JsonObject(body);
            JsonArray courseData = nckuhubResponse.getArray("courses");
            JsonObject ids = new JsonObject();
            for (Object i : courseData) {
                JsonObject each = (JsonObject) i;
                String deptID = each.getString("系號");
                JsonObject dept = ids.getJson(deptID);
                if (dept == null)
                    ids.put(deptID, dept = new JsonObject());
                dept.put(
                        each.getString("選課序號"),
                        each.getInt("id")
                );
            }
            nckuHubCourseIdJson = ids.toString();
            lastCourseIDUpdateTime = System.currentTimeMillis();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            logger.err("Update course id failed");
            return false;
        }
    }
}
