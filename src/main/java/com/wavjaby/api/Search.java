package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.json.JsonBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.logger.Logger;
import com.wavjaby.logger.ProgressBar;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.*;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.wavjaby.Cookie.*;
import static com.wavjaby.Lib.*;
import static com.wavjaby.Main.*;

public class Search implements HttpHandler {
    private static final String TAG = "[Search] ";
    private static final Pattern displayRegex = Pattern.compile("[\\r\\n]+\\.(\\w+) *\\{[\\r\\n]* *(?:/\\* *\\w+ *: *\\w+ *;? *\\*/ *)?display *: *(\\w+) *;? *");
    private static final DecimalFormat floatFormat = new DecimalFormat("#.#");

    private final UrSchool urSchool;

    public Search(UrSchool urSchool) {
        this.urSchool = urSchool;
    }

    private class SearchQuery {
        String searchID, timeSearchID;

        String courseName;     // 課程名稱
        String teacherName;     // 教師姓名
        String wk;          // 星期 1 ~ 7
        String deptNo;      // 系所 A...
        String grade;      // 年級 1 ~ 7
        String cl;          // 節次 1 ~ 16 []

        boolean getAll;

        boolean getSerial;
        Map<String, String> serialIdNumber;     // 系號=序號&系號2=序號

        boolean getTime;
        int yearBegin, semBegin, yearEnd, semEnd;

        SearchQuery(String queryString, String[] cookieIn) {
            if (cookieIn != null) for (String i : cookieIn)
                if (i.startsWith("searchID")) {
                    String searchIDs = i.substring(16);
                    int split = searchIDs.indexOf('|');
                    if (split == -1)
                        searchID = searchIDs;
                    else {
                        searchID = searchIDs.substring(0, split);
                        timeSearchID = searchIDs.substring(split + 1);
                    }
                    break;
                }

            Map<String, String> query = parseUrlEncodedForm(queryString);

            getAll = query.containsKey("ALL");
            String serialNum;
            getSerial = (serialNum = query.get("serial")) != null;
            String queryTime;
            getTime = (queryTime = query.get("queryTime")) != null;
            if (getTime) {
                String[] cache = queryTime.split(",");
                // TODO: Error catch
                yearBegin = Integer.parseInt(cache[0]);
                semBegin = Integer.parseInt(cache[1]);
                yearEnd = Integer.parseInt(cache[2]);
                semEnd = Integer.parseInt(cache[3]);
            }

            if (!getAll) {
                if (getSerial) {
                    try {
                        serialIdNumber = parseUrlEncodedForm(URLDecoder.decode(serialNum, "UTF-8"));
                    } catch (UnsupportedEncodingException ignore) {
                    }
                } else {
                    courseName = query.get("course");       // 課程名稱
                    teacherName = query.get("teacher");     // 教師姓名
                    wk = query.get("day");                  // 星期 1 ~ 7
                    deptNo = query.get("dept");             // 系所 A...
                    grade = query.get("grade");             // 年級 1 ~ 7
                    cl = query.get("section");              // 節次 1 ~ 16 []
                }
            }
        }

        boolean noQuery() {
            return !getAll && !getSerial && courseName == null && teacherName == null && wk == null && deptNo == null && grade == null && cl == null;
        }

        public boolean invalidSerialNumber() {
            return getSerial && serialIdNumber.size() == 0;
        }
    }

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
                String[] cookieIn = requestHeaders.containsKey("Cookie")
                        ? requestHeaders.get("Cookie").get(0).split(",")
                        : null;
                String loginState = unpackLoginStateCookie(cookieIn, cookieManager);

                // search
                JsonBuilder data = new JsonBuilder();
                SearchQuery searchQuery = new SearchQuery(req.getRequestURI().getQuery(), cookieIn);
                boolean success = true;
                if (searchQuery.invalidSerialNumber()) {
                    data.append("err", TAG + "Invalid serial number");
                    success = false;
                }
                if (searchQuery.noQuery()) {
                    data.append("err", TAG + "No query string found");
                    success = false;
                }
                if (success)
                    success = search(searchQuery, data, cookieStore);

                // set cookie
                Headers responseHeader = req.getResponseHeaders();
                if (success) {
                    String searchID = (searchQuery.searchID == null ? "" : searchQuery.searchID) +
                            '|' +
                            (searchQuery.timeSearchID == null ? "" : searchQuery.timeSearchID);
                    responseHeader.add("Set-Cookie", searchID +
                            "; Path=/api/search" + getCookieInfoData(refererUrl));
                } else
                    responseHeader.add("Set-Cookie", removeCookie("searchID") +
                            "; Path=/api/search" + getCookieInfoData(refererUrl));
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
            }
            Logger.log(TAG, "Search " + (System.currentTimeMillis() - startTime) + "ms");
        });
    }

    private boolean search(SearchQuery searchQuery, JsonBuilder outData, CookieStore cookieStore) {
        try {
            StringBuilder searchResult = new StringBuilder();
            boolean success = true;
            // get all course
            if (searchQuery.getAll) {
                Connection.Response allDeptRes = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all")
                        .cookieStore(cookieStore)
                        .execute();
                String allDeptBody = allDeptRes.body();
                cosPreCheck(allDeptBody, cookieStore, outData);

                List<String> allDeptList = new ArrayList<>();
                for (Element element : allDeptRes.parse().getElementsByClass("pnl_dept"))
                    allDeptList.addAll(element.getElementsByAttribute("data-dept").eachAttr("data-dept"));
                String[] allDept = allDeptList.toArray(new String[0]);

                int cryptStart, cryptEnd;
                if ((cryptStart = allDeptBody.indexOf("'crypt'")) == -1 ||
                        (cryptStart = allDeptBody.indexOf('\'', cryptStart + 7)) == -1 ||
                        (cryptEnd = allDeptBody.indexOf('\'', ++cryptStart)) == -1
                ) {
                    outData.append("err", TAG + "Can not get crypt");
                    success = false;
                }
                // start getting dept
                else {
                    String crypt = allDeptBody.substring(cryptStart, cryptEnd);
                    CountDownLatch countDownLatch = new CountDownLatch(allDept.length);
                    ExecutorService fetchPool = Executors.newFixedThreadPool(3);
                    AtomicBoolean allSuccess = new AtomicBoolean(true);
                    ProgressBar progressBar = new ProgressBar(TAG + "Get All ");
                    Logger.addProgressBar(progressBar);
                    progressBar.setProgress(0f);
                    int[] j = {0};
                    for (String dept : allDept) {
                        fetchPool.submit(() -> {
                            if (allSuccess.get() && !getDept(dept, crypt, cookieStore, outData, searchResult))
                                allSuccess.set(false);
                            progressBar.setProgress((float) ++j[0] / allDept.length * 100f);
                            countDownLatch.countDown();
                        });
                        Thread.sleep(100);
                    }
                    countDownLatch.await();
                    progressBar.setProgress(100f);
                    Logger.removeProgressBar(progressBar);
                    success = allSuccess.get();
                }
            }
            // get listed serial
            else if (searchQuery.getSerial) {
                for (Map.Entry<String, String> i : searchQuery.serialIdNumber.entrySet()) {
                    searchResult.setLength(0);
                    if (!postSearchData(searchQuery, i, cookieStore, outData, searchResult)) {
                        success = false;
                        break;
                    }
                    if (searchResult.length() > 0) searchResult.setCharAt(0, '[');
                    else searchResult.append('[');
                    searchResult.append(']');
                    outData.append(i.getKey(), searchResult.toString(), true);
                }
            } else {
                success = postSearchData(searchQuery, null, cookieStore, outData, searchResult);
            }

            if (success && !searchQuery.getSerial) {
                if (searchResult.length() > 0)
                    searchResult.setCharAt(0, '[');
                else
                    searchResult.append('[');
                searchResult.append(']');
                outData.append("data", searchResult.toString(), true);
            }
            return success;
        } catch (Exception e) {
            outData.append("err", TAG + "Unknown error: " + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return false;
    }

    private boolean getDept(String deptNo, String crypt, CookieStore cookieStore, JsonBuilder outData, StringBuilder searchResultBuilder) {
        try {
            Connection.Response id = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all&m=result_init")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .method(Connection.Method.POST)
                    .requestBody("dept_no=" + deptNo + "&crypt=" + URLEncoder.encode(crypt, "UTF-8"))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .timeout(60 * 1000)
                    .execute();

            JsonObject idData = new JsonObject(id.body());
            if (idData.getString("err").length() > 0) {
                outData.append("err", TAG + "Error from NCKU course: " + idData.getString("err"));
                return false;
            }

            Connection.Response result = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry_all&m=result&i=" +
                            URLEncoder.encode(idData.getString("id"), "UTF-8"))
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .maxBodySize(20 * 1024 * 1024)
                    .timeout(60 * 1000)
                    .header("Referer", courseNckuOrg + "/index.php?c=qry_all")
                    .execute();
            if (result.url().getQuery().equals("c=qry_all")) {
                outData.append("warn", TAG + "Dept.No " + deptNo + " not found");
                return true;
            }

            String searchResultBody = result.body();
//            cosPreCheck(searchResultBody, cookieStore, outData);

            int resultTableStart;
            if ((resultTableStart = searchResultBody.indexOf("<table")) == -1) {
                outData.append("err", TAG + "Result table not found");
                return false;
            }
            // get table body
            int resultTableBodyStart, resultTableBodyEnd;
            if ((resultTableBodyStart = searchResultBody.indexOf("<tbody>", resultTableStart + 7)) == -1 ||
                    (resultTableBodyEnd = searchResultBody.indexOf("</tbody>", resultTableBodyStart + 7)) == -1
            ) {
                outData.append("err", TAG + "Result table body not found");
                return false;
            }

            // parse table
            String resultBody = searchResultBody.substring(resultTableBodyStart, resultTableBodyEnd + 8);
            Node tbody = Parser.parseFragment(resultBody, new Element("tbody"), "").get(0);

            parseCourseTable(
                    (Element) tbody,
                    null,
                    searchResultBody,
                    searchResultBuilder
            );

            return true;
        } catch (IOException e) {
            outData.append("err", TAG + "Unknown error" + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return false;
    }

    private boolean postSearchData(SearchQuery searchQuery, Map.Entry<String, String> getSerialNum,
                                   CookieStore cookieStore, JsonBuilder outData, StringBuilder searchResultBuilder) {
        try {
            // setup
            if (searchQuery.searchID == null) {
//                Logger.log(TAG, TAG + "Get searchID");
                final String body = HttpConnection.connect(courseNckuOrg + "/index.php?c=qry11215&m=en_query")
                        .ignoreContentType(true)
                        .execute().body();
                cosPreCheck(body, cookieStore, outData);
                if ((searchQuery.searchID = getSearchID(body, outData)) == null)
                    return false;
            }

            String searchResultBody = postCourseNCKU(searchQuery, getSerialNum == null ? null : getSerialNum.getKey(), cookieStore, outData);
            if (searchResultBody == null)
                return false;

            if ((searchQuery.searchID = getSearchID(searchResultBody, outData)) == null)
                return false;

            int resultTableStart;
            if ((resultTableStart = searchResultBody.indexOf("<table")) == -1) {
                outData.append("err", TAG + "Result table not found");
                return false;
            }
            // get table body
            int resultTableBodyStart, resultTableBodyEnd;
            if ((resultTableBodyStart = searchResultBody.indexOf("<tbody>", resultTableStart + 7)) == -1 ||
                    (resultTableBodyEnd = searchResultBody.indexOf("</tbody>", resultTableBodyStart + 7)) == -1
            ) {
                outData.append("err", TAG + "Result table body not found");
                return false;
            }

            // parse table
//            Logger.log(TAG, TAG + "Parse course table");
            String resultBody = searchResultBody.substring(resultTableBodyStart, resultTableBodyEnd + 8);
            Node tbody = Parser.parseFragment(resultBody, new Element("tbody"), "").get(0);

            parseCourseTable(
                    (Element) tbody,
                    getSerialNum.getValue().split(","),
                    searchResultBody,
                    searchResultBuilder
            );

            return true;
        } catch (IOException e) {
            outData.append("err", TAG + "Unknown error" + Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        }
        return false;
    }

    private void parseCourseTable(Element tbody, String[] getSerialNumber, String searchResultBody, StringBuilder result) {
        // find style
        List<String> styleList = new ArrayList<>();
        int styleStart, styleEnd = 0;
        while ((styleStart = searchResultBody.indexOf("<style", styleEnd)) != -1) {
            styleStart = searchResultBody.indexOf(">", styleStart + 6);
            if (styleStart == -1) continue;
            styleStart += 1;
            styleEnd = searchResultBody.indexOf("</style>", styleStart);
            if (styleEnd == -1) continue;
            styleList.add(searchResultBody.substring(styleStart, styleEnd));
            styleEnd += 8;
        }
        // style stuff
        List<Map.Entry<String, Boolean>> styles = new ArrayList<>();
        for (String style : styleList) {
            Matcher matcher = displayRegex.matcher(style);
            while (matcher.find()) {
                if (matcher.groupCount() < 2) continue;
                styles.add(new AbstractMap.SimpleEntry<>(matcher.group(1), !matcher.group(2).equals("none")));
            }
        }

        // get course list
        Elements courseList = tbody.getElementsByTag("tr");
        for (Element element : courseList) {
            Elements section = element.getElementsByTag("td");

            String departmentName = section.get(0).ownText();

            List<Node> section1 = section.get(1).childNodes();
            // get serial number
            String serialNumber = ((Element) section1.get(0)).text().trim();
            if (getSerialNumber != null) {
                String serialNumberStr = serialNumber.substring(serialNumber.indexOf('-') + 1);
                boolean notContains = true;
                for (String s : getSerialNumber)
                    if (s.equals(serialNumberStr)) {
                        notContains = false;
                        break;
                    }
                // skip if we don't want
                if (notContains) continue;
            }

            // get system number
            String systemNumber = ((TextNode) section1.get(1)).text().trim();

            // get attribute code
            String attributeCode = ((TextNode) section1.get(3)).text().trim();
            int end = attributeCode.lastIndexOf(']');
            if (end != -1) attributeCode = attributeCode.substring(attributeCode.indexOf('[') + 1, end);

            // get course name
            String courseName = section.get(4).getElementsByClass("course_name").get(0).text();

            // get course note
            String courseNote = section.get(4).ownText().replace("\"", "\\\"");

            // get course limit
            String courseLimit = section.get(4).getElementsByClass("cond").text().replace("\"", "\\\"");

            // get course type
            String courseType = section.get(3).text();

            // get teacher name
            String teachers = section.get(6).text();
            if (teachers.length() == 0 || teachers.equals("未定"))
                teachers = "";
            else {
                teachers = teachers.replace("*", "");
                urSchool.addTeacherCache(teachers.split(" "));
            }

            // get course tags
            String tags;
            StringBuilder tagBuilder = new StringBuilder();
            for (Element tag : section.get(4).getElementsByClass("label")) {
                // get tag type
                String tagType = "";
                for (String i : tag.classNames())
                    if (i.length() > 6 && i.startsWith("label")) {
                        tagType = i.substring(6);
                        break;
                    }
                Element j = tag.firstElementChild();
                String link = j == null ? "" : j.attr("href");

                // write tag
                tagBuilder.append(',').append('"')
                        .append(tag.text()).append(',')
                        .append(tagType).append(',')
                        .append(link)
                        .append('"');
            }
            if (tagBuilder.length() == 0) tags = "[]";
            else {
                tagBuilder.setCharAt(0, '[');
                tags = tagBuilder.append(']').toString();
            }

            // get grade & classInfo & group
            int courseGrade = -1;
            String classInfo = "";
            String classGroup = "";
            List<Node> section2 = section.get(2).childNodes();
            int section2c = 0;
            for (Node node : section2) {
                if (!(node instanceof TextNode))
                    // <br>
                    section2c++;
                else {
                    // text
                    String cache = ((TextNode) node).text().trim();
                    if (cache.length() == 0) continue;
                    if (section2c == 0)
                        courseGrade = Integer.parseInt(cache);
                    else if (section2c == 1)
                        classInfo = cache;
                    else
                        classGroup = cache;
                }
            }

            // get credits & required
            List<TextNode> section5 = section.get(5).textNodes();
            float credits;
            boolean required;
            String section5Str;
            if (section5.size() > 0 && (section5Str = section5.get(0).text().trim()).length() > 0) {
                credits = Float.parseFloat(section5Str);
                String cache = section5.get(1).text().trim();
                required = cache.equals("必修") || cache.equals("Required");
            } else {
                credits = -1;
                required = false;
            }

            // get time
            String time;
            StringBuilder timeSectionBuilder = new StringBuilder();
            timeSectionBuilder.append('[');
            StringBuilder builder = new StringBuilder();
            int timeParseState = 0;
            for (Node node : section.get(8).childNodes()) {
                // time
                if (timeParseState == 0) {
                    timeParseState++;
                    String text;
                    if (node instanceof TextNode && (text = ((TextNode) node).text().trim()).length() > 0) {
                        int split = text.indexOf(']', 1);
                        if (split != -1)
                            builder.append(text, 1, split).append(',')
                                    .append(text, split + 1, text.length()).append(',');
                        else builder.append(',').append(',');
                    }
                    // if no time
                    else builder.append(',').append(',');
                    continue;
                }
                if (timeParseState == 1) {
                    timeParseState++;
                    Attributes attributes = node.attributes();
                    // location link
                    String attribute;
                    if (attributes.size() > 0 && (attribute = attributes.get("href")).length() > 0) {
                        String[] j = attribute.substring(17, attribute.length() - 3).split("','");
                        builder.append(j[0]).append(',').append(j[1]).append(',').append(((Element) node).text().trim());
                        continue;
                    }
                    // if no location
                    else builder.append(',').append(',');
                }
                if (timeParseState == 2) {
                    String tagName = ((Element) node).tagName();
                    if (tagName.equals("br")) {
                        timeSectionBuilder.append('"').append(builder).append('"').append(',');
                        timeParseState = 0;
                        builder.setLength(0);
                    } else if (((Element) node).tagName().equals("div"))
                        builder.append(',').append(node.attr("data-mkey"));
                }
            }
            if (builder.length() > 0) {
                if (timeParseState != 2)
                    builder.append(',').append(',');
                timeSectionBuilder.append('"').append(builder).append('"');
            }
            timeSectionBuilder.append(']');
            time = timeSectionBuilder.toString();

            // get selected & available
            String[] count;
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
            int available = count.length < 2 ? -1 :
                    (count[1].equals("額滿") || count[1].equals("full")) ? 0 :
                            (count[1].equals("不限") || count[1].equals("unlimited")) ? -2 :
                                    (count[1].startsWith("洽") || count[1].startsWith("please connect")) ? -3 :
                                            Integer.parseInt(count[1]);

            // get moodle
            String moodle = "";
            Elements moodleEle = section.get(9).getElementsByAttributeValueStarting("href", "javascript:moodle");
            if (moodleEle.size() == 1) {
                String str = moodleEle.get(0).attr("href");
                moodle = str.substring(19, str.length() - 3).replace("','", ",");
            }

            // output
            JsonBuilder jsonBuilder = new JsonBuilder();
            jsonBuilder.append("dn", departmentName);
            jsonBuilder.append("sn", serialNumber);
            jsonBuilder.append("ca", attributeCode);
            jsonBuilder.append("cs", systemNumber);
            jsonBuilder.append("cn", courseName);
            jsonBuilder.append("ci", courseNote);
            jsonBuilder.append("cl", courseLimit);
            jsonBuilder.append("ct", courseType);
            jsonBuilder.append("g", courseGrade);
            jsonBuilder.append("co", classInfo);
            jsonBuilder.append("cg", classGroup);
            jsonBuilder.append("ts", teachers);
            jsonBuilder.append("tg", tags, true);
            jsonBuilder.append("c", floatFormat.format(credits), true);
            jsonBuilder.append("r", required);
            jsonBuilder.append("s", selected);
            jsonBuilder.append("a", available);
            jsonBuilder.append("t", time, true);
            jsonBuilder.append("m", moodle);
            result.append(',').append(jsonBuilder);
        }
    }

    private String postCourseNCKU(SearchQuery searchQuery, String getSerialNum,
                                  CookieStore cookieStore, JsonBuilder outData) {
        try {
            StringBuilder postData = new StringBuilder();
            String host;
            if (searchQuery.getTime) {
                host = courseQueryNckuOrg;
                postData.append("id=").append(URLEncoder.encode(searchQuery.timeSearchID, "UTF-8"));
            } else {
                host = courseNckuOrg;
                postData.append("id=").append(URLEncoder.encode(searchQuery.searchID, "UTF-8"));
                if (searchQuery.courseName != null) postData.append("&cosname=").append(URLEncoder.encode(searchQuery.courseName, "UTF-8"));
                if (searchQuery.teacherName != null) postData.append("&teaname=").append(URLEncoder.encode(searchQuery.teacherName, "UTF-8"));
                if (searchQuery.wk != null) postData.append("&wk=").append(searchQuery.wk);
                if (getSerialNum != null) postData.append("&dept_no=").append(getSerialNum);
                else if (searchQuery.deptNo != null) postData.append("&dept_no=").append(searchQuery.deptNo);
                if (searchQuery.grade != null) postData.append("&degree=").append(searchQuery.grade);
                if (searchQuery.cl != null) postData.append("&cl=").append(searchQuery.cl);
            }


//            Logger.log(TAG, TAG + "Post search query");
            Connection.Response search = HttpConnection.connect(host + "/index.php?c=qry11215&m=save_qry")
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .method(Connection.Method.POST)
                    .requestBody(postData.toString())
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .execute();
            String searchBody = search.body();
            if (searchBody.equals("0")) {
                outData.append("err", TAG + "Condition not set");
                return null;
            }
            if (searchBody.equals("1")) {
                outData.append("err", TAG + "Wrong condition format");
                return null;
            }

            // get result
//            Logger.log(TAG, TAG + "Get search result");
            Connection.Response searchResult = HttpConnection.connect(host + "/index.php?c=qry11215" + searchBody)
                    .cookieStore(cookieStore)
                    .ignoreContentType(true)
                    .method(Connection.Method.POST)
                    .requestBody(postData.toString())
                    .maxBodySize(20 * 1024 * 1024)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .execute();

            String searchResultBody = searchResult.body();
            cosPreCheck(searchResultBody, cookieStore, outData);
            return searchResultBody;
        } catch (IOException e) {
            e.printStackTrace();
            outData.append("err", TAG + "Unknown error" + Arrays.toString(e.getStackTrace()));
            return null;
        }
    }

    private String getSearchID(String body, JsonBuilder outData) {
        // get entry function
        int searchFunctionStart = body.indexOf("function setdata()");
        if (searchFunctionStart == -1) {
            outData.append("err", TAG + "Search function not found");
            return null;
        } else searchFunctionStart += 18;

        int idStart, idEnd;
        if ((idStart = body.indexOf("'id'", searchFunctionStart)) == -1 ||
                (idStart = body.indexOf('\'', idStart + 4)) == -1 ||
                (idEnd = body.indexOf('\'', idStart + 1)) == -1
        ) {
            outData.append("err", TAG + "Search id not found");
            return null;
        }
        return body.substring(idStart + 1, idEnd);
    }
}
