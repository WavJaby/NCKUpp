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

    public HttpResponseData(ResponseState state, String data) {
        this.state = state;
        this.data = data;
    }

    public HttpResponseData(ResponseState errorState) {
        this.state = errorState;
        this.data = null;
    }

    public boolean isSuccess() {
        return this.state == ResponseState.SUCCESS && this.data != null;
    }
}
