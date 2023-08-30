package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.api.Login.loginCheckString;
import static com.wavjaby.lib.Cookie.*;

public class Logout implements EndpointModule {
    private static final String TAG = "[Logout]";
    private static final Logger logger = new Logger(TAG);
    private final ProxyManager proxyManager;

    public Logout(ProxyManager proxyManager) {
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
        String loginState = getDefaultCookie(req.getRequestHeaders(), cookieStore);

        // Logout
        ApiResponse apiResponse = new ApiResponse();
        logout(cookieStore, apiResponse);

        Headers responseHeaders = req.getResponseHeaders();
        packCourseLoginStateCookie(req, loginState, cookieStore);
        addRemoveCookieToHeader("authData", "/api/login", req);
        addRemoveCookieToHeader("stuSysLoginData", "/", req);

        apiResponse.sendResponse(req);
        logger.log("Logout " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private void logout(CookieStore cookieStore, ApiResponse response) {
        try {
            Connection.Response toLogin = HttpConnection.connect(courseNckuOrg + "/index.php?c=auth&m=logout")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .proxy(proxyManager.getProxy())
                    .execute();

            response.setData(new JsonObjectStringBuilder()
                    .append("login", toLogin.body().contains(loginCheckString))
                    .toString()
            );
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
        }
    }
}
