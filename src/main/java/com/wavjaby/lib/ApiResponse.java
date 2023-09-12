package com.wavjaby.lib;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Main;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.logger.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.wavjaby.lib.Cookie.isSafari;
import static com.wavjaby.lib.Lib.setAllowOrigin;

public class ApiResponse {
    private static final Logger logger = new Logger("ApiResponse");
    private boolean success = true;
    private final List<String> err = new ArrayList<>();
    private final List<String> warn = new ArrayList<>();
    private String msg;
    private ApiCode responseCode = ApiCode.SUCCESS;
    private String data;

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

    @Override
    public String toString() {
        JsonObjectStringBuilder builder = new JsonObjectStringBuilder();

        if (!err.isEmpty())
            builder.append("err", new JsonArray(err));

        if (!warn.isEmpty())
            builder.append("warn", new JsonArray(warn));

        if (msg != null)
            builder.append("msg", msg);

        builder.append("code", responseCode.code);

        builder.append("success", success);
        builder.appendRaw("data", data);

        return builder.toString();
    }

    public void errorUnsupportedHttpMethod(String method) {
        responseCode = ApiCode.UNSUPPORTED_HTTP_METHOD;
        success = false;
        err.add("Unsupported HTTP Method: " + method);
    }

    public void errorBadQuery(String message) {
        responseCode = ApiCode.BAD_QUERY;
        success = false;
        err.add("Bad query: " + message);
    }

    public void errorBadPayload(String message) {
        responseCode = ApiCode.BAD_PAYLOAD;
        success = false;
        err.add("Bad payload: " + message);
    }

    public void errorNetwork(IOException e) {
        errorNetwork(e.toString());
    }

    public void errorNetwork(String message) {
        if (responseCode.code < 1999)
            responseCode = ApiCode.SERVER_NETWORK_ERROR;
        success = false;
        err.add("Server Network error: " + message);
    }

    public void errorFetch(String message) {
        if (responseCode.code < 1999)
            responseCode = ApiCode.SERVER_DATA_FETCH_ERROR;
        success = false;
        err.add("Server fetch error: " + message);
    }

    public void errorServerDatabase(String message) {
        responseCode = ApiCode.SERVER_DATABASE_ERROR;
        success = false;
        err.add("Server database error: " + message);
    }

    public void errorParse(String message) {
        if (responseCode.code < 1999)
            responseCode = ApiCode.SERVER_DATA_PARSE_ERROR;
        success = false;
        err.add("Server parse error: " + message);
    }

    public void errorCookie(String message) {
        responseCode = ApiCode.COOKIE_ERROR;
        success = false;
        err.add("Cookie format error: " + message);
    }

    public void errorLoginRequire() {
        responseCode = ApiCode.LOGIN_REQUIRE;
        success = false;
        err.add("Login required");
    }

    public void errorCourseNCKU() {
        responseCode = ApiCode.COURSE_NCKU_ERROR;
        success = false;
        err.add("Error from " + Main.courseNcku);
    }

    public void setResponseCode(ApiCode code) {
        responseCode = code;
    }

    public int getResponseCode() {
        if (responseCode == ApiCode.UNSUPPORTED_HTTP_METHOD)
            return 405; // Method Not Allowed
        if (responseCode == ApiCode.LOGIN_REQUIRE)
            return 403; // Forbidden

        return responseCode.code <= ApiCode.NORMAL.code ? 200
                : responseCode.code <= ApiCode.SERVER_ERROR.code ? 500
                : 400;
    }

    public void sendResponse(HttpExchange req) {
        Headers responseHeader = req.getResponseHeaders();
        byte[] dataByte = this.toString().getBytes(StandardCharsets.UTF_8);

        List<String> cookie;
        if (isSafari(req.getRequestHeaders()) && (cookie = responseHeader.get("Set-Cookie")) != null) {
            responseHeader.set("Content-Type", "application/json; charset=UTF-8; c=" +
                    String.join(",", cookie).replace("Path=/api/login", "Path=/"));
            responseHeader.remove("Set-Cookie");
        } else
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

        setAllowOrigin(req.getRequestHeaders(), responseHeader);

        try {
            // send response
            req.sendResponseHeaders(getResponseCode(), dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            logger.errTrace(e);
            req.close();
        }
    }
}
