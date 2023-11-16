package com.wavjaby.lib;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Main;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.logger.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static com.wavjaby.lib.Cookie.isSafari;
import static com.wavjaby.lib.Lib.setAllowOrigin;

public class ApiResponse extends RestApiResponse {

    public void addWarn(String warning) {
        warn.add(warning);
    }

    public void setMessageDisplay(String message) {
        msg = message;
    }

    public void setData(String data) {
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getData() {
        return data;
    }

    public void errorUnsupportedHttpMethod(String method) {
        customCode = ApiCode.UNSUPPORTED_HTTP_METHOD.code;
        success = false;
        err.add("Unsupported HTTP Method: " + method);
    }

    public void errorBadQuery(String message) {
        customCode = ApiCode.BAD_QUERY.code;
        success = false;
        err.add("Bad query: " + message);
    }

    public void errorBadPayload(String message) {
        customCode = ApiCode.BAD_PAYLOAD.code;
        success = false;
        err.add("Bad payload: " + message);
    }

    public void errorNetwork(IOException e) {
        errorNetwork(e.toString());
    }

    public void errorNetwork(String message) {
        if (customCode < 1999)
            customCode = ApiCode.SERVER_NETWORK_ERROR.code;
        success = false;
        err.add("Server Network error: " + message);
    }

    public void errorFetch(String message) {
        if (customCode < 1999)
            customCode = ApiCode.SERVER_DATA_FETCH_ERROR.code;
        success = false;
        err.add("Server fetch error: " + message);
    }

    public void errorServerDatabase(String message) {
        customCode = ApiCode.SERVER_DATABASE_ERROR.code;
        success = false;
        err.add("Server database error: " + message);
    }

    public void errorParse(String message) {
        if (customCode < 1999)
            customCode = ApiCode.SERVER_DATA_PARSE_ERROR.code;
        success = false;
        err.add("Server parse error: " + message);
    }

    public void errorCookie(String message) {
        customCode = ApiCode.COOKIE_ERROR.code;
        success = false;
        err.add("Cookie format error: " + message);
    }

    public void errorLoginRequire() {
        customCode = ApiCode.LOGIN_REQUIRE.code;
        success = false;
        err.add("Login required");
    }

    public void errorCourseNCKU() {
        customCode = ApiCode.COURSE_NCKU_ERROR.code;
        success = false;
        err.add("Error from " + Main.courseNcku);
    }

    public void errorTooManyRequests() {
        customCode = ApiCode.TOO_MANY_REQUESTS.code;
        success = false;
        err.add("Too many requests");
        msg = "Too many requests";
    }

    public void setResponseCode(ApiCode code) {
        customCode = code.code;
    }

    public int getResponseCode() {
        if (customCode == ApiCode.UNSUPPORTED_HTTP_METHOD.code)
            return 405; // Method Not Allowed
        if (customCode == ApiCode.LOGIN_REQUIRE.code)
            return 403; // Forbidden
        if (customCode == ApiCode.TOO_MANY_REQUESTS.code)
            return 429; // Too many requests

        return customCode <= ApiCode.NORMAL.code ? 200
                : customCode <= ApiCode.SERVER_ERROR.code ? 500
                : 400;
    }

    @Override
    public void sendResponse(HttpExchange req) {
        Headers headers = req.getResponseHeaders();

        List<String> cookie;
        if (isSafari(req.getRequestHeaders()) && (cookie = headers.get("Set-Cookie")) != null) {
            headers.set("Content-Type", "application/json; charset=UTF-8; c=" +
                    String.join(",", cookie).replace("Path=/api/login", "Path=/"));
            headers.remove("Set-Cookie");
        } else
            headers.set("Content-Type", "application/json;charset=UTF-8");

        setAllowOrigin(req.getRequestHeaders(), headers);

        byte[] data = toJsonByte();
        try {
            // send response
            req.sendResponseHeaders(getResponseCode(), data.length);
            OutputStream response = req.getResponseBody();
            response.write(data);
            req.close();
        } catch (IOException e) {
            Logger.errTrace("ApiResponse", e);
            req.close();
        } finally {
            req.close();
        }
    }
}
