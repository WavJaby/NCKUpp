package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Module;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.logger.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;

@RequestMapping("/api/v0")
public class IP implements Module {
    private static final String TAG = "IP";
    private static final Logger logger = new Logger(TAG);

    public static class IpInfo {
        public final String ip;

        private IpInfo(String ip) {
            this.ip = ip;
        }
    }

    @Override
    public String getTag() {
        return TAG;
    }

    @SuppressWarnings("unused")
    @RequestMapping("/ip")
    public IpInfo ip(HttpExchange req) {
        String ip = getClientIP(req);
        return new IpInfo(ip);
    }

    public static String getClientIP(HttpExchange req) {
        Headers headers = req.getRequestHeaders();
        String remoteIps = headers.getFirst("X-forwarded-for");
        if (remoteIps == null) {
            InetSocketAddress socketAddress = req.getRemoteAddress();
            InetAddress inaddr = socketAddress.getAddress();
            return inaddr.getHostAddress();
        } else {
            return remoteIps.substring(remoteIps.lastIndexOf(',') + 1);
        }
    }
}
