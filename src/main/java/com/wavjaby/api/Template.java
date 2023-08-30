package com.wavjaby.api;

import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;

import java.net.CookieManager;
import java.net.CookieStore;

import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;

@SuppressWarnings("ALL")
public class Template implements EndpointModule {
    private static final String TAG = "[Template]";
    private static final Logger logger = new Logger(TAG);


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

        ApiResponse apiResponse = new ApiResponse();

        packCourseLoginStateCookie(req, loginState, cookieStore);
        apiResponse.sendResponse(req);

        logger.log("Get template " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
