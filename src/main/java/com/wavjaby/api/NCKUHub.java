package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.ThreadFactory;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.*;

import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Lib.*;

public class NCKUHub implements EndpointModule {
    private static final String TAG = "[NCKU Hub]";
    private static final Logger logger = new Logger(TAG);

    private final Map<String, Map<String, Integer>> nckuHubCourseIdMap = new HashMap<>();
    private String availableSerialId;
    private final long courseIDUpdateInterval = 10 * 60 * 1000;
    private static final int maxCacheSize = 20 * 1000 * 1000;
    private static final int maxCacheTime = 10 * 60 * 1000;
    private static final int cacheCleanerInterval = 30 * 1000;
    private long lastCourseIDUpdateTime;
    private int lastCacheSize = 0;
    private int cacheSize = 0;
    private final Map<Integer, NckuHubCourseData> courseInfoCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cacheCleaner = Executors.newSingleThreadScheduledExecutor();
    private final ThreadPoolExecutor courseInfoGetter = (ThreadPoolExecutor) Executors.newFixedThreadPool(8, new ThreadFactory("Ncku-Hub-"));
    private final Semaphore courseInfoGetterLock = new Semaphore(courseInfoGetter.getCorePoolSize(), true);

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
                while (cacheSize > maxCacheSize && !sorted.isEmpty()) {
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
        executorShutdown(courseInfoGetter, 1000, "NckuHubCourseGetter");
        executorShutdown(cacheCleaner, 1000, "NckuHubCacheCleaner");
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
                apiResponse.setData(availableSerialId);
            } else {
                // get course info
                getNckuHubCourseInfo(queryString, apiResponse);
            }

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
        logger.log("Get NCKU Hub " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private void getNckuHubCourseInfo(String queryString, ApiResponse response) {
        Map<String, String> query = parseUrlEncodedForm(queryString);
        String serialIdsStr = query.get("id");
        if (serialIdsStr == null) {
            response.errorBadQuery("Query require \"id\"");
            return;
        }
        String[] serialIds = serialIdsStr.split(",");
        CountDownLatch taskLeft = new CountDownLatch(serialIds.length);

        JsonObjectStringBuilder courses = new JsonObjectStringBuilder();
        long now = System.currentTimeMillis();
        for (String serialId : serialIds) {
            // Convert serial id to nckuhub id
            int split = serialId.indexOf('-');
            Map<String, Integer> a = nckuHubCourseIdMap.get(serialId.substring(0, split));
            final Integer nckuhubId = a == null ? null : a.get(serialId.substring(split + 1));
            if (nckuhubId == null) {
                courses.appendRaw(serialId, null);
                taskLeft.countDown();
                continue;
            }

            // Try get cached data
            final NckuHubCourseData cached = courseInfoCache.get(nckuhubId);
            if (cached != null && now - cached.lastUpdate < maxCacheTime) {
                courses.appendRaw(serialId, cached.data);
                taskLeft.countDown();
                continue;
            }

            // Fetch new data
            try {
                courseInfoGetterLock.acquire();
            } catch (InterruptedException e) {
                logger.errTrace(e);
            }
            courseInfoGetter.execute(() -> {
                // No cache, fetch data
                Connection.Response nckuhubCourse;
                try {
                    // Fetch data
                    nckuhubCourse = HttpConnection.connect("https://nckuhub.com/course/" + nckuhubId)
                            .header("Connection", "keep-alive")
                            .ignoreContentType(true)
                            .execute();
                } catch (IOException e) {
                    logger.errTrace(e);
                    response.errorNetwork(e);
                    courseInfoGetterLock.release();
                    taskLeft.countDown();
                    return;
                }
                // Parse data
                JsonObject json = new JsonObject(nckuhubCourse.body());
                for (Object i : json.getArray("rates")) {
                    JsonObject o = (JsonObject) i;
                    // NckuHub typo
                    o.put("recommend", o.getInt("recommand"));
                    o.remove("recommand");
                }
                json.remove("courseInfo");
                String resultData = json.toString();
                courses.appendRaw(serialId, resultData);
                taskLeft.countDown();

                // Update cache
                synchronized (courseInfoCache) {
                    if (cached != null) {
                        cacheSize -= cached.size;
                        cached.updateData(resultData);
                        cacheSize += cached.size;
                    } else {
                        NckuHubCourseData newCache = new NckuHubCourseData(resultData, nckuhubId);
                        cacheSize += newCache.size;
                        courseInfoCache.put(nckuhubId, newCache);
                    }
                }
                courseInfoGetterLock.release();
            });
        }

        try {
            taskLeft.await();
        } catch (InterruptedException e) {
            logger.errTrace(e);
        }
        response.setData(courses.toString());
    }

    private boolean updateNckuHubCourseID() {
        logger.log("Updating course id");
        Connection.Response nckuhubCourse;
        try {
            nckuhubCourse = HttpConnection.connect("https://nckuhub.com/course/")
                    .ignoreContentType(true)
                    .execute();
        } catch (IOException e) {
            logger.errTrace(e);
            return false;
        }
        JsonObject nckuhubResponse = new JsonObject(nckuhubCourse.body());
        JsonArray courseData = nckuhubResponse.getArray("courses");
        nckuHubCourseIdMap.clear();
        HashSet<String> available = new HashSet<>();
        for (Object i : courseData) {
            JsonObject each = (JsonObject) i;
            String deptID = each.getString("系號");
            String serialID = each.getString("選課序號");
            Map<String, Integer> dept = nckuHubCourseIdMap.computeIfAbsent(deptID, k -> new HashMap<>());
            dept.put(serialID, each.getInt("id"));
            available.add(deptID + '-' + serialID);
        }
        availableSerialId = "[\"" + String.join("\",\"", available) + "\"]";
        lastCourseIDUpdateTime = System.currentTimeMillis();
        return true;
    }
}
