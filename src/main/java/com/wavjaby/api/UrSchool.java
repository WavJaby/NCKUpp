package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.json.JsonBuilder;
import com.wavjaby.logger.Logger;
import com.wavjaby.logger.ProgressBar;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.CookieManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.wavjaby.Cookie.getDefaultCookie;
import static com.wavjaby.Lib.setAllowOrigin;
import static com.wavjaby.Main.pool;

public class UrSchool implements HttpHandler {
    private static final String TAG = "[UrSchool] ";

    private String UrSchoolData;
    private final long updateInterval = 10 * 60 * 1000;
    private long lastUpdateTime;

    public UrSchool() {
        File file = new File("./urschool.json");
        if (file.exists()) {
            try {
                InputStream reader = Files.newInputStream(file.toPath());
                ByteArrayOutputStream out = new ByteArrayOutputStream();

                int len;
                byte[] buff = new byte[1024];
                while ((len = reader.read(buff)) != -1)
                    out.write(buff, 0, len);

                UrSchoolData = out.toString("UTF-8");
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (System.currentTimeMillis() - file.lastModified() < updateInterval) {
                Logger.log(TAG, "Up to date");
                return;
            }
        }
        updateUrSchoolData();
    }

    @Override
    public void handle(HttpExchange req) {
        pool.submit(() -> {
            long startTime = System.currentTimeMillis();
            CookieManager cookieManager = new CookieManager();
            Headers requestHeaders = req.getRequestHeaders();
            getDefaultCookie(requestHeaders, cookieManager);

            try {
                JsonBuilder data = new JsonBuilder();

                boolean success = true;
                if (System.currentTimeMillis() - lastUpdateTime > updateInterval)
                    updateUrSchoolData();
                data.append("data", UrSchoolData, true);


                Headers responseHeader = req.getResponseHeaders();
                byte[] dataByte = data.toString().getBytes(StandardCharsets.UTF_8);
                responseHeader.set("Content-Type", "application/json; charset=UTF-8");

                // send response
                setAllowOrigin(requestHeaders, responseHeader);
                req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
                OutputStream response = req.getResponseBody();
                response.write(dataByte);
                response.flush();
                req.close();
            } catch (IOException e) {
                req.close();
            }
            Logger.log(TAG, "Get UrSchool " + (System.currentTimeMillis() - startTime) + "ms");
        });
    }

    @SuppressWarnings("busy-waiting")
    private void updateUrSchoolData() {
        pool.submit(() -> {
            long start = System.currentTimeMillis();
            lastUpdateTime = start;

            int[] maxPage = new int[1];
            StringBuilder result = new StringBuilder();

            // Get first page
            ProgressBar progressBar = new ProgressBar(TAG + "Update data ");
            Logger.addProgressBar(progressBar);
            progressBar.setProgress(0f);
            String firstPage = fetchUrSchoolData(1, maxPage);
            progressBar.setProgress((float) 1 / maxPage[0] * 100);
            if (firstPage == null)
                return;
            result.append(firstPage);

            // Get the rest of the page
            ExecutorService readPool = Executors.newFixedThreadPool(16);
            CountDownLatch count = new CountDownLatch(maxPage[0] - 1);
            for (int i = 1; i < maxPage[0]; i++) {
                int finalI = i + 1;
                readPool.submit(() -> {
                    String page = fetchUrSchoolData(finalI, null);
                    result.append(page);
                    progressBar.setProgress(((float) (maxPage[0] - count.getCount() + 1) / maxPage[0]) * 100);
                    count.countDown();
                });
            }
            // Wait result
            try {
                count.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
            progressBar.setProgress(100f);
            Logger.removeProgressBar(progressBar);
            if (result.length() > 0) result.setCharAt(0, '[');
            else result.append('[');
            result.append(']');
            String resultString = result.toString();
            UrSchoolData = resultString;
            try {
                File file = new File("./urschool.json");
                FileWriter fileWriter = new FileWriter(file);
                fileWriter.write(resultString);
                fileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Logger.log(TAG, "Used " + (System.currentTimeMillis() - start) + "ms");
        });
    }

    private List<String[]> fetchFreeProxy() {
        try {
            List<String[]> proxy = new ArrayList<>();
            Connection.Response result = HttpConnection.connect("https://free-proxy-list.net/")
                    .ignoreContentType(true)
                    .execute();
            String resultBody = result.body();
            int resultTableStart;
            if ((resultTableStart = resultBody.indexOf("<table")) == -1) {
                Logger.log(TAG, "result table not found");
                return null;
            }
            // get table body
            int resultTableBodyStart, resultTableBodyEnd;
            if ((resultTableBodyStart = resultBody.indexOf("<tbody", resultTableStart + 7)) == -1 ||
                    (resultTableBodyEnd = resultBody.indexOf("</tbody>", resultTableBodyStart + 6)) == -1
            ) {
                Logger.log(TAG, "result table body not found");
                return null;
            }

            // parse table
            String bodyStr = resultBody.substring(resultTableBodyStart, resultTableBodyEnd + 8);
            Node tbody = Parser.parseFragment(bodyStr, new Element("tbody"), "").get(0);

            for (Element i : ((Element) tbody).getElementsByTag("tr")) {
                Elements elements = i.getElementsByTag("td");
                String[] data = elements.stream().map(Element::text).toArray(String[]::new);
                proxy.add(data);
            }
            return proxy;
        } catch (IOException e) {
            return null;
        }
    }

    private String fetchUrSchoolData(int page, int[] maxPage) {
        try {
            String resultBody;
            while (true) {
                try {
                    Connection.Response result = HttpConnection.connect("https://urschool.org/ncku/list?page=" + page)
                            .ignoreContentType(true)
                            .timeout(10 * 1000)
                            .execute();
                    resultBody = result.body();
                    break;
                } catch (IOException ignore) {
                }
            }

            if (maxPage != null) {
                int maxPageStart, maxPageEnd;
                if ((maxPageStart = resultBody.lastIndexOf("https://urschool.org/ncku/list?page=")) == -1 ||
                        (maxPageStart = resultBody.lastIndexOf("https://urschool.org/ncku/list?page=", maxPageStart - 36)) == -1 ||
                        (maxPageEnd = resultBody.indexOf('"', maxPageStart + 36)) == -1) {
                    Logger.log(TAG, "max page not found");
                    return null;
                }
                maxPage[0] = Integer.parseInt(resultBody.substring(maxPageStart + 36, maxPageEnd));
            }

            int resultTableStart;
            if ((resultTableStart = resultBody.indexOf("<table")) == -1) {
                Logger.log(TAG, "result table not found");
                return null;
            }
            // get table body
            int resultTableBodyStart, resultTableBodyEnd;
            if ((resultTableBodyStart = resultBody.indexOf("<tbody", resultTableStart + 7)) == -1 ||
                    (resultTableBodyEnd = resultBody.indexOf("</tbody>", resultTableBodyStart + 6)) == -1
            ) {
                Logger.log(TAG, "result table body not found");
                return null;
            }

            // parse table
            String bodyStr = resultBody.substring(resultTableBodyStart, resultTableBodyEnd + 8);
            Node tbody = Parser.parseFragment(bodyStr, new Element("tbody"), "").get(0);

            StringBuilder out = new StringBuilder();
            for (Element i : ((Element) tbody).getElementsByTag("tr")) {
                String id = i.attr("onclick");
                int idStart, idEnd;
                if ((idStart = id.indexOf('\'')) == -1 ||
                        (idEnd = id.indexOf('\'', idStart + 1)) == -1
                ) {
                    Logger.log(TAG, "id not found");
                    return null;
                }
                int modeStart, modeEnd;
                if ((modeStart = id.indexOf('\'', idEnd + 1)) == -1 ||
                        (modeEnd = id.indexOf('\'', modeStart + 1)) == -1
                ) {
                    Logger.log(TAG, "id not found");
                    return null;
                }
                String mode = id.substring(modeStart + 1, modeEnd);
                id = id.substring(idStart + 1, idEnd);

                StringBuilder builder = new StringBuilder();
                builder.append(',').append('"').append(id).append('"');
                builder.append(',').append('"').append(mode).append('"');

                Elements elements = i.getElementsByTag("td");
                for (int j = 0; j < elements.size(); j++) {
                    Element element = elements.get(j);
                    String text = element.text();
                    if (j > 2 && j < 8) {
                        int end = text.lastIndexOf(' ');
                        if (end != -1)
                            text = text.substring(0, end);
                        else
                            text = "-1";
                        builder.append(',').append(text);
                    } else
                        builder.append(',').append('"').append(text.replace("\\", "\\\\")).append('"');
                }
                builder.setCharAt(0, '[');
                builder.append(']');
                out.append(',').append(builder);
            }
            return out.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
