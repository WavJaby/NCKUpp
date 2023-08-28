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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.*;

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

    public static class SemesterInfo {
        public final int academicYear;
        public final int semester;

        private SemesterInfo(int academicYear, int semester) {
            this.academicYear = academicYear;
            this.semester = semester;
        }

        public String toJsonString() {
            return "{\"year\":" + academicYear + ",\"sem\":" + semester + "}";
        }
    }

    private static class StudentShortInfo {
        public final String studentId;
        public final String name;
        public final String infoText;

        private StudentShortInfo(String studentId, String name, String infoText) {
            this.studentId = studentId;
            this.name = name;
            this.infoText = infoText;
        }

        public String toJsonString() {
            return new JsonObjectStringBuilder()
                    .append("id", studentId)
                    .append("name", name)
                    .append("info", infoText)
                    .toString();
        }
    }

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
            Map<String, String> query = parseUrlEncodedForm(req.getRequestURI().getRawQuery());
            String method = req.getRequestMethod();
            // Get pre schedule
            if ("true".equals(query.get("pre"))) {
                if (method.equalsIgnoreCase("GET"))
                    getPreCourseSchedule(cookieStore, apiResponse);
                else if (method.equalsIgnoreCase("POST"))
                    postPreCourseSchedule(readRequestBody(req, StandardCharsets.UTF_8), cookieStore, apiResponse);
                else
                    apiResponse.errorUnsupportedHttpMethod(method);
            }
            // Get schedule
            else {
                if (method.equalsIgnoreCase("GET"))
                    getCourseSchedule(cookieStore, apiResponse);
                else
                    apiResponse.errorUnsupportedHttpMethod(method);
            }

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

    private void getPreCourseSchedule(CookieStore cookieStore, ApiResponse response) {
        Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos31315")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .followRedirects(false)
                .proxy(proxyManager.getProxy());
        Element body = checkCourseNckuLoginRequiredPage(conn, response);
        if (body == null)
            return;

        // Get year, semester
        CourseSchedule.SemesterInfo semesterInfo = getYearSemester(body);
        if (semesterInfo == null)
            response.addWarn("Semester info not found");

        // Get student ID
        CourseSchedule.StudentShortInfo studentShortInfo = getStudentShortInfo(body, response);
        if (studentShortInfo == null)
            response.addWarn("Student short info not found");

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
                StringBuilder builder = new StringBuilder();
                // Parse section
                CourseSchedule.parseTimeStr(builder, time, response);
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
        if (action == null || action.isEmpty()) {
            response.errorBadPayload("Payload form require \"action\"");
            return;
        }
        if (action.equals("delete")) {
            if (info == null || info.isEmpty()) {
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
        SemesterInfo semesterInfo = getYearSemester(body);
        if (semesterInfo == null)
            response.addWarn("Semester info not found");

        // Get student ID
        StudentShortInfo studentShortInfo = getStudentShortInfo(body, response);
        if (studentShortInfo == null)
            response.addWarn("Student short info not found");

        // Get credits
        Element creditsEle, currentTimeEle = body.getElementById("current_time");
        if (currentTimeEle == null || (creditsEle = currentTimeEle.nextElementSibling()) == null) {
            response.errorParse("Credits not found");
            return;
        }
        String creditsStr = creditsEle.text();
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
        Elements eachCourse = getCourseListTable(body, response);
        if (eachCourse == null)
            return;

        JsonArray courseInfo = null;
        String lastSN = null;
        JsonArray courseScheduleData = new JsonArray();
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
                parseTimeStr(builder, time, response);
            }
            info.put("time", builder.toString());
            String room = rowElements.get(8).text();
            int roomIdEnd = room.indexOf(' ');
            if (!room.isEmpty() && roomIdEnd == -1) {
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
        if (semesterInfo != null)
            builder.appendRaw("semesterInfo", semesterInfo.toJsonString());
        if (studentShortInfo != null)
            builder.appendRaw("studentInfo", studentShortInfo.toJsonString());
        builder.append("credits", credits);
        builder.append("schedule", courseScheduleData);
        response.setData(builder.toString());
    }

    static void parseTimeStr(StringBuilder builder, String time, ApiResponse response) {
        int dayEnd = time.indexOf(' ');
        String day = dayEnd == -1 ? time : time.substring(0, dayEnd);
        Integer date = dayOfWeekTextToInt.get(day);
        if (date == null) {
            response.addWarn(TAG + "Course Time parse error, unknown date: " + day);
            date = -1;
        }
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
    }

    private Elements getCourseListTable(Element body, ApiResponse response) {
        Elements tables = body.getElementsByTag("table");
        if (tables.isEmpty()) {
            response.errorParse("Table not found");
            return null;
        }
        Elements tbody = tables.get(0).getElementsByTag("tbody");
        if (tbody.isEmpty()) {
            response.errorParse("Table body not found");
            return null;
        }
        return tbody.get(0).getElementsByTag("tr");
    }

    private SemesterInfo getYearSemester(Element body) {
        int year, semester;
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
            return new SemesterInfo(year, semester);
        }
        return null;
    }

    private StudentShortInfo getStudentShortInfo(Element body, ApiResponse response) {
        Elements elements = body.getElementsByClass("run_logout");
        Element logOutButton = null;
        for (Element element : elements) {
            if (element.childNodeSize() == 5) {
                logOutButton = element;
                break;
            }
        }
        if (logOutButton == null) {
            response.addWarn("Student short info button not found");
            return null;
        }
        List<Node> nodes = logOutButton.childNodes();
        Node node0 = nodes.get(0);
        String studentInfo = !(node0 instanceof TextNode) ? null : ((TextNode) node0).text().trim();
        Node node1 = nodes.get(1);
        String studentName = !(node1 instanceof Element) ? null : ((Element) node1).text().trim();
        Node node2 = nodes.get(2);
        String studentId = !(node2 instanceof TextNode) ? null : ((TextNode) node2).text().trim();
        if (studentId != null) {
            int l = studentId.indexOf('（');
            int r = studentId.indexOf('）');
            studentId = studentId.substring(l == -1 ? 0 : l + 1, r == -1 ? studentId.length() : r);
        }

        if (studentInfo == null || studentName == null || studentId == null || studentId.isEmpty()) {
            return null;
        }

        return new StudentShortInfo(studentId, studentName, studentInfo);
    }
}
