package com.wavjaby.api;

import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.*;

public class A9Registered implements EndpointModule {
    private static final String TAG = "[A9Registered]";
    private static final Logger logger = new Logger(TAG);
    private static final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final ZoneOffset timeZoneOffset = ZoneOffset.ofHours(8);
    private static final String CACHE_FILE_PATH = "./api_file/A9Registered.json";
    private static final Object lock = new Object();
    private final ProxyManager proxyManager;

    private File cacheFile = null;
    private long lastUpdateTime = -1;
    private String courseCountData = null;

    private static class CourseData {
        final String name;
        final int count;

        public CourseData(String name, int count) {
            this.name = name;
            this.count = count;
        }

        @Override
        public String toString() {
            return "{\"name\":\"" + name + "\",\"count\":" + count + "}";
        }
    }

    public A9Registered(ProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Override
    public void start() {
        cacheFile = getFileFromPath(CACHE_FILE_PATH, true);
        if (!cacheFile.exists()) {
            logger.err("A9Registered cache file not found");
            return;
        }

        // Read cache
        String cacheDataStr = readFileToString(cacheFile, false, StandardCharsets.UTF_8);
        if (cacheDataStr == null) {
            logger.err("A9Registered cache file read error");
            return;
        }
        JsonObject cacheData = new JsonObject(cacheDataStr);
        lastUpdateTime = cacheData.getLong("lastUpdate");
        courseCountData = cacheData.toString();
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
        CookieStore cookieStore = new CookieManager().getCookieStore();
        String loginState = getDefaultCookie(req, cookieStore);

        ApiResponse apiResponse = new ApiResponse();
        getA9Registered(cookieStore, apiResponse);

        packCourseLoginStateCookie(req, loginState, cookieStore);
        apiResponse.sendResponse(req);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
    };

    private void getA9Registered(CookieStore cookieStore, ApiResponse response) {
        Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry13225")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .followRedirects(false)
                .proxy(proxyManager.getProxy());
        Element body = checkCourseNckuLoginRequiredPage(conn, response, true);

        // Read data from cache
        if (body == null) {
            response.setData(courseCountData);
            return;
        }

        // Parse last update time
        Element updateTimeTable = body.getElementsByClass("title-table").first();
        Long parsedTimeSec = null;
        if (updateTimeTable != null) {
            Element updateTimeEle = updateTimeTable.getElementsByTag("p").first();
            if (updateTimeEle != null) {
                String updateTime = updateTimeEle.text();
                updateTime = updateTime.substring(updateTime.lastIndexOf(": ") + 1).trim();
                try {
                    parsedTimeSec = LocalDateTime.parse(updateTime, timeFormat).toEpochSecond(timeZoneOffset) * 1000;
                } catch (DateTimeParseException e) {
                    logger.errTrace(e);
                }
            }
        }

        synchronized (lock) {
            // Read data from cache
            if (parsedTimeSec == null || parsedTimeSec == lastUpdateTime) {
                response.setData(courseCountData);
                return;
            }

            JsonObjectStringBuilder result = new JsonObjectStringBuilder();
            // Parse table list
            Element dataTable = body.getElementsByClass("A9-table").first();
            if (dataTable == null) {
                response.errorParse("DataTable not found");
                return;
            }
            Element tbody = dataTable.lastElementChild();
            if (tbody == null) {
                response.errorParse("DataTable body not found");
                return;
            }

            JsonObjectStringBuilder registeredCountList = new JsonObjectStringBuilder();
            for (Element row : tbody.children()) {
                Elements col = row.children();
                if (col.size() < 7) {
                    response.errorParse("DataTable row parse error");
                    return;
                }
                String serial = col.get(1).ownText().trim() + '-' + col.get(2).ownText().trim();
                registeredCountList.appendRaw(serial, new CourseData(col.get(4).ownText().trim(), Integer.parseInt(col.get(6).ownText().trim())).toString());
            }
            result.append("list", registeredCountList);
            result.append("lastUpdate", lastUpdateTime = parsedTimeSec);
            response.setData(courseCountData = result.toString());

            // Update cache file
            try {
                FileWriter fileWriter = new FileWriter(cacheFile);
                fileWriter.write(courseCountData);
                fileWriter.close();
            } catch (IOException e) {
                logger.errTrace(e);
            }
        }
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}