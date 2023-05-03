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
import static com.wavjaby.lib.Lib.getOriginUrl;
import static com.wavjaby.lib.Lib.setAllowOrigin;

public class CourseSchedule implements EndpointModule {
    private static final String TAG = "[Schedule]";
    private static final Logger logger = new Logger(TAG);
    private static final HashMap<String, Integer> DayTextToInt = new HashMap<String, Integer>() {{
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
        String originUrl = getOriginUrl(requestHeaders);
        String loginState = getDefaultCookie(requestHeaders, cookieStore);

        try {
            ApiResponse data = new ApiResponse();
            boolean success = getCourseSchedule(cookieStore, data);

            Headers responseHeader = req.getResponseHeaders();
            packCourseLoginStateCookie(responseHeader, loginState, originUrl, cookieStore);

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
        logger.log("Get schedule " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private boolean getCourseSchedule(CookieStore cookieStore, ApiResponse response) {
        Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21215")
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
            return false;
        }


        // Get year, semester
        Elements pagePath = root.getElementsByClass("breadcrumb");
        if (pagePath.size() == 0) {
            response.addWarn(TAG + "Year and Semester not found");
        } else {
            pagePath = pagePath.first().getElementsByTag("a");
            if (pagePath.size() < 2) {
                response.addWarn(TAG + "Year and Semester not found");
            }
        }
        String pageName = pagePath.get(1).text();
        int year = -1, semester = -1;
        int start = 0, end;
        char c;
        while (start < pageName.length() && ((c = pageName.charAt(start)) < '0' || c > '9')) start++;
        end = start;
        if (start < pageName.length()) {
            while (end < pageName.length() && ((c = pageName.charAt(end)) >= '0' && c <= '9')) end++;
            year = Integer.parseInt(pageName.substring(start, end));
        }

        start = end;
        while (start < pageName.length() && ((c = pageName.charAt(start)) < '0' || c > '9')) start++;
        end = start;
        if (start < pageName.length()) {
            while (end < pageName.length() && ((c = pageName.charAt(end)) >= '0' && c <= '9')) end++;
            semester = Integer.parseInt(pageName.substring(start, end));
        }

        // Get student ID
        Element userIdEle = root.getElementById("current_time");
        List<TextNode> textNodes;
        if (userIdEle == null ||
                userIdEle.childNodeSize() != 3 ||
                (textNodes = userIdEle.textNodes()).size() != 2) {
            response.addError(TAG + "Student ID not found");
            return false;
        }
        String studentID = textNodes.get(1).toString();
        int idStart = studentID.lastIndexOf(';');
        if (idStart != -1) studentID = studentID.substring(idStart + 1);


        // Get credits
        Element creditsEle = userIdEle.parent();
        if (creditsEle == null || creditsEle.childrenSize() != 2) {
            response.addError(TAG + "Credits not found");
            return false;
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
            response.addError(TAG + "Credits parse error");
            return false;
        }
        float credits = Float.parseFloat(creditsEnd == -1 ? creditsStr.substring(creditsStart) : creditsStr.substring(creditsStart, creditsEnd));

        // get table
        JsonArray courseScheduleData = new JsonArray();
        Elements tables = root.getElementsByTag("table");
        if (tables.size() == 0) {
            response.addError(TAG + "Table not found");
            return false;
        }
        Elements tbody = tables.get(0).getElementsByTag("tbody");
        if (tbody.size() == 0) {
            response.addError(TAG + "Table body not found");
            return false;
        }
        Elements eachCourse = tbody.get(0).getElementsByTag("tr");
        JsonArray courseInfo = null;
        String lastSN = null;

        for (Element row : eachCourse) {
            if (row.childNodeSize() < 10) {
                response.addError(TAG + "Course info not found");
                return false;
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
            String type = rowElements.get(6).text();
            switch (type) {
                case "講義":
                case "Lecture":
                    type = "lecture";
                    break;
                case "實習":
                case "Lab":
                    type = "lab";
                    break;
            }
            info.put("type", type);

            // parse time
            String time = rowElements.get(7).text();
            int dayEnd = time.indexOf(' ');
            String day = dayEnd == -1 ? time : time.substring(0, dayEnd);
            Integer date = DayTextToInt.get(day);
            if (date == null) {
                response.addWarn(TAG + "Course Time parse error, unknown date: " + day);
                continue;
            }
            StringBuilder builder = new StringBuilder();
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
            info.put("time", builder.toString());
            String room = rowElements.get(8).text();
            int roomIdEnd = room.indexOf(' ');
            if (room.length() > 0 && roomIdEnd == -1) {
                response.addError(TAG + "Course room id not found: " + room);
                return false;
            }
            String roomID = roomIdEnd == -1 ? null : room.substring(0, roomIdEnd);
            if (roomIdEnd != -1)
                room = room.substring(
                        room.charAt(roomIdEnd + 1) == '(' ? roomIdEnd + 2 : roomIdEnd,
                        room.charAt(room.length() - 1) == ')' ? room.length() - 1 : room.length());

            info.put("roomID", roomID);
            info.put("room", room);
            info.put("time", builder.toString());

            courseInfo.add(info);
        }

        JsonObjectStringBuilder builder = new JsonObjectStringBuilder();
        builder.append("year", year);
        builder.append("semester", semester);
        builder.append("studentId", studentID);
        builder.append("credits", credits);
        builder.append("schedule", courseScheduleData);
        response.setData(builder.toString());
        return true;
    }
}
