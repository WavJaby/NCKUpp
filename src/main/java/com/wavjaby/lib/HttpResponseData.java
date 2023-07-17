package com.wavjaby.lib;

public class HttpResponseData {
    public enum ResponseState {
        SUCCESS,
        ROBOT_CODE_CRACK_ERROR,
        NETWORK_ERROR,
    }

    private boolean logErrorTrace;

    public String data;
}
