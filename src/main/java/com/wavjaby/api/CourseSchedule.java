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
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.checkCourseNckuLoginRequiredPage;
import static com.wavjaby.lib.Lib.setAllowOrigin;

public class CourseSchedule implements EndpointModule {
    private static final String TAG = "[Schedule]";
    private static final Logger logger = new Logger(TAG);
    public static final HashMap<String, Integer> dayOfWeekTextToInt = new HashMap<String, Integer>() {{
        put("時間未定", -1);
        put("Undecided", -1);
        put("星期一", 0);
        put("Monday", 0);
        put("星期二", 1);
        put("Tuesday", 1);
        put("星期三", 2);
        put("Wednesday", 2);
        put("星期四", 3);
        put("Thursday", 3);
        put("星期五", 4);
        put("Friday", 4);
        put("星期六", 5);
        put("Saturday", 5);
        put("星期日", 6);
        put("Sunday", 6);
    }};
    private final ProxyManager proxyManager;

    public CourseSchedule(ProxyManager proxyManager) {
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
            getCourseSchedule(cookieStore, apiResponse);

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
        logger.log("Get schedule " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private void getCourseSchedule(CookieStore cookieStore, ApiResponse response) {
        Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21215")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .followRedirects(false)
                .proxy(proxyManager.getProxy());
        Element body = checkCourseNckuLoginRequiredPage(conn, response);
        if (body == null)
            return;

        // Get year, semester
        int year = -1, semester = -1;
        Element title = body.getElementsByClass("apName").first();
        if (title != null) {
            String result = title.text();
            int start = 0, end;
            char c;
            while ((c = result.charAt(start)) < '0' || c > '9') start++;
            end = start;
            while ((c = result.charAt(end)) >= '0' && c <= '9') end++;
            year = Integer.parseInt(result.substring(start, end));

            start = end;
            while ((c = result.charAt(start)) < '0' || c > '9') start++;
            end = start;
            while ((c = result.charAt(end)) >= '0' && c <= '9') end++;
            semester = Integer.parseInt(result.substring(start, end));
        } else {
            response.addWarn("Year and Semester not found");
        }

        // Get student ID
        Element userIdEle = body.getElementById("current_time");
        List<TextNode> textNodes;
        if (userIdEle == null ||
                userIdEle.childNodeSize() != 3 ||
                (textNodes = userIdEle.textNodes()).size() != 2) {
            response.errorParse("Student ID not found");
            return;
        }
        String studentID = textNodes.get(1).toString();
        int idStart = studentID.lastIndexOf(';');
        if (idStart != -1) studentID = studentID.substring(idStart + 1);


        // Get credits
        Element creditsEle = userIdEle.parent();
        if (creditsEle == null || creditsEle.childrenSize() != 2) {
            response.errorParse("Credits not found");
            return;
        }
        String creditsStr = creditsEle.child(1).text();
        int creditsStart = -1, creditsEnd = -1;
        char[] chars = creditsStr.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (creditsStart == -1) {
                if (chars[i] >= '0' && chars[i] <= '9')
                    creditsStart = i;
            } else if ((chars[i] < '0' || chars[i] > '9') && chars[i] != '.') {
                creditsEnd = i;
                break;
            }
        }
        if (creditsStart == -1) {
            response.errorParse("Credits parse error");
            return;
        }
        float credits = Float.parseFloat(creditsEnd == -1 ? creditsStr.substring(creditsStart) : creditsStr.substring(creditsStart, creditsEnd));

        // get table
        JsonArray courseScheduleData = new JsonArray();
        Elements tables = body.getElementsByTag("table");
        if (tables.size() == 0) {
            response.errorParse("Table not found");
            return;
        }
        Elements tbody = tables.get(0).getElementsByTag("tbody");
        if (tbody.size() == 0) {
            response.errorParse("Table body not found");
            return;
        }
        Elements eachCourse = tbody.get(0).getElementsByTag("tr");
        JsonArray courseInfo = null;
        String lastSN = null;

        for (Element row : eachCourse) {
            if (row.childNodeSize() < 10) {
                response.errorParse("Course info not found");
                return;
            }
            Elements rowElements = row.children();

            String sn = rowElements.get(0).text();
            if (!sn.equals(lastSN)) {
                lastSN = sn;
                JsonObject course = new JsonObject();
                course.put("deptID", rowElements.get(1).text());
                course.put("sn", rowElements.get(2).text());
                course.put("name", rowElements.get(3).text());
                String requiredStr = rowElements.get(4).text();
                course.put("required", requiredStr.equals("必修") || requiredStr.equals("REQUIRED"));
                course.put("credits", Float.parseFloat(rowElements.get(5).text()));
                course.put("info", courseInfo = new JsonArray());
                courseScheduleData.add(course);
            }

            JsonObject info = new JsonObject();
            info.put("type", rowElements.get(6).text());

            // parse time
            Element timeElement = rowElements.get(7);
            // Time in link
            StringBuilder builder = new StringBuilder();
            Element timeElementChild = timeElement.firstElementChild();
            if (timeElementChild != null && timeElementChild.tagName().equals("div")) {
                builder.append("-1");
            }
            // Parse time text
            else {
                String time = timeElement.text();
                int dayEnd = time.indexOf(' ');
                String day = dayEnd == -1 ? time : time.substring(0, dayEnd);
                Integer date = dayOfWeekTextToInt.get(day);
                if (date == null) {
                    response.addWarn(TAG + "Course Time parse error, unknown date: " + day);
                    continue;
                }
                if (dayEnd != -1) {
                    builder.append(date).append(',');
                    int timeSplit = time.indexOf('~');
                    if (timeSplit == -1)
                        builder.append(time, dayEnd + 1, time.length());
                    else
                        builder.append(time, dayEnd + 1, timeSplit).append(',')
                                .append(time, timeSplit + 1, time.length());
                } else
                    builder.append(date);
            }
            info.put("time", builder.toString());
            String room = rowElements.get(8).text();
            int roomIdEnd = room.indexOf(' ');
            if (room.length() > 0 && roomIdEnd == -1) {
                response.errorParse("Course room id not found: " + room);
                return;
            }
            String roomID = roomIdEnd == -1 ? null : room.substring(0, roomIdEnd);
            if (roomIdEnd != -1)
                room = room.substring(
                        room.charAt(roomIdEnd + 1) == '(' ? roomIdEnd + 2 : roomIdEnd,
                        room.charAt(room.length() - 1) == ')' ? room.length() - 1 : room.length());

            info.put("roomID", roomID);
            info.put("room", room);

            courseInfo.add(info);
        }

        JsonObjectStringBuilder builder = new JsonObjectStringBuilder();
        builder.append("year", year);
        builder.append("semester", semester);
        builder.append("studentId", studentID);
        builder.append("credits", credits);
        builder.append("schedule", courseScheduleData);
        response.setData(builder.toString());
    }
}
