package com.wavjaby.api;

import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("ALL")
public class IP implements EndpointModule {
    private static final String TAG = "[IP]";


    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private final HttpHandler httpHandler = req -> {
        try {
            InetSocketAddress socketAddress = req.getRemoteAddress();
            byte[] dataByte = socketAddress.getHostName().getBytes(StandardCharsets.UTF_8);
            req.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");

            // send response
            req.sendResponseHeaders(200, dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            req.close();
            e.printStackTrace();
        }
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
