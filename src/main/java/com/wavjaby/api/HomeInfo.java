package com.wavjaby.api;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.Module;
import com.wavjaby.ProxyManager;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.lib.restapi.RequestMapping;
import com.wavjaby.lib.restapi.RestApiResponse;
import com.wavjaby.logger.Logger;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.util.Collections;
import java.util.List;

import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.lib.Cookie.getDefaultCookie;
import static com.wavjaby.lib.Cookie.packCourseLoginStateCookie;

@RequestMapping("/api/v0")
public class HomeInfo implements Module {
    private static final String TAG = "HomeInfo";
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

    @RequestMapping("/homeInfo")
    public RestApiResponse homeInfo(HttpExchange req) {
        long startTime = System.currentTimeMillis();
        CookieStore cookieStore = new CookieManager().getCookieStore();
        String loginState = getDefaultCookie(req, cookieStore);

        ApiResponse response = new ApiResponse();
        getHomepageInfo(cookieStore, response);
        packCourseLoginStateCookie(req, loginState, cookieStore);

        logger.log((System.currentTimeMillis() - startTime) + "ms");
        return response;
    }

    private void getHomepageInfo(CookieStore cookieStore, ApiResponse response) {
        Connection request = HttpConnection.connect(courseNckuOrg + "/index.php")
                .header("Connection", "keep-alive")
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
                for (Node node1 : phraseElement.childNodes()) {
                    boolean isSpan = node1 instanceof Element && ((Element) node1).tagName().equals("span");
                    List<Node> nodes = isSpan ? node1.childNodes() : Collections.singletonList(node1);
                    for (Node node : nodes) {
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


}

