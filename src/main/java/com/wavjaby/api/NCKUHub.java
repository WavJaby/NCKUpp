package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Lib.parseUrlEncodedForm;
import static com.wavjaby.lib.Lib.setAllowOrigin;

public class NCKUHub implements EndpointModule {
    private static final String TAG = "[NCKU Hub]";
    private static final Logger logger = new Logger(TAG);

    private String nckuHubCourseIdJson;
    private final long courseIDUpdateInterval = 10 * 60 * 1000;
    private long lastCourseIDUpdateTime;
    private static final int maxCacheSize = 20 * 1000 * 1000;
    private static final int maxCacheTime = 10 * 60 * 1000;
    private static final int cacheCleanerInterval = 30 * 1000;
    private int lastCacheSize = 0;
    private int cacheSize = 0;
    private final Map<Integer, NckuHubCourseData> courseInfoCache = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cacheCleaner = Executors.newSingleThreadScheduledExecutor();

    private static class NckuHubCourseData {
        int id;
        int size;
        long lastUpdate;
        String data;

        NckuHubCourseData(String data, int id) {
            this.data = data;
            this.id = id;
            size = data.getBytes(StandardCharsets.UTF_8).length;
            lastUpdate = System.currentTimeMillis();
        }

        void updateData(String data) {
            this.data = data;
            size = data.getBytes(StandardCharsets.UTF_8).length;
            lastUpdate = System.currentTimeMillis();
        }

        static int compare(NckuHubCourseData a, NckuHubCourseData b) {
            return (int) (a.lastUpdate - b.lastUpdate);
        }
    }

    @Override
    public void start() {
        updateNckuHubCourseID();

        cacheCleaner.scheduleWithFixedDelay(() -> {
            if (cacheSize > maxCacheSize) {
                LinkedList<NckuHubCourseData> sorted = new LinkedList<>(courseInfoCache.values());
                sorted.sort(NckuHubCourseData::compare);

                // Remove cache
                while (cacheSize > maxCacheSize && sorted.size() > 0) {
                    NckuHubCourseData i = sorted.removeFirst();
                    cacheSize -= i.size;
                    courseInfoCache.remove(i.id);
                }
            }
            if (lastCacheSize != cacheSize)
                logger.log("Cache size: " + cacheSize / 1000 + "KB");
            lastCacheSize = cacheSize;
        }, cacheCleanerInterval, cacheCleanerInterval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        cacheCleaner.shutdown();
        try {
            if (!cacheCleaner.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                logger.warn("CacheCleaner close timeout");
                cacheCleaner.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            logger.warn("CacheCleaner close error");
            cacheCleaner.shutdownNow();
        }
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
            ApiResponse apiResponse = new ApiResponse();

            String queryString = req.getRequestURI().getRawQuery();
            if (queryString == null) {
                // get courseID
                if (System.currentTimeMillis() - lastCourseIDUpdateTime > courseIDUpdateInterval)
                    if (!updateNckuHubCourseID())
                        apiResponse.addWarn("Update NCKU-HUB course id failed");
                apiResponse.setData(nckuHubCourseIdJson);
            } else {
                // get course info
                getNckuHubCourseInfo(queryString, apiResponse);
            }

            Headers responseHeader = req.getResponseHeaders();
            byte[] dataByte = apiResponse.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(apiResponse.isSuccess() ? 200 : 400, dataByte.length);
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

    private void getNckuHubCourseInfo(String queryString, ApiResponse response) {
        Map<String, String> query = parseUrlEncodedForm(queryString);
        String nckuID = query.get("id");
        if (nckuID == null) {
            response.errorBadQuery("Query require \"id\"");
            return;
        }
        String[] nckuIDs = nckuID.split(",");

        JsonArrayStringBuilder courses = new JsonArrayStringBuilder();
        long now = System.currentTimeMillis();
        for (String idStr : nckuIDs) {
            int id = Integer.parseInt(idStr);
            // Get cached data
            NckuHubCourseData cached = courseInfoCache.get(id);
            if (cached != null && now - cached.lastUpdate < maxCacheTime) {
                courses.appendRaw(cached.data);
                continue;
            }

            Connection.Response nckuhubCourse;
            try {
                // Fetch data
                nckuhubCourse = HttpConnection.connect("https://nckuhub.com/course/" + idStr)
                        .ignoreContentType(true)
                        .execute();
            } catch (IOException e) {
                logger.errTrace(e);
                response.errorNetwork(e);
                return;
            }
            String data = nckuhubCourse.body();
            courses.appendRaw(data);

            // Update cache
            if (cached != null) {
                cacheSize -= cached.size;
                cached.updateData(data);
            } else
                courseInfoCache.put(id, cached = new NckuHubCourseData(data, id));
            cacheSize += cached.size;
        }
        response.setData(courses.toString());
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
            logger.errTrace(e);
            return false;
        }
    }
}
