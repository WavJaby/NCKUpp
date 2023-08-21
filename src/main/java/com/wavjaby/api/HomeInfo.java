package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;
import static com.wavjaby.lib.Lib.setAllowOrigin;

public class HomeInfo implements EndpointModule {
    private static final String TAG = "[HomeInfo]";
    private static final Logger logger = new Logger(TAG);
    private final ProxyManager proxyManager;

    public HomeInfo(ProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }

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
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        String loginState = getDefaultCookie(requestHeaders, cookieStore);

        try {
            ApiResponse apiResponse = new ApiResponse();

            getHomepageInfo(cookieStore, apiResponse);

            Headers responseHeader = req.getResponseHeaders();
            packCourseLoginStateCookie(responseHeader, loginState, cookieStore);
            byte[] dataByte = apiResponse.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(apiResponse.getResponseCode(), dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            logger.errTrace(e);
            req.close();
        }
        logger.log("Get homeInfo " + (System.currentTimeMillis() - startTime) + "ms");
    };

    private void getHomepageInfo(CookieStore cookieStore, ApiResponse response) {
        Connection request = HttpConnection.connect(courseNckuOrg + "/index.php")
                .header("Connection", "keep-alive")
                .cookieStore(cookieStore)
                .proxy(proxyManager.getProxy());
        Document document;
        try {
            document = request.get();
        } catch (IOException e) {
            logger.errTrace(e);
            response.errorNetwork(e);
            return;
        }
        Element body = document.body();
        JsonObjectStringBuilder data = new JsonObjectStringBuilder();

        // Get mews table
        Element newsTable = body.getElementsByTag("table").first();
        if (newsTable == null) {
            response.errorParse("Can not get news table");
            return;
        }
        Element newsTbody = newsTable.getElementsByTag("tbody").first();
        if (newsTbody == null) {
            response.errorParse("Can not get news tbody");
            return;
        }
        Elements newsRows = newsTbody.children();
        JsonArrayStringBuilder news = new JsonArrayStringBuilder();
        for (int i = 1; i < newsRows.size(); i++) {
            Elements cols = newsRows.get(i).children();
            if (cols.size() < 3) {
                response.errorParse("Can not parse news rows");
                return;
            }
            Element contentsElements = cols.get(1).firstElementChild();
            if (contentsElements == null) {
                response.errorParse("Can not parse news content");
                return;
            }
            // Get contents
            JsonArrayStringBuilder contents = new JsonArrayStringBuilder();
            for (Element phraseElement : contentsElements.children()) {
                StringBuilder contentText = new StringBuilder();
                // content
                for (Node node : phraseElement.childNodes()) {
                    if (node instanceof TextNode) {
                        contentText.append(((TextNode) node).text());
                    } else if (node instanceof Element) {
                        if (((Element) node).tagName().equals("a")) {
                            // Append content text
                            if (contentText.length() > 0)
                                contents.append(contentText.toString().trim());
                            contentText.setLength(0);

                            // Get text with url
                            contents.append(new JsonObjectStringBuilder()
                                    .append("url", node.attr("href"))
                                    .append("content", ((Element) node).text().trim())
                            );
                        } else {
                            contentText.append(((Element) node).text());
                        }
                    } else {
                        response.errorParse("Can not parse news content text");
                        return;
                    }
                }
                if (contentText.length() > 0)
                    contents.append(contentText.toString().trim());
            }

            JsonObjectStringBuilder newsInfo = new JsonObjectStringBuilder();
            newsInfo.append("date", cols.get(0).text());
            newsInfo.append("contents", contents);
            newsInfo.append("department", cols.get(2).text());
            news.append(newsInfo);
        }
        data.append("news", news);

        // Get announcement
        Elements panels = body.getElementsByClass("panel");
        if (panels.size() < 2) {
            response.errorParse("Can not get announcement");
            return;
        }

        JsonObjectStringBuilder announcement = new JsonObjectStringBuilder();
        Elements linksElement = panels.get(1).getElementsByAttribute("href");
        for (Element linkElement : linksElement) {
            String url = linkElement.attr("href");
            if (!url.startsWith("http"))
                url = courseNckuOrg + '/' + url;
            String text = linkElement.text().trim();
            switch (text) {
                case "選課公告":
                case "Announcement of Course Enrollment":
                    announcement.append("enrollmentAnnouncement", url);
                    break;
                case "選課資訊":
                case "Information of Course Enrollment":
                    announcement.append("enrollmentInformation", url);
                    break;
                case "選課FAQs":
                case "FAQs for Course Enrollment":
                    announcement.append("enrollmentFAQs", url);
                    break;
                case "踏溯台南路線選擇系統":
                case "Exploring Tainan":
                    announcement.append("exploringTainan", url);
                    break;
                case "服務學習推薦專區":
                case "Service Courses Recommanded":
                    announcement.append("serviceRecommended", url);
                    break;
                case "課程資訊服務聯絡窗口":
                case "Contact Information":
                    announcement.append("contactInformation", url);
                    break;
            }

//            logger.log(url + " " + text);
        }
        data.append("bulletin", announcement);

        response.setData(data.toString());
    }

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }
}

