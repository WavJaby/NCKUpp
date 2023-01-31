package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.Module;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
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

import static com.wavjaby.Cookie.getDefaultCookie;
import static com.wavjaby.Cookie.packLoginStateCookie;
import static com.wavjaby.Lib.*;
import static com.wavjaby.Main.courseNckuOrg;

public class PreferenceEnter implements Module {
    private static final String TAG = "[PreferenceEnter] ";
    private final RobotCode robotCode;

    public PreferenceEnter(RobotCode robotCode) {
        this.robotCode = robotCode;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    private final HttpHandler httpHandler = req -> {
        long startTime = System.currentTimeMillis();
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        String refererUrl = getRefererUrl(requestHeaders);
        String loginState = getDefaultCookie(requestHeaders, cookieStore);
        Map<String, String> query = parseUrlEncodedForm(req.getRequestURI().getQuery());

        try {
            JsonObjectStringBuilder data = new JsonObjectStringBuilder();
            boolean success = addPreferenceEnter(query, data, cookieStore);
            data.append("success", success);

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
        Logger.log(TAG, "Add preference " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private boolean addPreferenceEnter(Map<String, String> query, JsonObjectStringBuilder outData, CookieStore cookieStore) {
        String key;
        if ((key = query.get("key")) == null) {
            outData.append("err", TAG + "Key not found");
            return false;
        }

        try {
            JsonObject resultData = null;
            String postData;
            int i;

            for (i = 0; i < 10; i++) {
                // Get ticket
                postData = "time=" + (System.currentTimeMillis() / 1000) + "&cosdata=" + URLEncoder.encode(key, "UTF-8");
                Connection.Response post = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21112")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .method(Connection.Method.POST)
                        .requestBody(postData)
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .execute();
                JsonObject postResult = new JsonObject(post.body());
                if (!postResult.getBoolean("status")) {
                    outData.append("err", "Cant get ticket");
                    outData.append("msg", parseUnicode(postResult.getString("msg")));
                    return false;
                }

                // Post request
                String code = robotCode.getCode(courseNckuOrg + "/index.php?c=cos21112&m=captcha", cookieStore);
                postData = "time=" + (System.currentTimeMillis() / 1000) +
                        "&ticket=" + URLEncoder.encode(postResult.getString("ticket"), "UTF-8") +
                        "&code=" + code +
                        "&cosdata=" + URLEncoder.encode(postResult.getString("cosdata"), "UTF-8");
                Connection.Response result = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21112")
                        .cookieStore(cookieStore)
                        .ignoreContentType(true)
                        .method(Connection.Method.POST)
                        .requestBody(postData)
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .execute();

                resultData = new JsonObject(result.body());
                if (resultData.getBoolean("status"))
                    break;
            }
            String msg = parseUnicode(resultData.getString("msg"));
            int end = msg.indexOf('<');
            if (end != -1)
                msg = msg.substring(0, end);
            outData.append("msg", msg);
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
