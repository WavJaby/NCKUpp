package com.wavjaby.api.search;

import com.wavjaby.ProxyManager;
import com.wavjaby.api.RobotCode;
import com.wavjaby.json.JsonException;
import com.wavjaby.json.JsonObject;
import com.wavjaby.lib.HttpResponseData;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieStore;
import java.net.URLEncoder;

import static com.wavjaby.lib.Lib.findStringBetween;

public class RobotCheck {
    private static final Logger logger = new Logger("RobotCheck");
    private static final int MAX_ROBOT_CHECK_REQUEST_TRY = 3;
    private static final int MAX_ROBOT_CHECK_TRY = 2;
    private final RobotCode robotCode;
    private final ProxyManager proxyManager;

    public RobotCheck(RobotCode robotCode, ProxyManager proxyManager) {
        this.robotCode = robotCode;
        this.proxyManager = proxyManager;
    }

    public HttpResponseData sendRequest(String urlOriginUri, Connection request, CookieStore cookieStore) {
        boolean networkError = false;
        for (int i = 0; i < MAX_ROBOT_CHECK_REQUEST_TRY; i++) {
            String response;
            try {
                response = request.execute().body();
                networkError = false;
            } catch (UncheckedIOException | IOException e) {
                networkError = true;
                // Last try failed
                if (i + 1 == MAX_ROBOT_CHECK_REQUEST_TRY)
                    logger.errTrace(e);
                else
                    logger.warn("Fetch page failed(" + (i + 1) + "): " + e.getMessage() + ", Retry...");
                continue;
            }

            // Check if no robot
            String codeTicket = findStringBetween(response, "index.php?c=portal&m=robot", "code_ticket=", "&");
            if (codeTicket == null) {
                String baseUrl = findStringBetween(response, "<base", "href=\"", "\"");
                return new HttpResponseData(HttpResponseData.ResponseState.SUCCESS, response, baseUrl);
            }

            // Crack robot
            logger.warn("Crack robot code");
            for (int j = 0; j < MAX_ROBOT_CHECK_TRY; j++) {
                String code = robotCode.getCode(urlOriginUri + "/index.php?c=portal&m=robot", cookieStore, RobotCode.Mode.MULTIPLE_CHECK, RobotCode.WordType.ALPHA);
                if (code == null || code.isEmpty())
                    continue;
                try {
                    String result = HttpConnection.connect(urlOriginUri + "/index.php?c=portal&m=robot")
                            .header("Connection", "keep-alive")
                            .cookieStore(cookieStore)
                            .ignoreContentType(true)
                            .proxy(proxyManager.getProxy())
                            .method(Connection.Method.POST)
//                            .header("Referer", "https://course-query.acad.ncku.edu.tw/query/index.php?c=qry11215&m=en_query")
                            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                            .header("X-Requested-With", "XMLHttpRequest")
                            .requestBody("sid=&time=" + (System.currentTimeMillis() / 1000) +
                                    "&code_ticket=" + URLEncoder.encode(codeTicket, "UTF-8") +
                                    "&code=" + code)
                            .execute().body();
//                    logger.log(result);
                    boolean success = new JsonObject(result).getBoolean("status");
                    logger.warn("Crack code(" + j + "): " + code + ", " +
                            (success ? "success" : "retry"));
                    if (success)
                        break;
                } catch (JsonException e) {
                    logger.errTrace(e);
                } catch (IOException e) {
                    logger.errTrace(e);
                    networkError = true;
                }
            }
        }
        if (networkError)
            return new HttpResponseData(HttpResponseData.ResponseState.NETWORK_ERROR);
        else
            return new HttpResponseData(HttpResponseData.ResponseState.ROBOT_CODE_CRACK_ERROR);
    }
}
