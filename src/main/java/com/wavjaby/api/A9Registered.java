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
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.checkCourseNckuLoginRequiredPage;
import static com.wavjaby.lib.Lib.setAllowOrigin;

public class A9Registered implements EndpointModule {
    private static final String TAG = "[A9Registered]";
    private static final Logger logger = new Logger(TAG);
    private static final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private final ProxyManager proxyManager;

    public A9Registered(ProxyManager proxyManager) {
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
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        String loginState = getDefaultCookie(requestHeaders, cookieStore);

        try {
            ApiResponse apiResponse = new ApiResponse();
            getA9Registered(cookieStore, apiResponse);

            Headers responseHeader = req.getResponseHeaders();
            packCourseLoginStateCookie(responseHeader, loginState, cookieStore);
            byte[] dataByte = apiResponse.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(apiResponse.getResponseCode(), dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            logger.errTrace(e);
            req.close();
        }
        logger.log("Get A9Registered " + (System.currentTimeMillis() - startTime) + "ms");
    };

    private void getA9Registered(CookieStore cookieStore, ApiResponse response) {
        Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry13225")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .followRedirects(false)
                .proxy(proxyManager.getProxy());
        Element body = checkCourseNckuLoginRequiredPage(conn, response, true);
        if (body == null)
            return;

        JsonObjectStringBuilder result = new JsonObjectStringBuilder();
        Element updateTimeTable = body.getElementsByClass("title-table").first();
        String updateTime = null;
        if (updateTimeTable != null) {
            Element updateTimeEle = updateTimeTable.getElementsByTag("p").first();
            if (updateTimeEle != null) {
                updateTime = updateTimeEle.text();
                updateTime = updateTime.substring(updateTime.lastIndexOf(": ") + 1).trim();
                result.append("lastUpdate", OffsetDateTime.of(LocalDateTime.parse(updateTime, timeFormat), ZoneOffset.ofHours(+8)).getSecond());
            }
        }
        if (updateTime == null)
            result.append("lastUpdate");


        Element dataTable = body.getElementsByClass("A9-table").first();
        if (dataTable == null) {
            response.errorParse("DataTable not found");
            return;
        }
        Element tbody = dataTable.lastElementChild();
        if (tbody == null) {
            response.errorParse("DataTable body not found");
            return;
        }

        JsonObjectStringBuilder registeredCountList = new JsonObjectStringBuilder();
        for (Element row : tbody.children()) {
            Elements col = row.children();
            if (col.size() < 7) {
                response.errorParse("DataTable row parse error");
                return;
            }
            String serial = col.get(1).text().trim() + '-' + col.get(2).text().trim();
            JsonObjectStringBuilder rowResult = new JsonObjectStringBuilder();
            rowResult.append("name", col.get(4).text().trim());
            rowResult.append("count", Integer.parseInt(col.get(6).text().trim()));
            registeredCountList.append(serial, rowResult);
        }
        result.append("list", registeredCountList);
        response.setData(result.toString());
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}