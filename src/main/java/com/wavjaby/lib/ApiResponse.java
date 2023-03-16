package com.wavjaby.lib;

import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonObjectStringBuilder;

import java.util.ArrayList;
import java.util.List;

public class ApiResponse {
    private boolean success = true;
    private final List<String> err = new ArrayList<>();
    private final List<String> warn = new ArrayList<>();
    private String msg;
    private String data;

    public void addError(String error) {
        err.add(error);
        success = false;
    }

    public void addWarn(String warning) {
        warn.add(warning);
    }

    public void setMessage(String message) {
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

    @Override
    public String toString() {
        JsonObjectStringBuilder builder = new JsonObjectStringBuilder();

        if (err.size() > 0)
            builder.append("err", new JsonArray(err));

        if (warn.size() > 0)
            builder.append("warn", new JsonArray(warn));

        if (msg != null)
            builder.append("msg", msg);

        builder.append("success", success);
        builder.appendRaw("data", data);

        return builder.toString();
    }
}
