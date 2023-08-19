package com.wavjaby.lib;

public enum ApiCode {
    // Normal 1000-1999
    SUCCESS(1000),
    NORMAL(1999),

    // Server error 2000-2999
    SERVER_NETWORK_ERROR(2000),
    SERVER_DATA_PARSE_ERROR(2001),
    SERVER_DATA_FETCH_ERROR(2002),
    SERVER_DATABASE_ERROR(2003),
    SERVER_ERROR(2999),

    // Client error 3000-3999
    UNSUPPORTED_HTTP_METHOD(3000),
    BAD_QUERY(3001),
    BAD_PAYLOAD(3002),
    COOKIE_ERROR(3003),
    CLIENT_DATA_ERROR(3999),

    // Api error
    LOGIN_REQUIRE(4000),
    COURSE_NCKU_ERROR(4001),
    API_ERROR(4999),


    UNKNOWN(-1);

    public final int code;

    ApiCode(int code) {
        this.code = code;
    }
}
