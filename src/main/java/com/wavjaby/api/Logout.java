package com.wavjaby.api;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Module;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.api.login.Login.loginCheckString;
import static com.wavjaby.lib.Cookie.*;


@RequestMapping("/api/v0")
public class Logout implements Module {
    private static final String TAG = "Logout";
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

    @RequestMapping("/logout")
    public RestApiResponse logout(HttpExchange req) {
        long startTime = System.currentTimeMillis();
        CookieStore cookieStore = new CookieManager().getCookieStore();
        String loginState = getDefaultCookie(req, cookieStore);

        // Logout
        ApiResponse response = new ApiResponse();
        logout(cookieStore, response);

        packCourseLoginStateCookie(req, loginState, cookieStore);
        addRemoveCookieToHeader("authData", "/api/v0/login", req);
        addRemoveCookieToHeader("stuSysLoginData", "/", req);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return response;
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
