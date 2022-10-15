package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonBuilder;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.wavjaby.Cookie.*;
import static com.wavjaby.Lib.getRefererUrl;
import static com.wavjaby.Lib.setAllowOrigin;
import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.Main.pool;

public class CourseSchedule implements HttpHandler {
    @Override
    public void handle(HttpExchange req) {
        pool.submit(() -> {
            long startTime = System.currentTimeMillis();
            CookieManager cookieManager = new CookieManager();
            CookieStore cookieStore = cookieManager.getCookieStore();
            Headers requestHeaders = req.getRequestHeaders();
            String refererUrl = getRefererUrl(requestHeaders);

            try {
                // unpack cookie
                String loginState = getDefaultCookie(requestHeaders, cookieManager);

                JsonBuilder data = new JsonBuilder();
                boolean success = getCourseSchedule(cookieStore, data);

                Headers responseHeader = req.getResponseHeaders();
                packLoginStateCookie(responseHeader, loginState, refererUrl, cookieStore);

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
                e.printStackTrace();
            }
            System.out.println("[Schedule] Get schedule " + (System.currentTimeMillis() - startTime) + "ms");
        });
    }

    public boolean getCourseSchedule(CookieStore cookieStore, JsonBuilder data) {
        Connection conn = HttpConnection.connect(courseNckuOrg + "/index.php?c=cos32315")
                .cookieStore(cookieStore);
        Document root = null;
        try {
            root = conn.get();
        } catch (IOException ignore) {
        }
        if (root == null) {
            data.append("err", "[Schedule] Can not fetch schedule");
            return false;
        }

        Elements tableData = root.getElementsByClass("row visible-xs");
        if (tableData.size() < 1) {
            data.append("err", "[Schedule] Table not found");
            return false;
        }
        Elements tbody = tableData.get(0).getElementsByTag("tbody");
        if (tbody.size() < 7) {
            data.append("err", "[Schedule] Table body not found");
            return false;
        }
        Element ownerInfoEle = tableData.get(0).child(0);
        if (ownerInfoEle.childNodeSize() < 2 || ownerInfoEle.getElementsByClass("clock").size() == 0) {
            data.append("err", "[Schedule] OwnerInfo not found");
            return false;
        }
        ownerInfoEle = ownerInfoEle.child(1);
        String[] ownerInfoArr = ownerInfoEle.textNodes().get(0).toString().split("&nbsp; ");
        if (ownerInfoArr.length != 3) {
            data.append("err", "[Schedule] OwnerInfo parse error");
            return false;
        }
        int creditsStart = -1, creditsEnd = -1;
        char[] creditsParseArr = ownerInfoArr[2].toCharArray();
        for (int i = 0; i < creditsParseArr.length; i++) {
            char ch = creditsParseArr[i];
            if (ch >= '0' && ch <= '9')
                if (creditsStart == -1) creditsStart = i;
                else creditsEnd = i;
            else if (creditsEnd != -1)
                break;
        }
        if (creditsStart == -1 || creditsEnd == -1) {
            data.append("err", "[Schedule] OwnerInfo credits parse error");
            return false;
        }
        ownerInfoArr[2] = ownerInfoArr[2].substring(creditsStart, creditsEnd + 1);

        JsonArray courseScheduleData = new JsonArray();

        for (Element element : tbody) {
            JsonArray array = new JsonArray();
            courseScheduleData.add(array);
            // section times
            Elements eachCourse = element.getElementsByTag("tr");
            if (eachCourse.size() < 18) {
                data.append("err", "[Schedule] Course section not found");
                return false;
            }
            for (int i = 1; i < 18; i++) {
                Elements elements = eachCourse.get(i).getElementsByTag("td");
                if (elements.size() == 0) {
                    data.append("err", "[Schedule] Course info not found");
                    return false;
                }
                List<TextNode> courseDataText = elements.get(0).textNodes();

                JsonArray courseData = new JsonArray();
                array.add(courseData);
                if (courseDataText.size() > 0) {
                    String courseName = courseDataText.get(0).text();
                    int courseIdEnd = courseName.indexOf("】");
                    if (courseIdEnd == -1) {
                        data.append("err", "[Schedule] Course name parse error");
                        return false;
                    }
                    courseData.add(courseName.substring(1, courseIdEnd));
                    courseData.add(courseName.substring(courseIdEnd + 1));
                    if (courseDataText.size() > 1) {
                        String locationText = courseDataText.get(1).text();
                        int locationStart = locationText.indexOf("：");
                        if (locationStart == -1) {
                            data.append("err", "[Schedule] Course location parse error");
                            return false;
                        }
                        courseData.add(locationText.substring(locationStart + 1, locationText.length() - 1));
                    } else
                        courseData.add("");
                }
            }
        }
        data.append("id", ownerInfoArr[0]);
        data.append("name", ownerInfoArr[1]);
        data.append("credits", ownerInfoArr[2]);
        data.append("schedule", courseScheduleData.toString(), true);
        return true;
    }
}
