package com.wavjaby.lib.restapi;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.logger.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.wavjaby.lib.Lib.setAllowOrigin;

public class RestApiResponse {
    protected int httpResponseCode;
    protected String data;
    protected boolean success;
    protected String msg;
    protected int customCode;

    protected final List<String> err = new ArrayList<>(0);
    protected final List<String> warn = new ArrayList<>(0);
    protected final List<String> info = new ArrayList<>(0);

    public RestApiResponse() {
        this.success = true;
    }

    public RestApiResponse(int httpResponseCode, boolean success, String msg) {
        this.httpResponseCode = httpResponseCode;
        this.success = success;
        this.msg = msg;
    }

    public RestApiResponse(int httpResponseCode, String data) {
        this.httpResponseCode = httpResponseCode;
        this.data = data;
        this.success = true;
    }

    public byte[] toJsonByte() {
        JsonObjectStringBuilder builder = new JsonObjectStringBuilder();
        builder.append("success", success);
        builder.appendRaw("data", data);
        builder.append("code", customCode);
        if (!err.isEmpty())
            builder.append("err", new JsonArray(err));

        if (!warn.isEmpty())
            builder.append("warn", new JsonArray(warn));

        if (msg != null)
            builder.append("msg", msg);

        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    public void sendResponse(HttpExchange req) {
        Headers headers = req.getResponseHeaders();
        headers.set("Content-Type", "application/json;charset=UTF-8");
        setAllowOrigin(req.getRequestHeaders(), headers);

        byte[] data = toJsonByte();
        try {
            // send response
            req.sendResponseHeaders(httpResponseCode, data.length);
            OutputStream response = req.getResponseBody();
            response.write(data);
            req.close();
        } catch (IOException e) {
            Logger.errTrace("RestApiResponse", e);
            req.close();
        } finally {
            req.close();
        }
    }
}
