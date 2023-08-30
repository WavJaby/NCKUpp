package com.wavjaby.api;

import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.Cookie;
import com.wavjaby.logger.Logger;

import java.net.CookieManager;
import java.net.CookieStore;

import static com.wavjaby.Main.courseNcku;
import static com.wavjaby.Main.courseNckuOrgUri;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;

public class AllDept implements EndpointModule {
    private static final String TAG = "[AllDept]";
    private static final Logger logger = new Logger(TAG);
    private final Search search;
    private String deptGroup;

    public AllDept(Search search) {
        this.search = search;
    }

    @Override
    public void start() {
        CookieStore cookieStore = new CookieManager().getCookieStore();
        cookieStore.add(courseNckuOrgUri, Cookie.createHttpCookie("PHPSESSID", "ID", courseNcku));
        Search.AllDeptGroupData allDept = search.getAllDeptGroupData(cookieStore);
        deptGroup = allDept.toString();
        logger.log("Get " + allDept.getDeptCount() + " dept");
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
        apiResponse.setData(deptGroup);

        packCourseLoginStateCookie(req, loginState, cookieStore);
        apiResponse.sendResponse(req);

        logger.log("Get all dept " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
