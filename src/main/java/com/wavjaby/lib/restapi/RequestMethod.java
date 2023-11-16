package com.wavjaby.lib.restapi;

public enum RequestMethod {
    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    CONNECT,
    OPTIONS,
    TRACE,
    UNKNOWN;

    public static RequestMethod getMethod(String method) {
        return method.equalsIgnoreCase("GET") ? GET
                : method.equalsIgnoreCase("POST") ? POST
                : method.equalsIgnoreCase("PUT") ? PUT
                : method.equalsIgnoreCase("PATCH") ? PATCH
                : method.equalsIgnoreCase("DELETE") ? DELETE
                // Other method
                : method.equalsIgnoreCase("HEAD") ? HEAD
                : method.equalsIgnoreCase("CONNECT") ? CONNECT
                : method.equalsIgnoreCase("OPTIONS") ? OPTIONS
                : method.equalsIgnoreCase("TRACE") ? TRACE
                : UNKNOWN;
    }
}
