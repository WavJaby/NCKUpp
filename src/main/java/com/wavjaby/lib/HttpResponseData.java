package com.wavjaby.lib;

public class HttpResponseData {

    public enum ResponseState {
        SUCCESS,
        ROBOT_CODE_CRACK_ERROR,
        DATA_PARSE_ERROR,
        NETWORK_ERROR,
    }

    public final ResponseState state;

    public final String data;
    public final String baseUrl;

    public HttpResponseData(ResponseState state, String data, String baseUrl) {
        this.state = state;
        this.data = data;
        this.baseUrl = baseUrl;
    }

    public HttpResponseData(ResponseState errorState) {
        this.state = errorState;
        this.data = null;
        this.baseUrl = null;
    }

    public boolean isSuccess() {
        return this.state == ResponseState.SUCCESS && this.data != null;
    }
}
