package com.wavjaby;

import com.wavjaby.lib.PropertiesReader;
import com.wavjaby.lib.ThreadFactory;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.wavjaby.lib.Lib.*;

@RequestMapping("/api/v0")
public class ProxyManager implements Module {
    private static final String TAG = "ProxyManager";
    private static final Logger logger = new Logger(TAG);
    private static final int TEST_TIMEOUT = 2000;
    private final PropertiesReader properties;
    private final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(new ThreadFactory(TAG + "-Checker"));
    private final List<ProxyData> proxies = new CopyOnWriteArrayList<>();
    private File proxyFile;
    private long proxyFileLastModified;
    private ProxyData proxy;
    private int proxyIndex;
    private boolean useProxy;

    public static class ProxyData extends ProxyInfo {
        public final String ip;
        public final int port;
        public final String protocol;
        private final Proxy proxy;

        public final String providerUrl;


        public ProxyData(String ip, int port, String protocol, String providerUrl) {
            this.ip = ip;
            this.port = port;
            this.protocol = protocol;
            this.providerUrl = providerUrl;

            this.proxy = new Proxy(getProxyType(), new InetSocketAddress(ip, port));
        }

        public ProxyData(String url, String providerUrl) {
            int protocolEnd = url.indexOf("://");
            this.protocol = url.substring(0, protocolEnd);
            int ipEnd = url.indexOf(':', protocolEnd + 3);
            this.ip = url.substring(protocolEnd + 3, ipEnd);
            int portEnd = ipEnd + 1;
            for (; portEnd < url.length(); portEnd++)
                if (url.charAt(portEnd) < '0' || url.charAt(portEnd) > '9')
                    break;
            this.port = Integer.parseInt(url.substring(ipEnd + 1, portEnd));
            this.providerUrl = providerUrl;

            this.proxy = new Proxy(getProxyType(), new InetSocketAddress(ip, port));
        }

        public String toUrl() {
            return protocol + "://" + ip + ':' + port;
        }

        public String toIp() {
            return ip + ':' + port;
        }

        public Proxy.Type getProxyType() {
            return protocol.startsWith("socks")
                    ? Proxy.Type.SOCKS
                    : protocol.startsWith("http")
                    ? Proxy.Type.HTTP
                    : Proxy.Type.valueOf(protocol.toUpperCase());
        }

        public Proxy toProxy() {
            return this.proxy;
        }

        @Override
        public int hashCode() {
            String[] ips = ip.split("\\.");
            return (Integer.parseInt(ips[0]) << 24) +
                    (Integer.parseInt(ips[1]) << 16) +
                    (Integer.parseInt(ips[2]) << 8) +
                    Integer.parseInt(ips[3]) | port;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (!(obj instanceof ProxyData)) return false;
            return ((ProxyData) obj).protocol.equals(protocol) &&
                    ((ProxyData) obj).ip.equals(ip) &&
                    ((ProxyData) obj).port == port;
        }
    }

    public static abstract class ProxyInfo {
        private int ping = -1;
        private boolean available = false;

        public void setPing(int ping) {
            this.ping = ping;
        }

        public int getPing() {
            return ping;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }

        public boolean isAvailable() {
            return available;
        }

        public boolean isUnavailable() {
            return !available;
        }
    }

    public ProxyManager(PropertiesReader properties) {
        this.properties = properties;
    }

    private final Runnable proxyCheckFunc = () -> {
        updateProxy();
        int newProxyIndex = proxyIndex;
        ProxyData testingProxy = proxies.get(newProxyIndex);
        for (int i = 0; i < proxies.size(); i++) {
            try {
                Connection.Response conn = HttpConnection.connect("https://course.ncku.edu.tw/index.php?c=auth")
                        .timeout(TEST_TIMEOUT)
                        .proxy(testingProxy.toProxy())
                        .ignoreContentType(true)
                        .userAgent(Main.USER_AGENT)
                        .execute();
                if (conn.statusCode() == 200) {
                    String body = conn.body();
                    // Success
                    if (body != null && body.contains("./index.php?c=verifycode")) {
                        // Update proxy
                        if (proxyIndex != newProxyIndex) {
                            proxyIndex = newProxyIndex;
                            proxy = proxies.get(proxyIndex);
                            getUsingProxy();
                        }
                        return;
                    }
                    // Verify code not found
                    else
                        logger.log("Test: " + newProxyIndex + '/' + proxies.size() + ' ' + testingProxy.toUrl() + " verify code not found");
                } else
                    logger.log("Test: " + newProxyIndex + '/' + proxies.size() + ' ' + testingProxy.toUrl() + ' ' +
                            conn.statusCode() + conn.statusMessage());
            } catch (Exception e) {
                String m = e.getMessage();
                if (m.length() > 20)
                    m = m.substring(0, 20) + "...";
                logger.log("Test: " + newProxyIndex + '/' + proxies.size() + ' ' + testingProxy.toUrl() + ' ' + m);
            }
            // Next proxy
            if (++newProxyIndex >= proxies.size())
                newProxyIndex = 0;
            testingProxy = proxies.get(newProxyIndex);
        }
    };

    @Override
    public void start() {
        proxyFile = getFileFromPath("./proxy.txt", false, true);
        useProxy = properties.getPropertyBoolean("useProxy", true);

        if (useProxy) {
            updateProxy();
            getUsingProxy();
            service.scheduleWithFixedDelay(proxyCheckFunc, 0, 5000, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        executorShutdown(service, 5000, "ProxyChecker");
    }

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public boolean api() {
        return false;
    }

    public void updateProxy() {
        // Check last modify
        long lastModified = proxyFile.lastModified();
        if (proxyFileLastModified == lastModified) {
            return;
        }
        proxyFileLastModified = lastModified;

        // Read proxy file
        String proxiesString = readFileToString(proxyFile, false, StandardCharsets.UTF_8);
        if (proxiesString != null) {
            List<ProxyData> oldProxy = new ArrayList<>();
            List<ProxyData> newProxy = new ArrayList<>();
            String[] proxiesStr = proxiesString.split("\r?\n");
            // Read new proxy
            for (String proxyStr : proxiesStr) {
                if (proxyStr.isEmpty())
                    continue;
                ProxyData newProxyData = new ProxyData(proxyStr, "local");
                if (proxies.contains(newProxyData))
                    oldProxy.add(newProxyData);
                else
                    newProxy.add(newProxyData);
            }
            // Proxy file empty
            if (oldProxy.isEmpty() && newProxy.isEmpty()) {
                logger.warn("Proxy file empty");
                return;
            }

            // Update proxy
            proxies.clear();
            proxies.addAll(oldProxy);
            proxies.addAll(newProxy);
        }
        if (!proxies.isEmpty()) {
            proxyIndex = 0;
            proxy = proxies.get(proxyIndex);
        }
    }

    public void nextProxy() {
        if (!useProxy || proxies.isEmpty())
            return;

        if (++proxyIndex >= proxies.size())
            proxyIndex = 0;

        proxy = proxies.get(proxyIndex);
        getUsingProxy();
    }

    public void getUsingProxy() {
        if (!useProxy)
            logger.log("Proxy not enable");
        else if (proxy == null)
            logger.log("No proxy");
        else
            logger.log("Using proxy: " + proxyIndex + '/' + proxies.size() + ' ' + proxy.toUrl());
    }

    public Proxy getProxy() {
        if (proxy == null)
            return null;
        return proxy.toProxy();
    }

    public ProxyData getProxyData() {
        if (proxy == null)
            return null;
        return proxy;
    }

}
