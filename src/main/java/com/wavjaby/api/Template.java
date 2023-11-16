package com.wavjaby.api;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Module;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.logger.Logger;

import java.net.CookieManager;
import java.net.CookieStore;

import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;

@SuppressWarnings("ALL")
@RequestMapping("/api/v0")
public class Template implements Module {
    private static final String TAG = "Template";
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

    @RequestMapping("/template")
    public RestApiResponse template(HttpExchange req) {
        long startTime = System.currentTimeMillis();
        CookieStore cookieStore = new CookieManager().getCookieStore();
        String loginState = getDefaultCookie(req, cookieStore);

        ApiResponse apiResponse = new ApiResponse();

        packCourseLoginStateCookie(req, loginState, cookieStore);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return apiResponse;
    }
}
