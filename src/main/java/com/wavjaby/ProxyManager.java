package com.wavjaby;

import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;

public class ProxyManager {
    private static final String TAG = "[ProxyManager] ";
    private final Proxy proxy;

    public static class ProxyData {
        public final String ip;
        public final int port;
        public final String protocol;
        public long ping;


        public ProxyData(String ip, int port, String protocol) {
            this.ip = ip;
            this.port = port;
            this.protocol = protocol;
            this.ping = -1;
        }

        public ProxyData(String url) {
            int protocolEnd = url.indexOf("://");
            this.protocol = url.substring(0, protocolEnd);
            int ipEnd = url.indexOf(':', protocolEnd + 3);
            this.ip = url.substring(protocolEnd + 3, ipEnd);
            int portEnd = ipEnd + 1;
            for (; portEnd < url.length(); portEnd++)
                if (url.charAt(portEnd) < '0' || url.charAt(portEnd) > '9')
                    break;
            this.port = Integer.parseInt(url.substring(ipEnd + 1, portEnd));
            this.ping = -1;
        }

        @Override
        public String toString() {
            return "ping: " + ping + '\n' +
                    "ip: " + ip + '\n' +
                    "port: " + port;
        }

        public String getUrl() {
            return protocol + "://" + ip + ':' + port;
        }

        public Proxy.Type getProxyType() {
            return protocol.startsWith("socks") ? Proxy.Type.SOCKS : Proxy.Type.valueOf(protocol.toUpperCase());
        }

        @Override
        public int hashCode() {
            String[] ips = ip.split("\\.");
            return (Integer.parseInt(ips[0]) << 23) +
                    (Integer.parseInt(ips[1]) << 15) +
                    (Integer.parseInt(ips[2]) << 7) +
                    (Integer.parseInt(ips[3]) >> 1) +
                    port;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (!(obj instanceof ProxyData)) return false;
            return ((ProxyData) obj).protocol.equals(protocol) &&
                    ((ProxyData) obj).ip.equals(ip) &&
                    ((ProxyData) obj).port == port;
        }

        public Proxy toProxy() {
            return new Proxy(getProxyType(), new InetSocketAddress(ip, port));
        }
    }

    ProxyManager() {
        String proxiesString = null;
        try {
            File proxyTxtFile = new File("proxy.txt");
            if (proxyTxtFile.exists())
                proxiesString = new String(Files.readAllBytes(proxyTxtFile.toPath()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (proxiesString != null) {
            String[] proxies = proxiesString.split("\r?\n");
            ProxyData proxyData = new ProxyData(proxies[0]);
            proxy = proxyData.toProxy();
        } else
            proxy = null;

        if (proxy != null)
            Logger.log(TAG, "Using proxy: " + proxy);
    }

    public Proxy getProxy() {
        return proxy;
    }

}
