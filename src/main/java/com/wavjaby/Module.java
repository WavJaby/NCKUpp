package com.wavjaby;

import com.sun.net.httpserver.HttpHandler;

public interface Module {
    void start();

    void stop();

    HttpHandler getHttpHandler();
}
