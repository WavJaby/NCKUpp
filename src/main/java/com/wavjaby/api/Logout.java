package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.Module;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.wavjaby.Cookie.*;
import static com.wavjaby.Lib.getRefererUrl;
import static com.wavjaby.Lib.setAllowOrigin;
import static com.wavjaby.Main.courseNckuOrg;

public class Logout implements Module {
    private static final String TAG = "[Logout] ";


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

        try {
            // unpack cookie
            String loginState = getDefaultCookie(requestHeaders, cookieStore);

            // login
            JsonObjectStringBuilder data = new JsonObjectStringBuilder();
            boolean success = logout(data, cookieStore);
            data.append("success", success);

            Headers responseHeader = req.getResponseHeaders();
            packLoginStateCookie(responseHeader, loginState, refererUrl, cookieStore);
            responseHeader.add("Set-Cookie",
                    removeCookie("authData") + "; Path=/api/login" + getCookieInfoData(refererUrl));

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
        Logger.log(TAG, "Logout " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private boolean logout(JsonObjectStringBuilder outData, CookieStore cookieStore) {
        try {
            Connection.Response toLogin = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=logout")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .execute();

            outData.append("login", toLogin.body().contains("/index.php?c=auth&m=logout"));
            return true;
        } catch (Exception e) {
            outData.append("err", TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
        }
        return false;
    }
}
