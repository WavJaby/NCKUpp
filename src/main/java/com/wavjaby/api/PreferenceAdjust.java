package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.ApiResponse;
import com.wavjaby.EndpointModule;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;

import static com.wavjaby.Cookie.getDefaultCookie;
import static com.wavjaby.Cookie.packLoginStateCookie;
import static com.wavjaby.Lib.getRefererUrl;
import static com.wavjaby.Lib.setAllowOrigin;
import static com.wavjaby.Main.courseNckuOrg;

public class PreferenceAdjust implements EndpointModule {
    private static final String TAG = "[PreferenceAdjust] ";

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

        try {
            ApiResponse apiResponse = new ApiResponse();

            preferenceAdjust(apiResponse, cookieStore);
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
        Logger.log(TAG, "Preference adjust " + (System.currentTimeMillis() - startTime) + "ms");
    };

    private void preferenceAdjust(ApiResponse apiResponse, CookieStore cookieStore) {
        Connection pageFetch = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos21342")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .ignoreContentType(true);

        String body = null;
        try {
            body = pageFetch.execute().body();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        int tableStart;
        if ((tableStart = body.indexOf("id=\"list_A9_000_999\"")) == -1) {
            apiResponse.addError(TAG + "list body not found");
        }
        tableStart += 20;

        // Get data type
        int datatypeStart, datatypeEnd;
        if ((datatypeStart = body.indexOf("data_type=\"", tableStart)) == -1 ||
                (datatypeEnd = body.indexOf('"', datatypeStart += 11)) == -1) {
            apiResponse.addError(TAG + "datatype not found");
            return;
        }
        String datatype = body.substring(datatypeStart, datatypeEnd);

        // Get action key
        int actionKeyStart, actionKeyEnd;
        if ((actionKeyStart = body.indexOf("id='cos21342_action'")) == -1 ||
                (actionKeyStart = body.indexOf('>', actionKeyStart + 20)) == -1 ||
                (actionKeyEnd = body.indexOf('<', actionKeyStart += 1)) == -1) {
            apiResponse.addError(TAG + "actionKey not found");
            return;
        }
        String actionKey = body.substring(actionKeyStart, actionKeyEnd);

        // Get list items
        int itemTagStart, itemTagEnd = datatypeEnd,
                itemIdStart, itemIdEnd,
                courseNameStart, courseNameEnd,
                courseInfoStart, courseInfoEnd;
        while ((itemTagStart = body.indexOf("class=\"list-group-item", itemTagEnd)) != -1 &&
                (itemTagEnd = body.indexOf('>', itemTagStart + 22)) != -1) {
            // Get itemID
            if ((itemIdStart = body.indexOf("data_item=\"", itemTagStart)) == -1 ||
                    (itemIdEnd = body.indexOf('"', itemIdStart += 11)) == -1 ||
                    itemIdStart > itemTagEnd || itemIdEnd > itemTagEnd) {
                apiResponse.addError(TAG + "item id not found");
                return;
            }
            String itemId = body.substring(itemIdStart, itemIdEnd);

            // Get course name
            if ((courseNameStart = body.indexOf("class=\"course_name\"", itemTagEnd)) == -1 ||
                    (courseNameStart = body.indexOf('>', courseNameStart + 19)) == -1 ||
                    (courseNameEnd = body.indexOf('<', courseNameStart)) == -1) {
                apiResponse.addError(TAG + "course name not found");
                return;
            }
            String courseIdAndName = body.substring(courseNameStart, courseNameEnd);

            // Get course info
            if ((courseInfoStart = body.indexOf('>', courseNameEnd)) == -1 ||
                    (courseInfoEnd = body.indexOf('<', courseInfoStart += 1)) == -1) {
                apiResponse.addError(TAG + "course info not found");
                return;
            }
            String courseInfo = body.substring(courseInfoStart, courseInfoEnd).trim();

            System.out.println(itemId);
            System.out.println(courseIdAndName);
            System.out.println(courseInfo);

            itemTagEnd = courseNameEnd;
        }


        System.out.println(actionKey);
        System.out.println(datatype);
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
