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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

public class ProxyChecker {
    ProxyChecker() {
        Map<String, ProxyManager.ProxyData> proxyDataList = new ConcurrentHashMap<>();

        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        pool.submit(() -> {
            String proxyUrl = "https://www.proxy-list.download/api/v1/get?type=https";
            getTextTypeProxyList(proxyUrl, "https", proxyDataList);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            proxyUrl = "https://www.proxy-list.download/api/v1/get?type=socks4";
            getTextTypeProxyList(proxyUrl, "socks4", proxyDataList);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            proxyUrl = "https://www.proxy-list.download/api/v1/get?type=socks5";
            getTextTypeProxyList(proxyUrl, "socks5", proxyDataList);
        });

        pool.submit(() -> getFreeProxy("https://www.sslproxies.org/", proxyDataList));
        pool.submit(() -> getFreeProxy("https://www.socks-proxy.net/", proxyDataList));

        // https://proxyscrape.com/free-proxy-list-clean-tp
        pool.submit(() -> {
            String proxyUrl = "https://api.proxyscrape.com/v2/?" +
                    "protocol=socks4&" +
                    "request=displayproxies&" +
                    "timeout=700";
            getTextTypeProxyList(proxyUrl, "socks4", proxyDataList);
        });
        pool.submit(() -> {
            String proxyUrl = "https://api.proxyscrape.com/v2/?" +
                    "protocol=socks5&" +
                    "request=displayproxies&" +
                    "timeout=700";
            getTextTypeProxyList(proxyUrl, "socks5", proxyDataList);
        });
        pool.submit(() -> {
            String proxyUrl = "https://api.proxyscrape.com/v2/?" +
                    "protocol=https&" +
                    "request=displayproxies&" +
                    "timeout=700";
            getTextTypeProxyList(proxyUrl, "https", proxyDataList);
        });

        pool.submit(() -> {
            String proxyUrl = "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/socks4.txt";
            getTextTypeProxyList(proxyUrl, "socks4", proxyDataList);
        });
        pool.submit(() -> {
            String proxyUrl = "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/socks5.txt";
            getTextTypeProxyList(proxyUrl, "socks5", proxyDataList);
        });
        pool.submit(() -> {
            String proxyUrl = "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/https.txt";
            getTextTypeProxyList(proxyUrl, "https", proxyDataList);
        });

        pool.submit(() -> {
            String proxyUrl = "https://raw.githubusercontent.com/zloi-user/hideip.me/main/socks4.txt";
            getTextTypeProxyList(proxyUrl, "socks4", proxyDataList);
        });
        pool.submit(() -> {
            String proxyUrl = "https://raw.githubusercontent.com/zloi-user/hideip.me/main/socks5.txt";
            getTextTypeProxyList(proxyUrl, "socks5", proxyDataList);
        });
        pool.submit(() -> {
            String proxyUrl = "https://raw.githubusercontent.com/zloi-user/hideip.me/main/https.txt";
            getTextTypeProxyList(proxyUrl, "https", proxyDataList);
        });

        pool.submit(() -> {
            String proxyUrl = "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies_anonymous/socks4.txt";
            getTextTypeProxyList(proxyUrl, "socks4", proxyDataList);
        });
        pool.submit(() -> {
            String proxyUrl = "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies_anonymous/socks5.txt";
            getTextTypeProxyList(proxyUrl, "socks5", proxyDataList);
        });

        pool.submit(() -> {
            String proxyUrl = "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/socks4.txt";
            getTextTypeProxyList(proxyUrl, "socks4", proxyDataList);
        });
        pool.submit(() -> {
            String proxyUrl = "https://raw.githubusercontent.com/TheSpeedX/SOCKS-List/master/socks5.txt";
            getTextTypeProxyList(proxyUrl, "socks5", proxyDataList);
        });

        pool.submit(() -> {
            String proxyUrl = "https://raw.githubusercontent.com/hookzof/socks5_list/master/proxy.txt";
            getTextTypeProxyList(proxyUrl, "socks5", proxyDataList);
        });

        pool.submit(() -> {
            String proxyUrl = "https://openproxylist.xyz/socks4.txt";
            getTextTypeProxyList(proxyUrl, "socks4", proxyDataList);
        });
        pool.submit(() -> {
            String proxyUrl = "https://openproxylist.xyz/socks5.txt";
            getTextTypeProxyList(proxyUrl, "socks5", proxyDataList);
        });

        pool.submit(() -> getCheckerProxy(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE), proxyDataList));
        pool.submit(() -> getCheckerProxy(OffsetDateTime.now(ZoneOffset.UTC).minus(24, ChronoUnit.HOURS).format(DateTimeFormatter.ISO_DATE), proxyDataList));

        pool.submit(() -> getGeonodeProxyList("https", 3, proxyDataList));
        pool.submit(() -> getGeonodeProxyList("socks5", 3, proxyDataList));
        pool.submit(() -> getGeonodeProxyList("socks4", 3, proxyDataList));

        // https://spys.one/asia-proxy/
        /*
[['xf5',2],['xf1',1],['xpp',5,1]].forEach(i=>{const j=document.getElementById(i[0]);j.value=i[1];i[2]&&j.onchange();});

[['xpp',5],['xf1',1],['xf2',1],['xf5',1,1]].forEach(i=>{const j=document.getElementById(i[0]);j.value=i[1];i[2]&&j.onchange();});

let list = document.querySelectorAll('body > table:nth-child(4) > tbody > tr:nth-child(4) > td > table > tbody > tr');
console.log([...list].slice(3, list.length - 1).map(i=>(i=i.children)&&i[1].firstChild.innerText.toLowerCase()+'://'+i[0].innerText).join('\n'));

         */
        try {
            String url = "https://spys.one/en/free-proxy-list/";
            File proxyCheckListFile = new File("proxyCheck.txt");
            if (proxyCheckListFile.exists() && proxyCheckListFile.isFile()) {
                String freeProxyStr = new String(Files.readAllBytes(proxyCheckListFile.toPath()));
                Map<String, ProxyManager.ProxyData> newData = new HashMap<>();
                for (String s : freeProxyStr.split("\n?\n")) {
                    if (s.length() == 0) break;
                    ProxyManager.ProxyData proxyData = new ProxyManager.ProxyData(s, url);
                    newData.put(proxyData.toUrl(), proxyData);
                }
                proxyDataList.putAll(newData);

                FileOutputStream out = new FileOutputStream(proxyCheckListFile);
                StringBuilder builder = new StringBuilder();
                for (ProxyManager.ProxyData data : newData.values())
                    builder.append(data.toUrl()).append('\n');
                out.write(builder.toString().getBytes());
                out.close();

                System.out.println(newData.size() + "\t" + url);
            } else
                System.out.println("ProxyCheck file not found");

        } catch (IOException e) {
            e.printStackTrace();
        }

        pool.shutdown();
        try {
            if (!pool.awaitTermination(60000, TimeUnit.MILLISECONDS))
                pool.shutdownNow();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long start = System.currentTimeMillis();
        System.out.println(proxyDataList.size());
        testProxy(proxyDataList, 300, 700, true, 1, -1, true);
        proxyDataList.values().removeIf(ProxyManager.ProxyInfo::isUnavailable);
        System.out.println(proxyDataList.size());
        System.out.println("Filter use: " + ((System.currentTimeMillis() - start) / 1000) + "s");

        long start1 = System.currentTimeMillis();
        testProxy(proxyDataList, 150, 700, false, 1, -1, true);
        proxyDataList.values().removeIf(ProxyManager.ProxyInfo::isUnavailable);
        System.out.println("Filter2 use: " + ((System.currentTimeMillis() - start1) / 1000) + "s");
        System.out.println("Done");
        readLocalProxyList(proxyDataList);
        System.out.println(proxyDataList.size());

        System.out.println("Ping...");
        testProxy(proxyDataList, 4, 1000, false, -1, 6, false);
        proxyDataList.values().removeIf(ProxyManager.ProxyInfo::isUnavailable);
        System.out.println("\nAvailable: " + proxyDataList.size());
        System.out.println("Used: " + ((System.currentTimeMillis() - start) / 1000) + "s");
        try {
            if (proxyDataList.size() > 0) {
                FileWriter fileWriter = new FileWriter("proxy.txt");
                ArrayList<ProxyManager.ProxyData> sorted = new ArrayList<>(proxyDataList.values());
                sorted.sort(Comparator.comparingInt(ProxyManager.ProxyInfo::getPing));
                for (ProxyManager.ProxyData proxyData : sorted) {
                    fileWriter.write(proxyData.toUrl() + '\n');
                }
                fileWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void testProxy(Map<String, ProxyManager.ProxyData> proxyDataList, int threadCount, int timeoutTime, boolean timeoutAvailable,
                           int maxTry, int conformTry, boolean errorSameLine) {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount);
        Semaphore fetchPoolLock = new Semaphore(threadCount, true);
        ThreadPoolExecutor checkConnectionPool = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        Semaphore checkPoolLock = new Semaphore(threadCount * 2, true);
        CountDownLatch taskLeft = new CountDownLatch(proxyDataList.size());
        final boolean conforming = conformTry != -1;

        for (final ProxyManager.ProxyData proxyData : proxyDataList.values()) {
            String ip = proxyData.ip;
            int port = proxyData.port;
            try {
                if (fetchPoolLock.availablePermits() > 10)
                    Thread.sleep(timeoutTime / threadCount);
                fetchPoolLock.acquire();
            } catch (InterruptedException ignored) {
            }

            pool.submit(() -> {
                long latency = -1;
                boolean timeout = false;
                String errorMsg = null;
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
                        checkPoolLock.acquire();
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
                    checkPoolLock.release();
                    if (result != null) {
                        latency = result.latency;
                        errorMsg = result.errorMessage;
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
                    String type = strLenLimit(proxyData.protocol, 7, 7);
                    String ipStr = strLenLimit(ip, 16, 16);
                    String portStr = strLenLimit(String.valueOf(port), 8, 8);
                    String messageStr;
                    if (errorMsg != null) {
                        messageStr = "Error: " + errorMsg;
                        proxyData.setAvailable(false);
                    } else if (timeout) {
                        messageStr = "Time out";
                        proxyData.setAvailable(timeoutAvailable);
                    } else {
                        messageStr = latency + "ms " + safeSubStr(proxyData.providerUrl, 8);
                        proxyData.setPing((int) latency);
                        proxyData.setAvailable(true);
                    }
                    // Proxy pass
                    messageStr = strLenLimit(messageStr, 50, 50);
                    if (errorMsg == null && !timeout) {
                        System.out.print('\r' + left + strLenLimit(proxyData.toUrl(), 31, 31) + messageStr + '\n');
                    }
                    // Progress
                    else {
                        if (errorSameLine) {
                            System.out.print('\r' + progress + left + checkCount + type + ipStr + portStr + messageStr);
                        } else
                            System.out.println(left + type + ipStr + portStr + messageStr);
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

        checkConnectionPool.shutdown();
        pool.shutdown();

        try {
            if (!checkConnectionPool.awaitTermination(500, TimeUnit.MILLISECONDS))
                checkConnectionPool.shutdownNow();
            if (!pool.awaitTermination(1000, TimeUnit.MILLISECONDS))
                pool.shutdownNow();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
                    InputStream in = conn.getInputStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    int len;
                    byte[] buff = new byte[1024];
                    while ((len = in.read(buff, 0, buff.length)) > 0)
                        out.write(buff, 0, len);

                    data = out.toString("UTF-8");
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

    private void getFreeProxy(String proxyUrl, Map<String, ProxyManager.ProxyData> proxyDataList) {
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
                if (tds.get(6).text().equalsIgnoreCase("yes")) {
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

    private void getCheckerProxy(String date, Map<String, ProxyManager.ProxyData> proxyDataList) {
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
            if (typeInt == 0)
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
        new ProxyChecker();
    }
}
