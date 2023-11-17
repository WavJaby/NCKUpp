package com.wavjaby.api;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Main;
import com.wavjaby.Module;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiCode;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.parseUrlEncodedForm;

@RequestMapping("/api/v0")
public class CourseFuncBtn implements Module {
    private static final String TAG = "CourseFuncBtn";
    private static final Logger logger = new Logger(TAG);
    private final RobotCode robotCode;
    private final ProxyManager proxyManager;

    public CourseFuncBtn(ProxyManager proxyManager, RobotCode robotCode) {
        this.robotCode = robotCode;
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


    @RequestMapping("/courseFuncBtn")
    public RestApiResponse courseFuncBtn(HttpExchange req) {
        long startTime = System.currentTimeMillis();
        CookieStore cookieStore = new CookieManager().getCookieStore();
        String loginState = getDefaultCookie(req, cookieStore);

        ApiResponse apiResponse = new ApiResponse();
        Map<String, String> query = parseUrlEncodedForm(req.getRequestURI().getRawQuery());
        String key;
        if ((key = query.get("cosdata")) != null) {
            postCosData(key, cookieStore, apiResponse);
        } else if ((key = query.get("prekey")) != null) {
            postPreKey(key, cookieStore, apiResponse);
        } else if ((key = query.get("flexTimeKey")) != null) {
            getFlexTime(key, cookieStore, apiResponse);
        } else
            apiResponse.errorBadQuery("Query require one of \"cosdata\" or \"prekey\"");

        packCourseLoginStateCookie(req, loginState, cookieStore);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return apiResponse;
    }

    public void getFlexTime(String key, CookieStore cookieStore, ApiResponse response) {
        try {
            Connection.Response post = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=get_flex_time")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .userAgent(Main.USER_AGENT)
                    .method(Connection.Method.POST)
                    .requestBody("key=" + URLEncoder.encode(key, "UTF-8"))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .execute();
            JsonObject postResult = new JsonObject(post.body());
            int count = postResult.getInt("count");
            if (count == 0) {
                response.errorParse("Flex time not found");
                return;
            }

            List<Node> n = Parser.parseFragment(postResult.getString("html"), new Element("table"), "");
            Node table, tbody;
            if (n.isEmpty() || (table = n.get(0)) == null) {
                response.errorParse("Flex time table not found");
                return;
            }
            if ((tbody = table.firstChild()) == null) {
                response.errorParse("Flex time table body not found");
                return;
            }

            JsonArrayStringBuilder timeList = new JsonArrayStringBuilder();
            List<Node> nodes = tbody.childNodes();
            if (nodes.size() - 1 != count) {
                response.addWarn("Count row not match");
            }

            for (int i = 1; i < nodes.size(); i++) {
                Element tr = (Element) nodes.get(i);
                Elements cols = tr.children();
                if (cols.size() != 3) {
                    response.errorParse("Flex time table body row parse error");
                    return;
                }
                JsonObjectStringBuilder timeData = new JsonObjectStringBuilder();
                String date = cols.get(0).ownText().trim();
                int dateSub = date.indexOf('(');
                timeData.append("date", dateSub != -1 ? date.substring(0, dateSub) : date);
                timeData.append("timeStart", cols.get(1).ownText().trim());
                timeData.append("timeEnd", cols.get(2).ownText().trim());

                timeList.append(timeData);
            }

            response.setData(timeList.toString());
        } catch (IOException e) {
            logger.errTrace(e);
        }
    }

    public void postPreKey(String key, CookieStore cookieStore, ApiResponse response) {
        try {
            Connection.Response post = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=add_presub")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .userAgent(Main.USER_AGENT)
                    .method(Connection.Method.POST)
                    .requestBody("key=" + URLEncoder.encode(key, "UTF-8"))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .execute();
            JsonObject postResult = new JsonObject(post.body());
            String msg = postResult.getString("msg");
            response.setMessageDisplay(msg);

            if (!postResult.getBoolean("result"))
                response.errorCourseNCKU();

            // Already in pre course
            if (msg.equals("該課程已在預選清單中") || msg.equals("This course is included in the preliminary course schedule."))
                response.setResponseCode(ApiCode.COURSE_NCKU_ALREADY_ERROR);

        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
    }

    public void postCosData(String key, CookieStore cookieStore, ApiResponse response) {
        try {
            boolean success = false;
            String message = null;
            for (int i = 0; i < 5; i++) {
                // Get ticket
                String postData;
                postData = "time=" + (System.currentTimeMillis() / 1000) + "&cosdata=" + URLEncoder.encode(key, "UTF-8");
                Connection.Response post = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21112")
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxyManager.getProxy())
                        .userAgent(Main.USER_AGENT)
                        .method(Connection.Method.POST)
                        .requestBody(postData)
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .execute();
                JsonObject postResult = new JsonObject(post.body());
                if (!postResult.getBoolean("status")) {
                    message = postResult.getString("msg");
                    break;
                }

                // Post request
                String code = robotCode.getCode(courseNckuOrg + "/index.php?c=cos21112&m=captcha",
                        cookieStore,
                        RobotCode.Mode.SINGLE,
                        RobotCode.WordType.HEX
                );
                postData = "time=" + (System.currentTimeMillis() / 1000) +
                        "&ticket=" + URLEncoder.encode(postResult.getString("ticket"), "UTF-8") +
                        "&code=" + code +
                        "&cosdata=" + URLEncoder.encode(postResult.getString("cosdata"), "UTF-8");
                Connection.Response result = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21112")
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .proxy(proxyManager.getProxy())
                        .userAgent(Main.USER_AGENT)
                        .method(Connection.Method.POST)
                        .requestBody(postData)
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .execute();

                JsonObject resultData = new JsonObject(result.body());
                message = resultData.getString("msg");
                if (resultData.getBoolean("status")) {
                    success = true;
                    break;
                }
                if (!message.contains("CAPTCHA") && !message.contains("驗證碼"))
                    break;
                logger.log("Retry");
            }
            if (!success)
                response.errorCourseNCKU();
            if (message == null || message.isEmpty())
                message = "Unknown error";
            response.setMessageDisplay(message);
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
    }
}
