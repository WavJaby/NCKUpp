package com.wavjaby;

import com.wavjaby.json.JsonObject;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ProxyChecker {
    ProxyChecker() {
        Set<ProxyManager.ProxyData> proxyDataList = new HashSet<>();

        try {
            String proxy = new String(Files.readAllBytes(Paths.get("proxy.txt")));
            for (String s : proxy.split("\r?\n")) {
                if (s.length() == 0)
                    break;
                proxyDataList.add(new ProxyManager.ProxyData(s, "local"));
            }
            System.out.println(proxyDataList.size() + "\tlocalhost");
        } catch (IOException e) {
            e.printStackTrace();
        }

        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newCachedThreadPool();

//        // https://geonode.com/free-proxy-list
//        for (int i = 0; i < 1; i++) {
//            int finalI = i + 1;
//            pool.submit(() -> {
//                try {
//                    String url = "https://proxylist.geonode.com/api/proxy-list?" +
//                            "limit=500&" +
//                            "page=" + finalI + "&" +
//                            "sort_by=responseTime&" +
//                            "sort_type=asc&" +
//                            "protocols=https%2Csocks4%2Csocks5";
//                    String freeProxyListStr = HttpConnection.connect(url)
//                            .ignoreContentType(true)
//                            .timeout(10000)
//                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36")
//                            .execute().body();
//                    JsonObject freeProxyList = new JsonObject(freeProxyListStr);
////            System.out.println(freeProxyList.getInt("total"));
////            System.out.println(freeProxyList.getInt("limit"));
////            System.out.println(freeProxyList.getInt("page"));
//                    JsonArray pageData = freeProxyList.getArray("data");
//                    Set<ProxyManager.ProxyData> newData = new HashSet<>();
//                    for (Object j : pageData) {
//                        JsonObject data = (JsonObject) j;
//                        if (data.getInt("upTime") < 90 ||
//                                data.getInt("latency") > 1000 ||
//                                data.getInt("responseTime") > 1000) continue;
//                        JsonArray protocols = data.getArray("protocols");
//                        newData.add(new ProxyManager.ProxyData(data.getString("ip"), data.getInt("port"), protocols.getString(0), url));
//                    }
//                    proxyDataList.addAll(newData);
//                    System.out.println(newData.size() + "\t" + url);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            });
//        }

        pool.submit(() -> {
            String proxyUrl = "https://www.proxy-list.download/api/v1/get?type=https";
            getTextTypeProxyList(proxyUrl, "https", proxyDataList);
            proxyUrl = "https://www.proxy-list.download/api/v1/get?type=socks4";
            getTextTypeProxyList(proxyUrl, "socks4", proxyDataList);
            try {
                Thread.sleep(6000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            proxyUrl = "https://www.proxy-list.download/api/v1/get?type=socks5";
            getTextTypeProxyList(proxyUrl, "socks5", proxyDataList);
        });

        // https://free-proxy-list.net/
        pool.submit(() -> {
            try {
                String url = "https://www.sslproxies.org/";
                Document freeProxyDoc = HttpConnection.connect(url)
                        .ignoreContentType(true)
                        .execute().parse();
                Element tbody = freeProxyDoc.getElementsByTag("tbody").first();
                if (tbody == null)
                    return;

                Set<ProxyManager.ProxyData> newData = new HashSet<>();
                for (Element tr : tbody.getElementsByTag("tr")) {
                    Elements tds = tr.children();
                    // Check support https
                    if (tds.get(6).text().equalsIgnoreCase("yes"))
                        newData.add(new ProxyManager.ProxyData(tds.get(0).text(), Integer.parseInt(tds.get(1).text()), "https", url));
                }
                proxyDataList.addAll(newData);
                System.out.println(newData.size() + "\t" + url);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        pool.submit(() -> {
            try {
                String url = "https://www.socks-proxy.net/";
                Document freeProxyDoc = HttpConnection.connect(url)
                        .ignoreContentType(true)
                        .execute().parse();
                Element tbody = freeProxyDoc.getElementsByTag("tbody").first();
                if (tbody == null)
                    return;

                Set<ProxyManager.ProxyData> newData = new HashSet<>();
                for (Element tr : tbody.getElementsByTag("tr")) {
                    Elements tds = tr.children();
                    // Check support https
                    if (tds.get(6).text().equalsIgnoreCase("yes"))
                        newData.add(new ProxyManager.ProxyData(tds.get(0).text(), Integer.parseInt(tds.get(1).text()), tds.get(4).text().toLowerCase(), url));
                }
                proxyDataList.addAll(newData);
                System.out.println(newData.size() + "\t" + url);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });


        // https://proxyscrape.com/free-proxy-list-clean-tp
        pool.submit(() -> {
            String proxyUrl = "https://api.proxyscrape.com/v2/?" +
                    "request=displayproxies&" +
                    "protocol=socks4&" +
                    "timeout=700&" +
                    "country=all&" +
                    "simplified=true";
            getTextTypeProxyList(proxyUrl, "socks4", proxyDataList);
        });
        pool.submit(() -> {
            String proxyUrl = "https://api.proxyscrape.com/v2/?" +
                    "request=displayproxies&" +
                    "protocol=socks5&" +
                    "timeout=700&" +
                    "country=all&" +
                    "simplified=true";
            getTextTypeProxyList(proxyUrl, "socks5", proxyDataList);
        });
        pool.submit(() -> {
            String proxyUrl = "https://api.proxyscrape.com/v2/?" +
                    "request=displayproxies&" +
                    "protocol=https&" +
                    "timeout=700&" +
                    "country=all&" +
                    "simplified=true";
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
            String proxyUrl = "https://proxylist.geonode.com/api/proxy-list?&limit=500&page=1&sort_by=lastChecked&sort_type=desc";
            try {
                String proxyGet = HttpConnection.connect(proxyUrl)
                        .ignoreContentType(true)
                        .timeout(30000)
                        .execute().body();
                JsonObject data = new JsonObject(proxyGet);
                Set<ProxyManager.ProxyData> newData = new HashSet<>();
                for (Object i : data.getArray("data")) {
                    JsonObject each = (JsonObject) i;
                    String protocol = null;
                    for (Object j : each.getArray("protocols")) {
                        if (j.equals("socks4") || j.equals("socks5") || j.equals("https"))
                            protocol = (String) j;
                    }
                    if (protocol == null)
                        continue;
                    newData.add(new ProxyManager.ProxyData(each.getString("ip"), Integer.parseInt(each.getString("port")), protocol, proxyUrl));
                }
                proxyDataList.addAll(newData);
                System.out.println(newData.size() + "\t" + proxyUrl);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // https://spys.one/asia-proxy/
        /*
[['xf5',2],['xf1',1],['xpp',5,1]].forEach(i=>{const j=document.getElementById(i[0]);j.value=i[1];i[2]&&j.onchange();});

[['xpp',5],['xf1',1],['xf2',1],['xf5',1,1]].forEach(i=>{const j=document.getElementById(i[0]);j.value=i[1];i[2]&&j.onchange();});


let list = document.querySelectorAll('body > table:nth-child(4) > tbody > tr:nth-child(4) > td > table > tbody > tr');
console.log([...list].slice(3, list.length - 1).map(i=>(i=i.children)&&i[1].innerText.toLowerCase()+'://'+i[0].innerText).join('\n'));

         */
        try {
            String url = "https://spys.one/en/free-proxy-list/";
            File proxyCheckListFile = new File("proxyCheck.txt");
            if (proxyCheckListFile.exists() && proxyCheckListFile.isFile()) {
                String freeProxyStr = new String(Files.readAllBytes(proxyCheckListFile.toPath()));
                Set<ProxyManager.ProxyData> newData = new HashSet<>();
                for (String s : freeProxyStr.split("\n?\n")) {
                    if (s.length() == 0) break;
                    try {
                        newData.add(new ProxyManager.ProxyData(s, url));
                    } catch (Exception e) {
                        System.out.println(s);
                    }
                }
                proxyDataList.addAll(newData);

                FileOutputStream out = new FileOutputStream(proxyCheckListFile);
                StringBuilder builder = new StringBuilder();
                for (ProxyManager.ProxyData data : newData)
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
            if (!pool.awaitTermination(30000, TimeUnit.MILLISECONDS))
                pool.shutdownNow();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

//        try {
//            FileWriter fileWriter = new FileWriter("totalProxy.txt");
//            for (ProxyManager.ProxyData proxyData : proxyDataList) {
//                fileWriter.write(proxyData.toUrl() + '\n');
//            }
//            fileWriter.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        long start = System.currentTimeMillis();
        System.out.println(proxyDataList.size());
        testProxy(proxyDataList, 60, 1000, 2, -1, true);
//        proxyDataList.removeIf(i -> i.ping == -1);
//        testProxy(proxyDataList, 20, 2000);
        proxyDataList.removeIf(i -> i.ping == -1);
        System.out.println("\nDone");
        System.out.println(proxyDataList.size());

        System.out.println("Ping...");
        testProxy(proxyDataList, 2, 2000, -1, 2, false);
        proxyDataList.removeIf(i -> i.ping == -1);
        System.out.println("\nUsed: " + ((System.currentTimeMillis() - start) / 1000) + "s");
        try {
            FileWriter fileWriter = new FileWriter("proxy.txt");
            if (proxyDataList.size() > 0)
                for (ProxyManager.ProxyData proxyData : proxyDataList.stream().sorted((a, b) -> (int) (a.ping - b.ping)).collect(Collectors.toList())) {
                    fileWriter.write(proxyData.toUrl() + '\n');
                    System.out.println(proxyData);
                }
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void testProxy(Set<ProxyManager.ProxyData> proxyDataList, int threadCount, int timeoutTime, int maxTry, int conformTry, boolean errorSameLine) {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount);
        Semaphore fetchPoolLock = new Semaphore(threadCount);
        ThreadPoolExecutor checkConnectionPool = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        Semaphore checkPoolLock = new Semaphore(threadCount * 2);
        CountDownLatch taskLeft = new CountDownLatch(proxyDataList.size());
        final boolean conforming = conformTry != -1;

        for (final ProxyManager.ProxyData proxyData : proxyDataList) {
            String ip = proxyData.ip;
            int port = proxyData.port;
            try {
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
                            ? "https://course.ncku.edu.tw/index.php?c=qry_all"
                            : "https://ifconfig.me/ip";
                    ProxyTestResult result = null;
                    latency = -1;
                    timeout = false;
                    errorMsg = null;
                    Future<ProxyTestResult> future = testProxyConnection(proxyData, testUrl, checkConnectionPool);
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
                    catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                    checkPoolLock.release();
                    if (result != null) {
                        latency = result.latency;
                        errorMsg = result.errorMessage;
                    }
                    if (conforming) {
                        // Conform failed (error or time out)
                        if (timeout || result == null || result.error)
                            break;
                    } else {
                        // Not timeout, pass
                        if (!timeout && result != null && !result.error)
                            break;
                    }
                }
                fetchPoolLock.release();

                try {
                    String progress = strLenLimit(String.format("%.1f%%", (1 - (float) taskLeft.getCount() / proxyDataList.size()) * 100), 6, 6);
                    String left = strLenLimit(String.valueOf(taskLeft.getCount()), 6, 6);
                    String type = strLenLimit(proxyData.protocol, 7, 7);
                    String ipStr = strLenLimit(ip, 16, 16);
                    String portStr = strLenLimit(String.valueOf(port), 8, 8);
                    String messageStr;
                    if (errorMsg != null) {
                        messageStr = "Error: " + errorMsg;
                        proxyData.ping = -1;
                    } else if (timeout) {
                        messageStr = "Time out";
                        proxyData.ping = -1;
                    } else {
                        messageStr = latency + "ms " + proxyData.providerUrl;
                        proxyData.ping = latency;
                    }
                    messageStr = strLenLimit(messageStr, 70, 70);
                    // Proxy pass
                    if (errorMsg == null && !timeout) {
                        System.out.println('\r' + left + strLenLimit(proxyData.toUrl(), 31, 31) + messageStr);
                    }
                    // Progress
                    else {
                        if (errorSameLine) {
                            System.out.print('\r' + progress + left + type + ipStr + portStr + messageStr);
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
            if (!pool.awaitTermination(2000, TimeUnit.MILLISECONDS))
                pool.shutdownNow();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static class ProxyTestResult {
        final String errorMessage;
        final String data;
        final long latency;
        final boolean error;

        private ProxyTestResult(String errorMessage, String data, long latency) {
            this.errorMessage = errorMessage;
            this.data = data;
            this.latency = latency;

            error = errorMessage != null;
        }
    }

    private Future<ProxyTestResult> testProxyConnection(ProxyManager.ProxyData proxyData, String url, ThreadPoolExecutor checkConnectionPool) {
        return checkConnectionPool.submit(() -> {
            long start = System.currentTimeMillis();
            String error = null, data = null;
            try {
                Proxy proxy = proxyData.toProxy();
                Connection conn = HttpConnection.connect(url)
                        .proxy(proxy)
                        .ignoreContentType(true)
                        .header("Connection", "keep-alive");
                Connection.Response response = conn.execute();

                if (response.statusCode() == 200) {
                    data = response.body();
                } else
                    error = "ResponseCode Error\n";
            } catch (Exception e) {
                error = e.getMessage();
                if (error == null)
                    error = "Unknown error";
            }
            return new ProxyTestResult(error, data, System.currentTimeMillis() - start);
        });
    }

    private void getTextTypeProxyList(String url, String protocol, Set<ProxyManager.ProxyData> proxyDataList) {
        try {
            String proxyGet = HttpConnection.connect(url)
                    .ignoreContentType(true)
                    .execute().body();
            Set<ProxyManager.ProxyData> newData = new HashSet<>();
            for (String s : proxyGet.split("\r?\n")) {
                String[] data = s.split(":");
                newData.add(new ProxyManager.ProxyData(data[0], Integer.parseInt(data[1]), protocol, url));
            }
            proxyDataList.addAll(newData);
            System.out.println(newData.size() + "\t" + url);
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

    public static void main(String[] args) {
        new ProxyChecker();
    }
}
