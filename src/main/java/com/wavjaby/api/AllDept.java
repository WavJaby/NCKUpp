package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.ApiResponse;
import com.wavjaby.EndpointModule;
import com.wavjaby.logger.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;

import static com.wavjaby.Cookie.getDefaultCookie;
import static com.wavjaby.Cookie.packLoginStateCookie;
import static com.wavjaby.Lib.getRefererUrl;
import static com.wavjaby.Lib.setAllowOrigin;

public class AllDept implements EndpointModule {
    private static final String TAG = "[AllDept] ";
    private final Search search;
    private String deptGroup;

    public AllDept(Search search) {
        this.search = search;
    }


    @Override
    public void start() {
        Search.AllDeptGroupData allDept = search.getAllDeptGroupData(new CookieManager().getCookieStore());
        deptGroup = allDept.toString();
//        Logger.log(TAG, "Get " + allDeptID.size() + " dept");
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

        try {
            ApiResponse apiResponse = new ApiResponse();

            apiResponse.setData(deptGroup);

            Headers responseHeader = req.getResponseHeaders();
            packLoginStateCookie(responseHeader, loginState, refererUrl, cookieStore);
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
        Logger.log(TAG, "Get all dept " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
