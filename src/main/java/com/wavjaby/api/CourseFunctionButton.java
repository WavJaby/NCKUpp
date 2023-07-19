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
import java.util.Arrays;
import java.util.Map;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.*;

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
        String originUrl = getOriginUrl(requestHeaders);
        String loginState = getDefaultCookie(requestHeaders, cookieStore);

        try {
            ApiResponse apiResponse = new ApiResponse();
            logger.log(req.getRequestURI().getRawQuery());
            Map<String, String> query = parseUrlEncodedForm(req.getRequestURI().getRawQuery());
            processData(query, apiResponse, cookieStore);

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
        logger.log("Send cosdata " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private void processData(Map<String, String> query, ApiResponse apiResponse, CookieStore cookieStore) {
        String key;
        if ((key = query.get("cosdata")) != null) {
            postCosData(key, cookieStore, apiResponse);
        } else if ((key = query.get("prekey")) != null) {
            postPreKey(key, cookieStore, apiResponse);
        } else
            apiResponse.addError(TAG + "Key not found");
    }

    private void postPreKey(String key, CookieStore cookieStore, ApiResponse apiResponse) {
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
            String msg = parseUnicode(postResult.getString("msg"));
            if (postResult.getBoolean("result")) {
                apiResponse.setMessage(msg);
            } else
                apiResponse.addError(msg);
        } catch (IOException e) {
            logger.err(e);
            apiResponse.addError(TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
        }
    }

    private void postCosData(String key, CookieStore cookieStore, ApiResponse apiResponse) {
        try {
            JsonObject resultData = null;
            String postData;
            int i;
            boolean success = false;
            for (i = 0; i < 10; i++) {
                // Get ticket
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
                    apiResponse.addError(parseUnicode(postResult.getString("msg")));
                    return;
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

                resultData = new JsonObject(result.body());
                String msg = resultData.getString("msg");
                if (resultData.getBoolean("status")) {
                    success = true;
                    break;
                }
                if (!msg.contains("CAPTCHA") && !msg.contains("驗證碼"))
                    break;
            }
            String msg = parseUnicode(resultData.getString("msg"));
            msg = msg.replace("<br>", "\\n");
            int start = msg.indexOf('>'), end = msg.lastIndexOf('<');
            if (start != -1 && end != -1) msg = msg.substring(start + 1, end);
            if (success)
                apiResponse.setMessage(msg);
            else
                apiResponse.addError(msg);
        } catch (IOException e) {
            logger.err(e);
            apiResponse.addError(TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
        }
    }
}
