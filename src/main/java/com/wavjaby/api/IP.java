package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.logger.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;

@SuppressWarnings("ALL")
public class IP implements EndpointModule {
    private static final String TAG = "[IP]";
    private static final Logger logger = new Logger(TAG);

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
        long startTime = System.currentTimeMillis();
        String remoteIp;
        try {
            Headers headers = req.getRequestHeaders();
            String remoteIps = headers.getFirst("X-forwarded-for");
            if (remoteIps == null) {
                InetSocketAddress socketAddress = req.getRemoteAddress();
                InetAddress inaddr = socketAddress.getAddress();
                remoteIp = inaddr.getHostAddress();
            } else {
                remoteIp = remoteIps.substring(remoteIps.lastIndexOf(',') + 1);
            }
            byte[] dataByte = remoteIp.getBytes();

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
            remoteIp = null;
        }
        logger.log(remoteIp + " " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}
