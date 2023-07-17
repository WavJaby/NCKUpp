package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.getOriginUrl;
import static com.wavjaby.lib.Lib.setAllowOrigin;

public class PreCourseSchedule implements EndpointModule {
    private static final String TAG = "[PreCourseSchedule]";
    private static final Logger logger = new Logger(TAG);
    private final ProxyManager proxyManager;

    public PreCourseSchedule(ProxyManager proxyManager) {
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
        String originUrl = getOriginUrl(requestHeaders);
        String loginState = getDefaultCookie(requestHeaders, cookieStore);

        try {
            ApiResponse apiResponse = new ApiResponse();
            getPreCourseSchedule(cookieStore, apiResponse);


            Headers responseHeader = req.getResponseHeaders();
            packCourseLoginStateCookie(responseHeader, loginState, originUrl, cookieStore);
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
            e.printStackTrace();
        }
        logger.log("Get template " + (System.currentTimeMillis() - startTime) + "ms");
    };

    private void getPreCourseSchedule(CookieStore cookieStore, ApiResponse response) {
        Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos31315")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .proxy(proxyManager.getProxy());
        Document root = null;
        try {
            root = conn.get();
        } catch (IOException ignore) {
        }
        if (root == null) {
            response.addError(TAG + "Can not fetch schedule");
            return;
        }

        // get table
        JsonArray courseScheduleData = new JsonArray();
        Elements tables = root.getElementsByTag("table");
        if (tables.size() == 0) {
            response.addError(TAG + "Table not found");
            return;
        }
        Elements tbody = tables.get(0).getElementsByTag("tbody");
        if (tbody.size() == 0) {
            response.addError(TAG + "Table body not found");
            return;
        }

        Elements eachCourse = tbody.get(0).getElementsByTag("tr");
        for (Element row : eachCourse) {
            Elements rowElements = row.children();
            if (rowElements.size() < 8) {
                continue;
            }

            JsonObject course = new JsonObject();
            course.put("deptID", rowElements.get(0).text());
            course.put("sn", rowElements.get(1).text());
            course.put("name", rowElements.get(2).text());
            course.put("credits", Float.parseFloat(rowElements.get(3).text()));

            // Parse time
            JsonArray timeArr = new JsonArray();
            for (Node i : rowElements.get(4).childNodes()) {
                if (!(i instanceof TextNode))
                    continue;
                String time = ((TextNode) i).text().trim();
                // Parse day of week
                int dayEnd = time.indexOf(' ');
                String day = dayEnd == -1 ? time : time.substring(0, dayEnd);
                Integer date = CourseSchedule.DayTextToInt.get(day);
                if (date == null) {
                    response.addWarn(TAG + "Course Time parse error, unknown date: " + day);
                    continue;
                }

                StringBuilder builder = new StringBuilder();
                // Parse section
                if (dayEnd != -1) {
                    builder.append(date).append(',');
                    int timeSplit = time.indexOf('~', dayEnd + 1);
                    if (timeSplit == -1)
                        builder.append(time, dayEnd + 1, time.length());
                    else
                        builder.append(time, dayEnd + 1, timeSplit).append(',')
                                .append(time, timeSplit + 1, time.length());
                } else
                    builder.append(date);
                timeArr.add(builder.toString());
            }

            course.put("time", timeArr);
            course.put("addTime", rowElements.get(5).text());
            course.put("info", rowElements.get(6).text());
//            logger.log(row);

            courseScheduleData.add(course);
        }

        JsonObjectStringBuilder builder = new JsonObjectStringBuilder();
        builder.append("schedule", courseScheduleData);
        response.setData(builder.toString());
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
