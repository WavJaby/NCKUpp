package com.wavjaby;

import com.sun.net.httpserver.HttpHandler;

public interface EndpointModule extends Module {
    HttpHandler getHttpHandler();
}
