package com.wavjaby;

import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonObject;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static com.wavjaby.lib.Lib.executorShutdown;
import static com.wavjaby.lib.Lib.readInputStreamToString;

public class ProxyChecker {
    int okCount = 0;
//    Map<String, ProxyManager.ProxyData> duplicateProxy = new ConcurrentHashMap<>();

    ProxyChecker(boolean updateProxyCache, boolean clearProxyCache, boolean ping) {
        long start = System.currentTimeMillis();
        Map<String, ProxyManager.ProxyData> proxyDataList = new ConcurrentHashMap<>();

//        // Read duplicate proxy
//        Map<String, ProxyManager.ProxyData> p = readProxyFile("duplicate_proxy.txt", "local");
//        if (p != null) duplicateProxy.putAll(p);

        // Read proxy cache
        Map<String, ProxyManager.ProxyData> c = readProxyFile("proxy_cache.txt", "local proxy cache");

        if (updateProxyCache) {
            System.out.println("Update proxy cache");
            fetchProxies(false, proxyDataList);
            System.out.println(proxyDataList.size());
            // Filter
            long filter1 = System.currentTimeMillis();
            testProxy(proxyDataList, 300, 800, true, 1, -1, true);
            proxyDataList.values().removeIf(ProxyManager.ProxyInfo::isUnavailable);
            System.out.println(proxyDataList.size());
            System.out.println("Filter use: " + ((System.currentTimeMillis() - filter1) / 1000) + "s");
            dumpProxyFile(proxyDataList, "proxy_cache.txt");
        }

        if (!clearProxyCache) {
            if (c != null)
                for (Map.Entry<String, ProxyManager.ProxyData> i : c.entrySet()) {
                    if (!proxyDataList.containsKey(i.getKey()))
                        proxyDataList.put(i.getKey(), i.getValue());
                }
        }

        // Filter 2
        System.out.println("Filter2 start");
        System.out.println(proxyDataList.size());
        long filter2 = System.currentTimeMillis();
        testProxy(proxyDataList, 250, 700, false, 1, -1, true);
        proxyDataList.values().removeIf(ProxyManager.ProxyInfo::isUnavailable);
        System.out.println("Filter2 use: " + ((System.currentTimeMillis() - filter2) / 1000) + "s");
        System.out.println("Done");


        if (ping) {
            readLocalProxyList(proxyDataList);
            System.out.println(proxyDataList.size());
            // Ping
            System.out.println("Ping...");
            testProxy(proxyDataList, 4, 1000, false, -1, 6, false);
            proxyDataList.values().removeIf(ProxyManager.ProxyInfo::isUnavailable);
            System.out.println("\nAvailable: " + proxyDataList.size());
            System.out.println("Used: " + ((System.currentTimeMillis() - start) / 1000) + "s");
            // Save available proxies
            ArrayList<ProxyManager.ProxyData> sorted = new ArrayList<>(proxyDataList.values());
            sorted.sort(Comparator.comparingInt(ProxyManager.ProxyInfo::getPing));
            dumpProxyFile(sorted, "proxy.txt");
        }

//        // Save duplicate proxy
//        p = readProxyFile("duplicate_proxy.txt", "local");
//        if (p != null) duplicateProxy.putAll(p);
//        dumpProxyFile(duplicateProxy, "duplicate_proxy.txt");
    }

    private String onProxyPass(ProxyManager.ProxyData proxyData, String responseData) {
//        if (responseData.equals("ok")) {
//            duplicateProxy.put(proxyData.toUrl(), proxyData);
//            okCount++;
//        } else if (responseData.equals("Duplicate")) {
//            duplicateProxy.put(proxyData.toUrl(), proxyData);
//        }

        return (++okCount) + " ";
    }

    private String onProxyTestStart() {
        return (okCount = 0) + " ";
    }

    private void fetchProxies(boolean allowHttp, Map<String, ProxyManager.ProxyData> proxyDataList) {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        pool.submit(() -> {
            try {
                getTextTypeProxyList("https://www.proxy-list.download/api/v1/get?type=socks4", "socks4", proxyDataList);
                Thread.sleep(5000);
                getTextTypeProxyList("https://www.proxy-list.download/api/v1/get?type=socks5", "socks5", proxyDataList);
                Thread.sleep(5000);
                getTextTypeProxyList("https://www.proxy-list.download/api/v1/get?type=https", "https", proxyDataList);
                if (allowHttp) {
                    Thread.sleep(5000);
                    getTextTypeProxyList("https://www.proxy-list.download/api/v1/get?type=http", "http", proxyDataList);
                }
            } catch (InterruptedException ignore) {
            }
        });

        pool.submit(() -> getFreeProxy("https://free-proxy-list.net/", allowHttp, proxyDataList));
        pool.submit(() -> getFreeProxy("https://www.sslproxies.org/", allowHttp, proxyDataList));
        pool.submit(() -> getFreeProxy("https://www.socks-proxy.net/", allowHttp, proxyDataList));

        pool.submit(() -> getProxyScrapeProxy("socks4", 9999, proxyDataList));
        pool.submit(() -> getProxyScrapeProxy("socks5", 9999, proxyDataList));
        pool.submit(() -> getProxyScrapeProxy("https", 9999, proxyDataList));
        if (allowHttp)
            pool.submit(() -> getProxyScrapeProxy("http", 9999, proxyDataList));

        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/socks4.txt", "socks4", proxyDataList));
        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/socks5.txt", "socks5", proxyDataList));
        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/https.txt", "https", proxyDataList));
        if (allowHttp)
            pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt", "http", proxyDataList));

        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/zloi-user/hideip.me/main/socks4.txt", "socks4", proxyDataList));
        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/zloi-user/hideip.me/main/socks5.txt", "socks5", proxyDataList));
        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/zloi-user/hideip.me/main/https.txt", "https", proxyDataList));
        if (allowHttp)
            pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/zloi-user/hideip.me/main/http.txt", "http", proxyDataList));

        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/monosans/proxy-list/main/proxies_anonymous/socks4.txt", "socks4", proxyDataList));
        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/monosans/proxy-list/main/proxies_anonymous/socks5.txt", "socks5", proxyDataList));
        if (allowHttp)
            pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/monosans/proxy-list/main/proxies_anonymous/http.txt", "http", proxyDataList));

        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/monosans/proxy-list/main/proxies_geolocation/socks4.txt", "socks4", proxyDataList));
        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/monosans/proxy-list/main/proxies_geolocation/socks5.txt", "socks5", proxyDataList));
        if (allowHttp)
            pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/monosans/proxy-list/main/proxies_geolocation/http.txt", "http", proxyDataList));

        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/socks4.txt", "socks4", proxyDataList));
        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/socks5.txt", "socks5", proxyDataList));
        if (allowHttp)
            pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/http.txt", "http", proxyDataList));

        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/hookzof/socks5_list/master/proxy.txt", "socks5", proxyDataList));

        pool.submit(() -> getTextTypeProxyList("https://openproxylist.xyz/socks4.txt", "socks4", proxyDataList));
        pool.submit(() -> getTextTypeProxyList("https://openproxylist.xyz/socks5.txt", "socks5", proxyDataList));
        if (allowHttp)
            pool.submit(() -> getTextTypeProxyList("https://openproxylist.xyz/http.txt", "http", proxyDataList));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        pool.submit(() -> getCheckerProxy(allowHttp, now.format(DateTimeFormatter.ISO_LOCAL_DATE), proxyDataList));
        pool.submit(() -> getCheckerProxy(allowHttp, now.minus(1, ChronoUnit.DAYS).format(DateTimeFormatter.ISO_LOCAL_DATE), proxyDataList));
        pool.submit(() -> getCheckerProxy(allowHttp, now.minus(2, ChronoUnit.DAYS).format(DateTimeFormatter.ISO_LOCAL_DATE), proxyDataList));

        pool.submit(() -> getGeonodeProxyList("socks4", 3, proxyDataList));
        pool.submit(() -> getGeonodeProxyList("socks5", 3, proxyDataList));
        pool.submit(() -> getGeonodeProxyList("https", 3, proxyDataList));
        if (allowHttp)
            pool.submit(() -> getGeonodeProxyList("http", 3, proxyDataList));


        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/jetkai/proxy-list/main/archive/txt/proxies-socks4.txt", "socks4", proxyDataList));
        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/jetkai/proxy-list/main/archive/txt/proxies-socks5.txt", "socks5", proxyDataList));
        pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/jetkai/proxy-list/main/archive/txt/proxies-https.txt", "https", proxyDataList));
        if (allowHttp)
            pool.submit(() -> getTextTypeProxyList("https://raw.githubusercontent.com/jetkai/proxy-list/main/archive/txt/proxies-http.txt", "http", proxyDataList));

        getSpysOneProxy(allowHttp, proxyDataList);

        executorShutdown(pool, 60000, "ProxyGetter");
    }

    private void testProxy(Map<String, ProxyManager.ProxyData> proxyDataList, int threadCount, int timeoutTime, boolean allowTimeout,
                           int maxTry, int conformTry, boolean errorSameLine) {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount);
        Semaphore fetchPoolLock = new Semaphore(threadCount, true);
        ThreadPoolExecutor checkConnectionPool = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        Semaphore checkConnectionLock = new Semaphore(threadCount * 2, true);
        CountDownLatch taskLeft = new CountDownLatch(proxyDataList.size());
        final boolean conforming = conformTry != -1;

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(onProxyTestStart());

        for (final ProxyManager.ProxyData proxyData : proxyDataList.values()) {
//            if (duplicateProxy.containsKey(proxyData.toUrl())) {
//                taskLeft.countDown();
//                continue;
//            }

            String ip = proxyData.ip;
            int port = proxyData.port;
            try {
                if (fetchPoolLock.availablePermits() > threadCount / 3)
                    Thread.sleep(timeoutTime / threadCount);
                fetchPoolLock.acquire();
            } catch (InterruptedException ignored) {
            }

            pool.submit(() -> {
                long latency = -1;
                boolean timeout = false;
                String errorMsg = null, data = null;
                int maxTryCount = Math.max(maxTry, conformTry);
                for (int tryCount = 0; tryCount < maxTryCount; tryCount++) {
                    String testUrl = conforming
                            ? "https://course.ncku.edu.tw/index.php"
                            : "https://api.simon.chummydns.com/api/ip";
//                            : "https://ifconfig.me/ip";
                    ProxyTestResult result = null;
                    latency = -1;
                    timeout = false;
                    errorMsg = null;
                    Future<ProxyTestResult> future = testProxyConnection(proxyData, testUrl, timeoutTime, checkConnectionPool);
                    try {
                        checkConnectionLock.acquire();
                        result = future.get(timeoutTime, TimeUnit.MILLISECONDS);
                    }
                    // Timeout
                    catch (TimeoutException e) {
                        future.cancel(true);
                        timeout = true;
                    }
                    // Other error
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    checkConnectionLock.release();
                    if (result != null) {
                        latency = result.latency;
                        errorMsg = result.errorMessage;
                        data = result.data;
                    }
                    if (conforming) {
                        // Conform failed (error or time out)
                        if (timeout || result == null || result.haveError())
                            break;
                    } else {
                        // Not timeout, pass
                        if (!timeout && result != null && !result.haveError())
                            break;
                    }
                }
                fetchPoolLock.release();

                try {
                    String progress = strLenLimit(String.format("%.1f%%", (1 - (float) taskLeft.getCount() / proxyDataList.size()) * 100), 6, 6);
                    String left = strLenLimit(String.valueOf(taskLeft.getCount()), 6, 6);
                    String checkCount = strLenLimit(String.valueOf(checkConnectionPool.getActiveCount()), 5, 5);
                    String poolCount = strLenLimit(String.valueOf(pool.getActiveCount()), 5, 5);
                    String type = strLenLimit(proxyData.protocol, 7, 7);
                    String ipStr = strLenLimit(ip, 16, 16);
                    String portStr = strLenLimit(String.valueOf(port), 8, 8);

                    String messageStr;
                    if (errorMsg != null) {
                        messageStr = "Error: " + errorMsg;
                        proxyData.setAvailable(false);
                    } else if (timeout) {
                        messageStr = "Time out";
                        proxyData.setAvailable(allowTimeout);
                    } else {
                        messageStr = latency + "ms  " +
                                (proxyData.providerUrl.startsWith("http")
                                        ? safeSubStr(proxyData.providerUrl, 8)
                                        : proxyData.providerUrl);
                        proxyData.setPing((int) latency);
                        proxyData.setAvailable(true);
                    }
                    String message = strLenLimit(messageBuilder.toString(), 6, 6);
                    // Proxy pass
                    if (errorMsg == null && !timeout) {
                        messageBuilder.setLength(0);
                        messageBuilder.append(onProxyPass(proxyData, data));

                        messageStr = strLenLimit(messageStr, 65, 65);
                        System.out.println('\r' + left + strLenLimit(proxyData.toUrl(), 31, 31) + messageStr);
                    }
                    // Progress
                    else {
                        if (errorSameLine) {
                            messageStr = strLenLimit(messageStr, 30, 30);
                            System.out.print('\r' + progress + left + checkCount + poolCount + message + type + ipStr + portStr + messageStr);
                        } else {
                            messageStr = strLenLimit(messageStr, 55, 55);
                            System.out.println(progress + left + message + type + ipStr + portStr + messageStr);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                taskLeft.countDown();
            });
        }

        try {
            taskLeft.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        executorShutdown(checkConnectionPool, timeoutTime, "CheckConnection");
        executorShutdown(pool, 1000, "CheckTask");

        System.out.print('\n');
    }

    private static class ProxyTestResult {
        final String errorMessage;
        final String data;
        final long latency;

        private ProxyTestResult(String errorMessage, String data, long latency) {
            this.errorMessage = errorMessage;
            this.data = data;
            this.latency = latency;
        }

        public boolean haveError() {
            return errorMessage != null;
        }
    }

    private Future<ProxyTestResult> testProxyConnection(ProxyManager.ProxyData proxyData, String url, int timeoutTime, ThreadPoolExecutor checkConnectionPool) {
        return checkConnectionPool.submit(() -> {
            long start = System.currentTimeMillis();
            String error = null, data = null;
            try {
                Proxy proxy = proxyData.toProxy();
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection(proxy);
                conn.setConnectTimeout(timeoutTime);
                conn.setReadTimeout(timeoutTime);
                conn.setUseCaches(false);
                conn.setRequestProperty("Connection", "close");

                if (conn.getResponseCode() == 200) {
                    data = readInputStreamToString(conn.getInputStream(), StandardCharsets.UTF_8);
                } else
                    error = "ResponseCode Error";
            } catch (Exception e) {
                error = e.getMessage();
                if (error == null)
                    error = "Unknown error";
            }
            return new ProxyTestResult(error, data, System.currentTimeMillis() - start);
        });
    }

    private void dumpProxyFile(Map<String, ProxyManager.ProxyData> proxies, String filePath) {
        dumpProxyFile(new ArrayList<>(proxies.values()), filePath);
    }

    private void dumpProxyFile(ArrayList<ProxyManager.ProxyData> proxies, String filePath) {
        try {
            FileWriter fileWriter = new FileWriter(filePath);
            for (ProxyManager.ProxyData proxyData : proxies) {
                fileWriter.write(proxyData.toUrl() + '\n');
            }
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<String, ProxyManager.ProxyData> readProxyFile(String filePath, String url) {
        File file = new File(filePath);
        try {
            if (file.exists() && file.isFile()) {
                String freeProxyStr = new String(Files.readAllBytes(file.toPath()));
                Map<String, ProxyManager.ProxyData> newData = new HashMap<>();
                for (String s : freeProxyStr.split("\n?\n")) {
                    if (s.length() == 0)
                        continue;
                    try {
                        ProxyManager.ProxyData proxyData = new ProxyManager.ProxyData(s, url);
                        newData.put(proxyData.toUrl(), proxyData);
                    } catch (IllegalArgumentException ignore) {
                    }
                }
                return newData;
            } else
                System.out.println(file.getName() + " not found");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void getTextTypeProxyList(String proxyUrl, String protocol, Map<String, ProxyManager.ProxyData> proxyDataList) {
        Map<String, ProxyManager.ProxyData> newData = new HashMap<>();
        try {
            String proxyGet = HttpConnection.connect(proxyUrl)
                    .ignoreContentType(true)
                    .execute().body();
            for (String s : proxyGet.split("\r?\n")) {
                String[] data = s.split(":");
                ProxyManager.ProxyData proxyData = new ProxyManager.ProxyData(data[0], Integer.parseInt(data[1]), protocol, proxyUrl);
                newData.put(proxyData.toUrl(), proxyData);
            }
        } catch (IOException e) {
            System.err.println("Error\t" + proxyUrl);
        }
        proxyDataList.putAll(newData);
        System.out.println(newData.size() + "\t" + proxyUrl);
    }

    private void getProxyScrapeProxy(String protocol, int timeout, Map<String, ProxyManager.ProxyData> proxyDataList) {
        // https://proxyscrape.com/free-proxy-list-clean-tp
        String proxyUrl = "https://api.proxyscrape.com/v2/?" +
                "protocol=" + protocol + "&" +
                "request=displayproxies&" +
                "timeout=" + timeout;
        getTextTypeProxyList(proxyUrl, "socks4", proxyDataList);
    }

    private void getSpysOneProxy(boolean allowHttp, Map<String, ProxyManager.ProxyData> proxyDataList) {
        // https://spys.one/asia-proxy/
        /*
[['xf5',2],['xf1',1],['xpp',5,1]].forEach(i=>{const j=document.getElementById(i[0]);j.value=i[1];i[2]&&j.onchange();});

[['xpp',5],['xf1',1],['xf2',1],['xf5',1,1]].forEach(i=>{const j=document.getElementById(i[0]);j.value=i[1];i[2]&&j.onchange();});

let list = document.querySelectorAll('body > table:nth-child(3) > tbody > tr:nth-child(4) > td > table > tbody > tr');
if(list.length===0) list = document.querySelectorAll('body > table:nth-child(4) > tbody > tr:nth-child(4) > td > table > tbody > tr');
console.log([...list].slice(3, list.length - 1).map(i=>(i=i.children)&&(i[1].firstElementChild||i[1]).innerText.toLowerCase()+'://'+i[0].innerText).join('\n'));
         */

        String url = "https://spys.one/en/free-proxy-list/";
        File spysOneProxyFile = new File("spys.one_proxy.txt");
        if (spysOneProxyFile.exists() && spysOneProxyFile.isFile()) {
            Map<String, ProxyManager.ProxyData> newData = readProxyFile(spysOneProxyFile.getName(), url);
            if (newData != null) {
                dumpProxyFile(newData, spysOneProxyFile.getName());
                for (Map.Entry<String, ProxyManager.ProxyData> i : newData.entrySet()) {
                    if (i.getValue().protocol.equals("http") && !allowHttp)
                        continue;
                    proxyDataList.put(i.getKey(), i.getValue());
                }
                System.out.println(newData.size() + "\t" + url);
            } else
                System.out.println(spysOneProxyFile.getName() + " empty");
        } else
            System.out.println(spysOneProxyFile.getName() + " not found");
    }

    private void getFreeProxy(String proxyUrl, boolean allowHttp, Map<String, ProxyManager.ProxyData> proxyDataList) {
        Map<String, ProxyManager.ProxyData> newData = new HashMap<>();
        try {
            Document freeProxyDoc = HttpConnection.connect(proxyUrl)
                    .ignoreContentType(true)
                    .get();
            Element tbody = freeProxyDoc.getElementsByTag("tbody").first();
            if (tbody == null) {
                System.err.println("Error\t" + proxyUrl);
                return;
            }

            for (Element tr : tbody.getElementsByTag("tr")) {
                Elements tds = tr.children();
                // Check support https
                if (tds.get(6).text().equalsIgnoreCase("yes") || allowHttp) {
                    ProxyManager.ProxyData proxyData = new ProxyManager.ProxyData(tds.get(0).text(), Integer.parseInt(tds.get(1).text()), "https", proxyUrl);
                    newData.put(proxyData.toUrl(), proxyData);
                }
            }
        } catch (IOException e) {
            System.err.println("Error\t" + proxyUrl);
        }
        proxyDataList.putAll(newData);
        System.out.println(newData.size() + "\t" + proxyUrl);
    }

    private void getGeonodeProxyList(String protocol, int maxPage, Map<String, ProxyManager.ProxyData> proxyDataList) {
        String proxyUrlPrefix = "https://proxylist.geonode.com/api/proxy-list?protocols=" + protocol;
        Map<String, ProxyManager.ProxyData> newData = new HashMap<>();
        for (int page = 1; page <= maxPage; page++) {
            String proxyUrl = proxyUrlPrefix + "&sort_by=responseTime&sort_type=asc&limit=500&page=" + page;
            JsonObject data;
            try {
                String proxyGet = HttpConnection.connect(proxyUrl)
                        .ignoreContentType(true)
                        .timeout(30000)
                        .execute().body();
                data = new JsonObject(proxyGet);
            } catch (IOException e) {
                System.err.println("Error\t" + proxyUrl);
                return;
            }
            for (Object i : data.getArray("data")) {
                JsonObject each = (JsonObject) i;
                String proxyProtocol = each.getArray("protocols").getString(0);
                int proxyPort = Integer.parseInt(each.getString("port"));
                ProxyManager.ProxyData proxyData = new ProxyManager.ProxyData(each.getString("ip"), proxyPort, proxyProtocol, proxyUrl);
                newData.put(proxyData.toUrl(), proxyData);
            }
            if (page * 500 > data.getInt("total"))
                break;
        }
        System.out.println(newData.size() + "\t" + proxyUrlPrefix);
        proxyDataList.putAll(newData);
    }

    private void getCheckerProxy(boolean allowHttp, String date, Map<String, ProxyManager.ProxyData> proxyDataList) {
        final String[] protocols = {"http", "https", "socks4", "socks5"};
        String proxyUrl = "https://checkerproxy.net/api/archive/" + date;
        JsonArray data;
        try {
            String proxyGet = HttpConnection.connect(proxyUrl)
                    .ignoreContentType(true)
                    .timeout(30000)
                    .execute().body();
            data = new JsonArray(proxyGet);
        } catch (IOException e) {
            System.err.println("Error\t" + proxyUrl);
            return;
        }

        Map<String, ProxyManager.ProxyData> newData = new HashMap<>();
        for (Object i : data) {
            JsonObject proxy = (JsonObject) i;
            String addr = proxy.getString("addr");
            int index = addr.indexOf(':');
            String ip = addr.substring(0, index);
            int port = Integer.parseInt(addr.substring(index + 1));
            int typeInt = proxy.getInt("type") - 1;
            // Skip http
            if (typeInt == 0 && !allowHttp)
                continue;
            String protocol = protocols[typeInt];
            ProxyManager.ProxyData proxyData = new ProxyManager.ProxyData(ip, port, protocol, proxyUrl);
            newData.put(proxyData.toUrl(), proxyData);
        }
        System.out.println(newData.size() + "\t" + proxyUrl);
        for (Map.Entry<String, ProxyManager.ProxyData> i : newData.entrySet()) {
            if (!proxyDataList.containsKey(i.getKey()))
                proxyDataList.putAll(newData);
        }
    }

    private void readLocalProxyList(Map<String, ProxyManager.ProxyData> proxyDataList) {
        try {
            String proxy = new String(Files.readAllBytes(Paths.get("proxy.txt")));
            Map<String, ProxyManager.ProxyData> newData = new HashMap<>();
            for (String s : proxy.split("\r?\n")) {
                if (s.length() == 0)
                    break;
                ProxyManager.ProxyData proxyData = new ProxyManager.ProxyData(s, "local");
                newData.put(proxyData.toUrl(), proxyData);
            }
            proxyDataList.putAll(newData);
            System.out.println(newData.size() + "\tlocal");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String strLenLimit(String str, int min, int max) {
        if (str.length() < min)
            return str + String.join("", Collections.nCopies(min - str.length(), " "));
        else if (max > 3 && str.length() > max)
            return str.substring(0, max - 3) + "...";
        return str;
    }

    private String safeSubStr(String str, int beginIndex) {
        return str.length() > beginIndex ? str.substring(beginIndex) : str;
    }

    public static void main(String[] args) {
        if (args.length == 1)
            while (true) {
                new ProxyChecker(false, false, true);
            }
        else
            while (true) {
                new ProxyChecker(true, true, true);
            }
    }
}
