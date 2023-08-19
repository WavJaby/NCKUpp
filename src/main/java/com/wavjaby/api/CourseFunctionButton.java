package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonObject;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.parseUrlEncodedForm;
import static com.wavjaby.lib.Lib.setAllowOrigin;

public class CourseFunctionButton implements EndpointModule {
    private static final String TAG = "[CourseFunctionButton]";
    private static final Logger logger = new Logger(TAG);
    private final RobotCode robotCode;

    public CourseFunctionButton(RobotCode robotCode) {
        this.robotCode = robotCode;
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
            logger.log(req.getRequestURI().getRawQuery());
            Map<String, String> query = parseUrlEncodedForm(req.getRequestURI().getRawQuery());
            processData(query, apiResponse, cookieStore);

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
            req.close();
            e.printStackTrace();
        }
        logger.log("Course function button " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private void processData(Map<String, String> query, ApiResponse response, CookieStore cookieStore) {
        String key;
        if ((key = query.get("cosdata")) != null) {
            postCosData(key, cookieStore, response);
        } else if ((key = query.get("prekey")) != null) {
            postPreKey(key, cookieStore, response);
        } else
            response.errorBadQuery("Query require one of \"cosdata\" or \"prekey\"");
    }

    private void postPreKey(String key, CookieStore cookieStore, ApiResponse response) {
        try {
            Connection.Response post = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=add_presub")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .method(Connection.Method.POST)
                    .requestBody("key=" + URLEncoder.encode(key, "UTF-8"))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .execute();
            JsonObject postResult = new JsonObject(post.body());
            String msg = postResult.getString("msg");
            response.setMessageDisplay(msg);

            if (!postResult.getBoolean("result"))
                response.errorCourseNcku();
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
    }

    private void postCosData(String key, CookieStore cookieStore, ApiResponse response) {
        try {
            boolean success = false;
            String message = null;
            for (int i = 0; i < 10; i++) {
                // Get ticket
                String postData;
                postData = "time=" + (System.currentTimeMillis() / 1000) + "&cosdata=" + URLEncoder.encode(key, "UTF-8");
                Connection.Response post = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21112")
                        .header("Connection", "keep-alive")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
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
                String code = robotCode.getCode(
                        courseNckuOrg + "/index.php?c=cos21112&m=captcha",
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
            }
            if (!success)
                response.errorCourseNcku();
            if (message == null || message.length() == 0)
                message = "Unknown error";
            else {
                message = message.replace("<br>", "\\n");
                int start = message.indexOf('>'), end = message.lastIndexOf('<');
                if (start != -1 && end != -1) message = message.substring(start + 1, end);
            }
            response.setMessageDisplay(message);
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
    }
}
