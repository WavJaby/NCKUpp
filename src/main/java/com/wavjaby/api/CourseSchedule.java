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

import static com.wavjaby.Cookie.*;
import static com.wavjaby.Lib.getRefererUrl;
import static com.wavjaby.Lib.setAllowOrigin;
import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.Main.pool;

public class CourseSchedule implements HttpHandler {
    private static final String TAG = "[Schedule] ";
    private static final HashMap<String, Integer> DayTextToInt = new HashMap<String, Integer>() {{
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

    @Override
    public void handle(HttpExchange req) {
        pool.submit(() -> {
            long startTime = System.currentTimeMillis();
            CookieManager cookieManager = new CookieManager();
            CookieStore cookieStore = cookieManager.getCookieStore();
            Headers requestHeaders = req.getRequestHeaders();
            String refererUrl = getRefererUrl(requestHeaders);

            try {
                // unpack cookie
                String loginState = getDefaultCookie(requestHeaders, cookieManager);

                JsonBuilder data = new JsonBuilder();
                boolean success = getCourseSchedule(cookieStore, data);

                Headers responseHeader = req.getResponseHeaders();
                packLoginStateCookie(responseHeader, loginState, refererUrl, cookieStore);

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
            Logger.log(TAG, "Get schedule " + (System.currentTimeMillis() - startTime) + "ms");
        });
    }

    private boolean getCourseSchedule(CookieStore cookieStore, JsonBuilder data) {
        Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21215")
                .cookieStore(cookieStore);
        Document root = null;
        try {
            root = conn.get();
        } catch (IOException ignore) {
        }
        if (root == null) {
            data.append("err", TAG + "Can not fetch schedule");
            return false;
        }


        // get student ID
        Element userIdEle = root.getElementById("current_time");
        List<TextNode> textNodes;
        if (userIdEle == null ||
                userIdEle.childNodeSize() != 3 ||
                (textNodes = userIdEle.textNodes()).size() != 2) {
            data.append("err", TAG + "Student ID not found");
            return false;
        }
        String studentID = textNodes.get(1).toString();
        int idStart = studentID.lastIndexOf(';');
        if (idStart != -1) studentID = studentID.substring(idStart + 1);


        // get student credits
        Element creditsEle = userIdEle.parent();
        if (creditsEle == null || creditsEle.childrenSize() != 2) {
            data.append("err", TAG + "Credits not found");
            return false;
        }
        String credits = creditsEle.child(1).text();
        int start = -1, end = -1;
        char[] chars = credits.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (start == -1) {
                if (chars[i] >= '0' && chars[i] <= '9')
                    start = i;
            } else if (chars[i] < '0' || chars[i] > '9') {
                end = i;
                break;
            }
        }
        if (start == -1) {
            data.append("err", TAG + "Credits parse error");
            return false;
        }
        credits = end == -1 ? credits.substring(start) : credits.substring(start, end);


        // get table
        JsonArray courseScheduleData = new JsonArray();
        Elements tables = root.getElementsByTag("table");
        if (tables.size() == 0) {
            data.append("err", TAG + "Table not found");
            return false;
        }
        Elements tbody = tables.get(0).getElementsByTag("tbody");
        if (tbody.size() == 0) {
            data.append("err", TAG + "Table body not found");
            return false;
        }
        Elements eachCourse = tbody.get(0).getElementsByTag("tr");
        JsonArray courseInfo = null;
        String lastSN = null;

        for (Element row : eachCourse) {
            if (row.childNodeSize() < 10) {
                data.append("err", TAG + "Course info not found");
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
            Integer date = DayTextToInt.get(dayEnd == -1 ? time : time.substring(0, dayEnd));
            if (date == null) {
                data.append("err", TAG + "Course Time parse error, unknown date: " + time.substring(0, dayEnd));
                return false;
            }
            StringBuilder builder = new StringBuilder();
            if (dayEnd != -1)
                builder.append(date).append(',');
            int timeSplit = time.indexOf('~');
            if (timeSplit == -1)
                builder.append(time, dayEnd + 1, time.length());
            else
                builder.append(time, dayEnd + 1, timeSplit).append(',')
                        .append(time, timeSplit + 1, time.length());
            info.put("time", builder.toString());
            String room = rowElements.get(8).text();
            int roomIdEnd = room.indexOf(' ');
            if (room.length() > 0 && roomIdEnd == -1) {
                data.append("err", TAG + "Course room id not found: " + room);
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

//        for (int i = 1; i < 18; i++) {
//            Elements elements = eachCourse.get(i).getElementsByTag("td");
//            if (elements.size() == 0) {
//                data.append("err", TAG + "Course info not found");
//                return false;
//            }
//            List<TextNode> courseDataText = elements.get(0).textNodes();
//
//            JsonArray courseData = new JsonArray();
//            array.add(courseData);
//            if (courseDataText.size() > 0) {
//                String courseName = courseDataText.get(0).text();
//                int courseIdEnd = courseName.indexOf("】");
//                if (courseIdEnd == -1) {
//                    data.append("err", TAG + "Course name parse error");
//                    return false;
//                }
//                courseData.add(courseName.substring(1, courseIdEnd));
//                courseData.add(courseName.substring(courseIdEnd + 1));
//                if (courseDataText.size() > 1) {
//                    String locationText = courseDataText.get(1).text();
//                    int locationStart = locationText.indexOf("：");
//                    if (locationStart == -1) {
//                        data.append("err", TAG + "Course location parse error");
//                        return false;
//                    }
//                    courseData.add(locationText.substring(locationStart + 1, locationText.length() - 1));
//                } else
//                    courseData.add("");
//            }
//        }
//    }
        data.append("id", studentID);
        data.append("credits", credits);
        data.append("schedule", courseScheduleData.toString(), true);
        return true;
    }
}
