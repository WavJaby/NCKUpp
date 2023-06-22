package com.wavjaby;

import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
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

        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(8);

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

        // https://free-proxy-list.net/
        pool.submit(() -> {
            try {
                String url = "https://free-proxy-list.net/";
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
                    if (tds.get(6).text().equals("yes"))
                        newData.add(new ProxyManager.ProxyData(tds.get(0).text(), Integer.parseInt(tds.get(1).text()), "https", url));
                }
                proxyDataList.addAll(newData);
                System.out.println(newData.size() + "\t" + url);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });


        // https://proxyscrape.com/free-proxy-list-clean-tp
        pool.submit(() -> {
            try {
                String proxyScrapeSocks4Url = "https://api.proxyscrape.com/v2/?" +
                        "request=displayproxies&" +
                        "protocol=socks4&" +
                        "timeout=1000&" +
                        "country=all&" +
                        "simplified=true";
                String proxyScrapeSocks4 = HttpConnection.connect(proxyScrapeSocks4Url)
                        .ignoreContentType(true)
                        .execute().body();
                Set<ProxyManager.ProxyData> newData = new HashSet<>();
                for (String s : proxyScrapeSocks4.split("\r?\n")) {
                    String[] data = s.split(":");
                    newData.add(new ProxyManager.ProxyData(data[0], Integer.parseInt(data[1]), "socks4", proxyScrapeSocks4Url));
                }
                proxyDataList.addAll(newData);
                System.out.println(newData.size() + "\t" + proxyScrapeSocks4Url);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        pool.submit(() -> {
            try {
                String proxyScrapeSocks5Url = "https://api.proxyscrape.com/v2/?" +
                        "request=displayproxies&" +
                        "protocol=socks5&" +
                        "timeout=1000&" +
                        "country=all&" +
                        "simplified=true";
                String proxyScrapeSocks5 = HttpConnection.connect(proxyScrapeSocks5Url)
                        .ignoreContentType(true)
                        .execute().body();
                Set<ProxyManager.ProxyData> newData = new HashSet<>();
                for (String s : proxyScrapeSocks5.split("\r?\n")) {
                    String[] data = s.split(":");
                    newData.add(new ProxyManager.ProxyData(data[0], Integer.parseInt(data[1]), "socks5", proxyScrapeSocks5Url));
                }
                proxyDataList.addAll(newData);
                System.out.println(newData.size() + "\t" + proxyScrapeSocks5Url);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // https://spys.one/asia-proxy/
        /*
[['xf5',2],['xf1',1],['xpp',5,1]].forEach(i=>{const j=document.getElementById(i[0]);j.value=i[1];i[2]&&j.onchange();});

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
            if (!pool.awaitTermination(20000, TimeUnit.MILLISECONDS))
                pool.shutdownNow();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long start = System.currentTimeMillis();
        System.out.println(proxyDataList.size());
        testProxy(proxyDataList, 50, 1500, 2, -1, true);
//        proxyDataList.removeIf(i -> i.ping == -1);
//        testProxy(proxyDataList, 20, 2000);
        System.out.println("Done");
        proxyDataList.removeIf(i -> i.ping == -1);
        System.out.println(proxyDataList.size());

        System.out.println("Ping...");
        testProxy(proxyDataList, 1, 2000, -1, 3, false);
        proxyDataList.removeIf(i -> i.ping == -1);
        System.out.println("Used: " + ((System.currentTimeMillis() - start) / 1000) + "s");

        try {
            FileWriter fileWriter = new FileWriter("proxy.txt");
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
        CountDownLatch taskLeft = new CountDownLatch(proxyDataList.size());
        boolean conforming = conformTry != -1;

        for (ProxyManager.ProxyData data : proxyDataList) {
            String ip = data.ip;
            int port = data.port;
            try {
                Thread.sleep(timeoutTime / threadCount);
                fetchPoolLock.acquire();
            } catch (InterruptedException ignored) {
            }

            pool.submit(() -> {
                String error = null;
                String message = null;
                boolean timeout = true;
                long latency = -1;
                int maxTryCount = Math.max(maxTry, conformTry);
                for (int tryCount = 0; tryCount < maxTryCount; tryCount++) {
                    String[] finalError = new String[1];
                    String[] finalMessage = new String[1];
                    Future<Long> future = checkConnectionPool.submit(() -> {
                        long start = System.currentTimeMillis();
                        try {
                            Proxy proxy = data.toProxy();
                            URL url;
                            if (conforming)
                                url = new URL("https://course.ncku.edu.tw/index.php");
                            else
                                url = new URL("https://api.simon.chummydns.com/api/ip");
//                            URL url = new URL("https://ifconfig.me/ip");
                            URLConnection conn = url.openConnection(proxy);
                            conn.setUseCaches(false);
                            conn.setReadTimeout(timeoutTime + 100);
                            conn.setConnectTimeout(timeoutTime + 100);
                            InputStream is = conn.getInputStream();
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            int nRead;
                            byte[] buff = new byte[1024];
                            while ((nRead = is.read(buff, 0, buff.length)) != -1)
                                buffer.write(buff, 0, nRead);
                            is.close();

                            finalMessage[0] = buffer.toString("UTF-8");
                            buffer.close();
                        } catch (IOException e) {
                            finalError[0] = e.getMessage();
//                        e.printStackTrace();
                        }
                        return System.currentTimeMillis() - start;
                    });
                    timeout = false;
                    try {
                        latency = future.get(timeoutTime, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        timeout = true;
                    }
                    error = finalError[0];
                    message = finalMessage[0];
                    // Pass
                    if (!timeout && !conforming) break;
                    // Conform failed (error or time out)
                    if ((error != null || timeout) && conforming) break;
                }

                String left = String.valueOf(taskLeft.getCount());
                left += String.join("", Collections.nCopies(5 - left.length(), " "));
                String type = data.protocol + String.join("", Collections.nCopies(7 - data.protocol.length(), " "));
                String ipStr = ip + String.join("", Collections.nCopies(16 - ip.length(), " "));
                String portStr = String.valueOf(port);
                portStr += String.join("", Collections.nCopies(8 - portStr.length(), " "));
                if (error != null) {
                    if (errorSameLine)
                        System.out.print('\r' + left + type + ipStr + portStr + "Error");
                    else
                        System.out.println(left + type + ipStr + portStr + "Error: " + error);
                    data.ping = -1;
                } else if (timeout) {
                    if (errorSameLine)
                        System.out.print('\r' + left + type + ipStr + portStr + "Time out");
                    else
                        System.out.println(left + type + ipStr + portStr + "Time out");
                    data.ping = -1;
                } else {
                    System.out.println('\r' + left + type + ipStr + portStr + latency + "ms " + data.providerUrl);
                    data.ping = latency;
                }
                taskLeft.countDown();
                fetchPoolLock.release();
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
            if (!pool.awaitTermination(timeoutTime + 100, TimeUnit.MILLISECONDS))
                pool.shutdownNow();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new ProxyChecker();
    }
}
