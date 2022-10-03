package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.json.JsonBuilder;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wavjaby.Cookie.*;
import static com.wavjaby.Lib.*;
import static com.wavjaby.Main.courseNckuOrg;
import static com.wavjaby.Main.pool;

public class Search implements HttpHandler {
    private static final Pattern displayRegex = Pattern.compile("[\r\n]+\\.(\\w+) *\\{[\\r\\n]* *(?:/\\* *\\w+ *: *\\w+ *;? *\\*/ *)?display *: *(\\w+) *;? *");
    private static final DecimalFormat floatFormat = new DecimalFormat("#.#");

    @Override
    public void handle(HttpExchange req) throws IOException {
        pool.submit(() -> {
            System.out.println("[Search] Search");
            CookieManager cookieManager = new CookieManager();
            CookieStore cookieStore = cookieManager.getCookieStore();
            Headers requestHeaders = req.getRequestHeaders();
            OutputStream response = req.getResponseBody();
            String refererUrl = getRefererUrl(requestHeaders);

            try {
                // unpack cookie
                List<String> cookieIn = requestHeaders.containsKey("Cookie")
                        ? Arrays.asList(requestHeaders.get("Cookie").get(0).split(","))
                        : null;
                String orgCookie = unpackLoginCookie(cookieIn, cookieManager);
                String queryString = req.getRequestURI().getQuery();
                boolean success = false;
                JsonBuilder data = new JsonBuilder();
                if (queryString != null) {
                    Map<String, String> query = parseUrlEncodedForm(queryString);

                    String cosname = query.get("cosname");
                    String teaname = query.get("teaname");
                    String wk = query.get("wk");
                    String dept_no = query.get("dept");
                    String degree = query.get("degree");
                    String cl = query.get("cl");
                    if (cosname == null && teaname == null && wk == null && dept_no == null && degree == null && cl == null)
                        data.append("err", "[Search] no query string found");
                    else
                        success = search(cosname, teaname, wk, dept_no, degree, cl, data, cookieStore);
                } else
                    data.append("err", "[Search] no query string found");

                Headers responseHeader = req.getResponseHeaders();
                packLoginCookie(responseHeader, orgCookie, refererUrl, cookieStore);

                setAllowOrigin(refererUrl, responseHeader);
                responseHeader.set("Access-Control-Allow-Credentials", "true");
                responseHeader.set("Content-Type", "application/json; charset=utf-8");
                byte[] dataByte = data.toString().getBytes(StandardCharsets.UTF_8);
                req.sendResponseHeaders(success ? 200 : 400, dataByte.length);
                response.write(dataByte);
                req.close();
            } catch (Exception e) {
                req.close();
                e.printStackTrace();
            }
            System.out.println("Done");
        });
    }

    public boolean search(
            String cosname,     // 課程名稱
            String teaname,     // 教師姓名
            String wk,          // 星期 1 ~ 7
            String dept_no,     // 系所 A...
            String degree,      // 年級 1 ~ 7
            String cl,          // 節次 1 ~ 16 []
            JsonBuilder outData, CookieStore cookieStore) {
        try {
            // setup
            final String body = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=en_query")
                    .cookieStore(cookieStore)
                    .execute().body();
            pool.submit(() -> {
                cosPreCheck(body, cookieStore, outData);
            });

            // get entry function
            int searchFunctionStart = body.indexOf("function setdata()");
            if (searchFunctionStart == -1) {
                outData.append("err", "[Search] search function not found");
                return false;
            } else searchFunctionStart += 18;

            String searchID;
            int idStart, idEnd;
            if ((idStart = body.indexOf("'id'", searchFunctionStart)) == -1 ||
                    (idStart = body.indexOf('\'', idStart + 4)) == -1 ||
                    (idEnd = body.indexOf('\'', idStart + 1)) == -1
            ) {
                outData.append("err", "[Search] search id not found");
                return false;
            } else searchID = body.substring(idStart + 1, idEnd);


            // search
            StringBuilder builder = new StringBuilder();
            builder.append("id=").append(URLEncoder.encode(searchID, "UTF-8"));
            if (cosname != null)
                builder.append("&cosname=").append(URLEncoder.encode(cosname, "UTF-8"));
            if (teaname != null)
                builder.append("&teaname=").append(URLEncoder.encode(teaname, "UTF-8"));
            if (wk != null)
                builder.append("&wk=").append(wk);
            if (dept_no != null)
                builder.append("&dept_no=").append(dept_no);
            if (degree != null)
                builder.append("&degree=").append(degree);
            if (cl != null)
                builder.append("&cl=").append(cl);

            Connection.Response search = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=save_qry")
                    .cookieStore(cookieStore)
                    .method(Connection.Method.POST)
                    .requestBody(builder.toString())
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .execute();
            String searchBody = search.body();
            if (searchBody.equals("0")) {
                outData.append("err", "[Search] condition not set");
                return false;
            }
            if (searchBody.equals("1")) {
                outData.append("err", "[Search] wrong condition format");
                return false;
            }

            // get result
            Connection.Response searchResult = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215" + searchBody)
                    .cookieStore(cookieStore)
                    .method(Connection.Method.POST)
                    .requestBody(builder.toString())
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .execute();

            final String searchResultBody = searchResult.body();
            pool.submit(() -> {
                cosPreCheck(searchResultBody, cookieStore, outData);
            });

            Document resultBody = searchResult.parse();
            Elements elements = resultBody.getElementsByTag("table");
            if (elements.size() == 0) {
                outData.append("err", "[Search] result table not found");
                return false;
            }
            Element resultTable = elements.get(0);
            elements = resultTable.getElementsByTag("tbody");
            if (elements.size() == 0) {
                outData.append("err", "[Search] result table body not found");
                return false;
            }
            outData.append("data", parseCourseTable(elements.get(0), resultBody), true);
            return true;
        } catch (Exception e) {
            outData.append("err", "[Login] Unknown error: " + e);
            e.printStackTrace();
        }
        return false;
    }

    public String parseCourseTable(Element tbody, Document resultBody) {
        try {
            // style stuff
            List<Map.Entry<String, Boolean>> styles = new ArrayList<>();
            for (Element style : resultBody.getElementsByTag("style")) {
                if (style.childNodeSize() == 0) continue;
                Matcher matcher = displayRegex.matcher(style.childNode(0).toString());
                while (matcher.find()) {
                    if (matcher.groupCount() < 2) continue;
                    styles.add(new AbstractMap.SimpleEntry<>
                            (matcher.group(1), !matcher.group(2).equals("none")));
                }
            }

            Elements courseList = tbody.getElementsByTag("tr");

            StringBuilder result = new StringBuilder();
            for (Element element : courseList) {
                JsonBuilder jsonBuilder = new JsonBuilder();

                Elements section = element.getElementsByTag("td");
//                String departmentName = section.get(0).ownText();
                String courseName = section.get(4).getElementsByClass("course_name").get(0).text();
                String courseNote = section.get(4).ownText();
                String courseLimit = section.get(4).getElementsByClass("cond").text();
                String courseType = section.get(3).text();
                String tags;
                StringBuilder tagBuilder = new StringBuilder();
                for (Element tag : section.get(4).getElementsByClass("label"))
                    tagBuilder.append(',').append('"').append(tag.text()).append('"');
                if (tagBuilder.length() == 0) tags = "[]";
                else {
                    tagBuilder.setCharAt(0, '[');
                    tags = tagBuilder.append(']').toString();
                }

                List<TextNode> cache = section.get(5).textNodes();
                float credits;
                boolean required;
                String cacheC;
                if (cache.size() > 0 && (cacheC = cache.get(0).text().trim()).length() > 0) {
                    credits = Float.parseFloat(cacheC);
                    String cacheS = cache.get(1).text().trim();
                    required = cacheS.equals("必修") || cacheS.equals("Required");
                } else {
                    credits = -1;
                    required = false;
                }
                String teacherName = section.get(6).text();

                List<Node> cache1 = section.get(1).childNodes();
                String serialNumber = ((Element) cache1.get(0)).text().trim();
                String classCode = ((TextNode) cache1.get(1)).text().trim();
                String attributeCode = ((TextNode) cache1.get(3)).text().trim();
                int end = attributeCode.lastIndexOf(']');
                if (end != -1) attributeCode = attributeCode.substring(attributeCode.indexOf('[') + 1, end);

                String time;
                String location;
                List<TextNode> cache2 = section.get(8).textNodes();
                String cache3;
                if (cache2.size() > 0 && !(cache3 = cache2.get(0).text()).contains("未定") && !cache3.contains("Undecided")) {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append('[');
                    for (int i = 0; i < cache2.size(); i++) {
                        if (i > 0) stringBuilder.append(',');
                        String[] timeStr = cache2.get(i).text().trim().split("]");
                        stringBuilder.append('"').append(timeStr[0].substring(1)).append(timeStr[1]).append('"');
                    }
                    time = stringBuilder.append(']').toString();
                } else time = null;
                Elements cache4 = section.get(8).getElementsByTag("a");
                if (cache4.size() > 0) {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append('[');
                    for (int i = 0; i < cache4.size(); i++) {
                        String attribute = cache4.get(i).attributes().get("href");
                        String[] j = attribute.substring(17, attribute.length() - 3).split("','");
                        if (i > 0) stringBuilder.append(',');
                        stringBuilder.append('"').append(j[0]).append(',').append(j[1]).append('"');
                    }
                    location = stringBuilder.append(']').toString();
                } else location = null;

                String[] count;
                // read count
                StringBuilder countBuilder = new StringBuilder();
                for (Node n : section.get(7).childNodes()) {
                    // need to check style
                    if (n instanceof Element) {
                        String nc = ((Element) n).className();
                        boolean display = true;
                        for (Map.Entry<String, Boolean> style : styles) {
                            if (nc.equals(style.getKey()))
                                display = style.getValue();
                        }
                        if (display)
                            countBuilder.append(((Element) n).text());
                    } else if (n instanceof TextNode)
                        countBuilder.append(((TextNode) n).text());
                }
                count = countBuilder.toString().split("/");
                int selected = count[0].length() > 0 ? Integer.parseInt(count[0]) : -1;
                String available = count.length > 1 ? count[1] : null;

                jsonBuilder.append("sn", serialNumber);
                jsonBuilder.append("cc", classCode);
                jsonBuilder.append("ac", attributeCode);
                jsonBuilder.append("cn", courseName);
                jsonBuilder.append("ct", courseType);
                jsonBuilder.append("cr", courseNote);
                jsonBuilder.append("cl", courseLimit);
                jsonBuilder.append("tn", teacherName);
                jsonBuilder.append("t", tags, true);
                jsonBuilder.append("c", floatFormat.format(credits), true);
                jsonBuilder.append("r", required);
                jsonBuilder.append("s", selected);
                jsonBuilder.append("a", available);
                if (time == null) jsonBuilder.append("time", "null", true);
                else jsonBuilder.append("time", time, true);
                if (location == null) jsonBuilder.append("location", "null", true);
                else jsonBuilder.append("location", location, true);

                result.append(',').append(jsonBuilder);
            }
            if (result.length() > 0) {
                result.setCharAt(0, '[');
                result.append(']');
                return result.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "[]";
    }
}
