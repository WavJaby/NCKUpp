package com.wavjaby.api;

import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.Main;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiRequestParser;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.ApiRequestParser.parseApiRequest;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.*;

public class CoursePreRegister implements EndpointModule {
    private static final String TAG = "[PreRegister]";
    private static final Logger logger = new Logger(TAG);
    private final ProxyManager proxyManager;

    public CoursePreRegister(ProxyManager proxyManager) {
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
        CookieStore cookieStore = new CookieManager().getCookieStore();
        String loginState = getDefaultCookie(req, cookieStore);

        ApiResponse apiResponse = new ApiResponse();

        String method = req.getRequestMethod();
        if (method.equalsIgnoreCase("GET"))
            getCoursePreRegisterList(req.getRequestURI().getRawQuery(), apiResponse, cookieStore);
        else if (method.equalsIgnoreCase("POST"))
            postCoursePreRegisterList(req.getRequestURI().getRawQuery(), readRequestBody(req, StandardCharsets.UTF_8), apiResponse, cookieStore);
        else
            apiResponse.errorUnsupportedHttpMethod(method);

        packCourseLoginStateCookie(req, loginState, cookieStore);
        apiResponse.sendResponse(req);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
    };

    private static class CoursePreRegisterRequest extends ApiRequestParser.ApiRequestLib {
        @ApiRequestParser.Required
        private String mode;

        @ApiRequestParser.Required
        @ApiRequestParser.Payload
        private String prechk;
        @ApiRequestParser.Required
        @ApiRequestParser.Payload
        private String cosdata;
        @ApiRequestParser.Required
        @ApiRequestParser.Payload
        private String action;
        @ApiRequestParser.Required
        @ApiRequestParser.Payload
        private String preSkip;
    }

    private void getCoursePreRegisterList(String rawQuery, ApiResponse response, CookieStore cookieStore) {
        CoursePreRegisterRequest request = parseApiRequest(new CoursePreRegisterRequest(), rawQuery, null, response);
        if (request == null)
            return;

        if (request.mode.equals("genEdu")) {
            Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21362")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .followRedirects(false)
                    .proxy(proxyManager.getProxy());
            Element body = checkCourseNckuLoginRequiredPage(conn, response, false);
            if (body == null)
                return;

            String pageError = checkCourseNckuPageError(body);
            if (pageError != null) {
                if (pageError.startsWith("目前尚無志願課程預排資料") ||
                        pageError.startsWith("No elective courses in your preliminary course schedule")) {
                    response.setData(new JsonObjectStringBuilder()
                            .append("action")
                            .appendRaw("courseList", "[]")
                            .toString());
                    return;
                }
                response.setMessageDisplay(pageError);
                response.errorCourseNCKU();
                return;
            }

            JsonObjectStringBuilder result = new JsonObjectStringBuilder();

            Element action = body.getElementById("cos21362_action");
            if (action == null) {
                response.errorParse("PreRegisterList action key not found");
                return;
            }
            result.append("action", action.ownText().trim());

            Element preSkip = body.getElementById("pre_duph_skip");
            if (preSkip == null) {
                response.errorParse("PreRegisterList pre skip not found");
                return;
            }
            result.append("preSkip", !preSkip.ownText().trim().isEmpty());

            Element tbody = getTbody(body, response);
            if (tbody == null)
                return;

            JsonArrayStringBuilder course = new JsonArrayStringBuilder();
            for (Element row : tbody.children()) {
                Elements cols = row.children();
                if (cols.size() < 10) {
                    response.errorParse("PreRegisterList table row error");
                    return;
                }

                String serialId = cols.get(1).ownText().trim() + '-' + cols.get(2).ownText().trim();
                String name = cols.get(3).ownText().trim();
                Element register = cols.get(9).firstElementChild();
                if (register == null) {
                    response.errorParse("PreRegisterList course register button not found");
                    return;
                }
                String onclickStr = register.attr("onclick");
                int start, end;
                if ((start = onclickStr.indexOf('\'')) == -1 ||
                        (end = onclickStr.indexOf('\'', start + 1)) == -1) {
                    response.errorParse("PreRegisterList course cosdata not found");
                    return;
                }
                String cosdata = onclickStr.substring(start + 1, end);
                if ((start = onclickStr.indexOf('\'', end + 1)) == -1 ||
                        (end = onclickStr.indexOf('\'', start + 1)) == -1) {
                    response.errorParse("PreRegisterList course prechk not found");
                    return;
                }
                String prechk = onclickStr.substring(start + 1, end);


                course.append(new JsonObjectStringBuilder()
                        .append("serialId", serialId)
                        .append("name", name)
                        .append("cosdata", cosdata)
                        .append("prechk", prechk)
                );
            }
            result.append("courseList", course);
            response.setData(result.toString());
        } else if (request.mode.equals("course")) {
            Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21322")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .followRedirects(false)
                    .proxy(proxyManager.getProxy());
            Element body = checkCourseNckuLoginRequiredPage(conn, response, false);
            if (body == null)
                return;

            String pageError = checkCourseNckuPageError(body);
            if (pageError != null) {
                if (pageError.startsWith("目前尚無一般課程預排資料") ||
                        pageError.startsWith("No general courses in your preliminary course schedule")) {
                    response.setData(new JsonObjectStringBuilder()
                            .append("action")
                            .appendRaw("courseList", "[]")
                            .toString());
                    return;
                }
                response.setMessageDisplay(pageError);
                response.errorCourseNCKU();
                return;
            }

            JsonObjectStringBuilder result = new JsonObjectStringBuilder();

            Element action = body.getElementById("cos21322_action");
            if (action == null) {
                response.errorParse("PreRegisterList action key not found");
                return;
            }
            result.append("action", action.ownText().trim());

            Element tbody = getTbody(body, response);
            if (tbody == null)
                return;

            JsonArrayStringBuilder course = new JsonArrayStringBuilder();
            for (Element row : tbody.children()) {
                Elements cols = row.children();
                if (cols.size() < 10) {
                    response.errorParse("PreRegisterList table row error");
                    return;
                }

                String serialId = cols.get(1).ownText().trim() + '-' + cols.get(2).ownText().trim();
                String name = cols.get(3).ownText().trim();
                Element register = cols.get(9).firstElementChild();
                if (register == null) {
                    response.errorParse("PreRegisterList course register button not found");
                    return;
                }
                String onclickStr = register.attr("onclick");
                int start, end;
                if ((start = onclickStr.indexOf('\'')) == -1 ||
                        (end = onclickStr.indexOf('\'', start + 1)) == -1) {
                    response.errorParse("PreRegisterList course cosdata not found");
                    return;
                }
                String cosdata = onclickStr.substring(start + 1, end);


                course.append(new JsonObjectStringBuilder()
                        .append("serialId", serialId)
                        .append("name", name)
                        .append("cosdata", cosdata)
                );
            }
            result.append("courseList", course);
            response.setData(result.toString());
        } else {
            response.errorBadQuery("Invalid mode: \"" + request.mode + '"');
        }
    }

    private Element getTbody(Element body, ApiResponse response) {
        Element mainTable = body.getElementById("main-table");
        if (mainTable == null) {
            response.errorParse("PreRegisterList table not found");
            return null;
        }
        Element tbody = mainTable.getElementsByTag("tbody").first();
        if (tbody == null) {
            response.errorParse("PreRegisterList tbody not found");
            return null;
        }
        return tbody;
    }

    private void postCoursePreRegisterList(String rawQuery, String payload, ApiResponse response, CookieStore cookieStore) {
        CoursePreRegisterRequest request = parseApiRequest(new CoursePreRegisterRequest(), rawQuery, payload, response);
        if (request == null)
            return;

        if (request.mode.equals("genEdu")) {
            String prechk = request.prechk;
            String cosdata = request.cosdata;
            String action = request.action;
            boolean preSkip = request.preSkip.equals("true");

            try {
                if (!preSkip) {
                    long time = (System.currentTimeMillis() / 1000);
                    Connection.Response post = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21215&m=pre_duph_chk&time=" + time)
                            .header("Connection", "keep-alive")
                            .cookieStore(cookieStore)
                            .ignoreContentType(true)
                            .proxy(proxyManager.getProxy())
                            .userAgent(Main.USER_AGENT)
                            .method(Connection.Method.POST)
                            .requestBody("prechk=" + prechk + "&time=" + time)
                            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                            .header("X-Requested-With", "XMLHttpRequest")
                            .execute();

                    JsonObject postResult = new JsonObject(post.body());
                    if (postResult.getBoolean("status")) {
                        String msg = postResult.getString("msg");
                        response.setMessageDisplay(msg);
                        return;
                    }
                    preSkip = postResult.containsKey("empty999") && postResult.getBoolean("empty999");
                }

                long time = (System.currentTimeMillis() / 1000);
                Connection.Response postCourse = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21362&m=" + action)
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxyManager.getProxy())
                        .userAgent(Main.USER_AGENT)
                        .method(Connection.Method.POST)
                        .requestBody("time=" + time + "&cosdata=" + cosdata)
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .execute();

                JsonObject postResult = new JsonObject(postCourse.body());
                String msg = postResult.getString("msg");
                logger.log(msg);
                response.setMessageDisplay(msg);
                response.setData(new JsonObjectStringBuilder()
                        .append("preSkip", preSkip)
                        .toString());

                if (!postResult.getBoolean("status"))
                    response.errorCourseNCKU();

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
