package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonException;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.*;

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
        String loginState = getDefaultCookie(requestHeaders, cookieStore);

        try {
            ApiResponse apiResponse = new ApiResponse();
            String method = req.getRequestMethod();
            if (method.equalsIgnoreCase("GET"))
                getPreCourseSchedule(cookieStore, apiResponse);
            else if (method.equalsIgnoreCase("POST"))
                postPreCourseSchedule(readRequestBody(req), cookieStore, apiResponse);
            else
                apiResponse.errorUnsupportedHttpMethod(method);


            Headers responseHeader = req.getResponseHeaders();
            packCourseLoginStateCookie(responseHeader, loginState, cookieStore);
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
        logger.log("Get template " + (System.currentTimeMillis() - startTime) + "ms");
    };

    private void getPreCourseSchedule(CookieStore cookieStore, ApiResponse response) {
        Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos31315")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .followRedirects(false)
                .proxy(proxyManager.getProxy());
        Element body = checkCourseNckuLoginRequiredPage(conn, response);
        if (body == null)
            return;

        // get table
        JsonArray courseScheduleData = new JsonArray();
        Elements tables = body.getElementsByTag("table");
        if (tables.isEmpty()) {
            response.errorParse("Table not found");
            return;
        }
        Elements tbody = tables.get(0).getElementsByTag("tbody");
        if (tbody.isEmpty()) {
            response.errorParse("Table body not found");
            return;
        }

        Elements eachCourse = tbody.get(0).getElementsByTag("tr");
        for (Element row : eachCourse) {
            Elements col = row.children();
            if (col.size() < 7) {
                if (!col.isEmpty() && "7".equals(col.get(0).attr("colspan")))
                    continue;
                response.errorParse("Course info row parse error");
                return;
            }

            JsonObject course = new JsonObject();
            course.put("deptID", col.get(0).text());
            course.put("sn", col.get(1).text());
            course.put("name", col.get(2).text());
            course.put("credits", Float.parseFloat(col.get(3).text()));

            // Parse time
            JsonArray timeArr = new JsonArray();
            for (Node i : col.get(4).childNodes()) {
                if (!(i instanceof TextNode))
                    continue;
                String time = ((TextNode) i).text().trim();
                // Parse day of week
                int dayEnd = time.indexOf(' ');
                String day = dayEnd == -1 ? time : time.substring(0, dayEnd);
                Integer date = CourseSchedule.dayOfWeekTextToInt.get(day);
                if (date == null) {
                    response.addWarn("Course info time parse error, unknown date: " + day);
                    continue;
                }

                StringBuilder builder = new StringBuilder();
                // Parse section
                if (dayEnd != -1 && time.length() > dayEnd + 1) {
                    builder.append(date).append(',');
                    int timeSplit = time.indexOf('~');
                    if (timeSplit == -1)
                        builder.append(sectionCharToByte(time.charAt(dayEnd + 1)));
                    else
                        builder.append(sectionCharToByte(time.charAt(dayEnd + 1))).append(',')
                                .append(sectionCharToByte(time.charAt(timeSplit + 1)));
                } else
                    builder.append(date);

                timeArr.add(new JsonObjectStringBuilder().append("time", builder.toString()));
            }
            course.put("info", timeArr);

            course.put("addTime", col.get(5).text());
            course.put("remark", col.get(6).text());

            if (col.size() == 8) {
                Element delBtn = col.get(7).firstElementChild();
                course.put("delete", delBtn == null ? null : delBtn.attr("data-info"));
            }
            courseScheduleData.add(course);
        }

        JsonObjectStringBuilder builder = new JsonObjectStringBuilder();
        builder.append("schedule", courseScheduleData);
        response.setData(builder.toString());
    }

    private void postPreCourseSchedule(String postData, CookieStore cookieStore, ApiResponse response) {
        Map<String, String> form = parseUrlEncodedForm(postData);
        String action = form.get("action");
        String info = form.get("info");
        if (action == null || action.length() == 0) {
            response.errorBadPayload("Payload form require \"action\"");
            return;
        }
        if (action.equals("delete")) {
            if (info == null || info.length() == 0) {
                response.errorBadPayload("Action delete require \"info\"");
                return;
            }
        } else if (!action.equals("reset")) {
            response.errorBadPayload("Unknown action \"" + action + "\"");
            return;
        }

        String postPayload;
        try {
            postPayload = info == null
                    ? "action=" + action
                    : "action=" + action + "&info=" + URLEncoder.encode(info, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.errTrace(e);
            response.errorParse("Unsupported encoding");
            return;
        }

        Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos31315&m=" + action)
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .proxy(proxyManager.getProxy())
                .ignoreContentType(true)
                .method(Connection.Method.POST)
                .requestBody(postPayload);

        try {
            JsonObject postResult = new JsonObject(conn.execute().body());
            String msg = postResult.getString("msg");
            if (!postResult.containsKey("result") || !postResult.getBoolean("result"))
                response.errorCourseNCKU();
            response.setMessageDisplay(msg);
        } catch (JsonException e) {
            logger.errTrace(e);
            if (response != null)
                response.errorParse("Response Json parse error: " + e.getMessage());
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
