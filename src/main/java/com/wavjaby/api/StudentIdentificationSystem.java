package com.wavjaby.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.EndpointModule;
import com.wavjaby.json.JsonArray;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ApiResponse;
import com.wavjaby.logger.Logger;
import com.wavjaby.svgbuilder.*;
import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.wavjaby.Main.stuIdSysNckuOrg;
import static com.wavjaby.lib.Cookie.*;
import static com.wavjaby.lib.Lib.*;

public class StudentIdentificationSystem implements EndpointModule {
    private static final String TAG = "[StuIdSys]";
    private static final Logger logger = new Logger(TAG);

    private static final byte[][] numbers = {
            {0b00100, 0b01010, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100},
            {0b00100, 0b01100, 0b10100, 0b00100, 0b00100, 0b00100, 0b00100, 0b11111},
            {0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111},
            {0b01110, 0b10001, 0b00001, 0b00110, 0b00001, 0b00001, 0b10001, 0b01110},
            {0b00010, 0b00110, 0b01010, 0b10010, 0b10010, 0b11111, 0b00010, 0b00010},
            {0b11110, 0b10000, 0b11110, 0b10001, 0b00001, 0b00001, 0b10001, 0b01110},
            {0b00111, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b10001, 0b01110},
            {0b11111, 0b00001, 0b00010, 0b00010, 0b00100, 0b00100, 0b01000, 0b01000},
            {0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b10001, 0b01110},
            {0b01110, 0b10001, 0b10001, 0b10001, 0b01110, 0b00001, 0b00010, 0b11100},
    };

    public static class SemesterOverview {
        final String semID;
        final float requireCredits;
        final float electiveCredits;
        final float summerCourses;
        final float secondMajorCredits;
        final float equivalentCredits;
        final float earnedCredits;
        final float totalCredits;
        final float weightedGrades;
        final float averageScore;
        final int classRanking, classRankingTotal;
        final int deptRanking, deptRankingTotal;

        public SemesterOverview(Elements row) {
            semID = row.get(1).getElementsByTag("input").attr("value");
            requireCredits = Float.parseFloat(row.get(2).text().trim());
            electiveCredits = Float.parseFloat(row.get(3).text().trim());
            summerCourses = Float.parseFloat(row.get(4).text().trim());
            // Minor/Second Major
            secondMajorCredits = Float.parseFloat(row.get(5).text().trim());
            equivalentCredits = Float.parseFloat(row.get(6).text().trim());
            earnedCredits = Float.parseFloat(row.get(7).text().trim());
            totalCredits = Float.parseFloat(row.get(8).text().trim());
            weightedGrades = Float.parseFloat(row.get(9).text().trim());
            averageScore = Float.parseFloat(row.get(10).text().trim());

            String[] classRankingStr = row.get(11).text().trim().split("／");
            classRanking = Integer.parseInt(classRankingStr[0]);
            classRankingTotal = Integer.parseInt(classRankingStr[1]);

            String[] deptRankingStr = row.get(12).text().trim().split("／");
            deptRanking = Integer.parseInt(deptRankingStr[0]);
            deptRankingTotal = Integer.parseInt(deptRankingStr[1]);
        }

        @Override
        public String toString() {
            return new JsonObjectStringBuilder()
                    .append("semID", semID)
                    .append("requireC", requireCredits)
                    .append("electiveC", electiveCredits)
                    .append("summerC", summerCourses)
                    .append("secondMajorC", secondMajorCredits)
                    .append("equivalentC", equivalentCredits)
                    .append("earnedC", earnedCredits)
                    .append("totalC", totalCredits)
                    .append("weightedGrades", weightedGrades)
                    .append("averageScore", averageScore)
                    .append("classRanking", classRanking)
                    .append("classRankingTotal", classRankingTotal)
                    .append("deptRanking", deptRanking)
                    .append("deptRankingTotal", deptRankingTotal)
                    .toString();
        }
    }

    public static class CourseGrade {
        final String serialNumber;
        final String courseNo;
        final String courseName;
        final String remark;
        final float credits;
        final String require;
        final float grade;
        final String gpa;
        final String normalDistImgQuery;


        public CourseGrade(Elements row) {
            String serialNumber_ = row.get(1).text().trim();
            if (serialNumber_.length() > 0) {
                int split = serialNumber_.indexOf(' ');
                if (split != -1) {
                    String dept = serialNumber_.substring(0, split);
                    String num = serialNumber_.substring(split + 1);
                    serialNumber = dept + '-' + (num.length() == 1
                            ? "00" + num
                            : num.length() == 2
                            ? "0" + num
                            : num);

                } else serialNumber = null;
            } else serialNumber = null;

            courseNo = row.get(2).text().trim();
            courseName = row.get(3).text().trim();
            // 課程別
            String remark_ = row.get(4).text().trim();
            remark = remark_.length() == 0 ? null : remark_;
            credits = Float.parseFloat(row.get(5).text().trim());
            // Required/Elective
            require = row.get(6).text().trim();
            String grade_ = row.get(7).text().trim();
            grade = grade_.equals("通過") ? -1 : Float.parseFloat(grade_);
            gpa = row.get(8).text().trim();

            String[] link_ = row.get(2).children().attr("href").split("[?&=]", 9);
            if (link_.length < 9)
                normalDistImgQuery = null;
            else {
                String syear = link_[2],
                        sem = link_[4],
                        co_no = link_[6],
                        class_code = link_[8];
                normalDistImgQuery = syear + ',' + sem + ',' + co_no + ',' + class_code;
            }
        }

        @Override
        public String toString() {
            return new JsonObjectStringBuilder()
                    .append("serialNumber", serialNumber)
                    .append("courseNo", courseNo)
                    .append("courseName", courseName)
                    .append("remark", remark)
                    .append("credits", credits)
                    .append("require", require)
                    .append("grade", grade)
                    .append("gpa", gpa)
                    .append("imgQuery", normalDistImgQuery)
                    .toString();
        }
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
        Headers requestHeaders = req.getRequestHeaders();

        try {
            ApiResponse apiResponse = new ApiResponse();
            studentIdSysGet(req, apiResponse);

            Headers responseHeader = req.getResponseHeaders();
            byte[] dataByte = apiResponse.toString().getBytes(StandardCharsets.UTF_8);
            responseHeader.set("Content-Type", "application/json; charset=UTF-8");

            // send response
            setAllowOrigin(requestHeaders, responseHeader);
            req.sendResponseHeaders(apiResponse.isSuccess() ? 200 : 400, dataByte.length);
            OutputStream response = req.getResponseBody();
            response.write(dataByte);
            response.flush();
            req.close();
        } catch (IOException e) {
            req.close();
            e.printStackTrace();
        }
        logger.log("Get template " + (System.currentTimeMillis() - startTime) + "ms");
    };

    @Override
    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    private void studentIdSysGet(HttpExchange req, ApiResponse apiResponse) {
        // Setup cookies
        CookieManager cookieManager = new CookieManager();
        CookieStore cookieStore = cookieManager.getCookieStore();
        Headers requestHeaders = req.getRequestHeaders();
        Headers responseHeader = req.getResponseHeaders();
        String originUrl = getOriginUrl(requestHeaders);
        String[] cookies = splitCookie(requestHeaders);
        String loginState = unpackStudentIdSysLoginStateCookie(cookies, cookieStore);

        Map<String, String> query = parseUrlEncodedForm(req.getRequestURI().getRawQuery());
        String mode = query.get("m");
        // Get semesters info
        if (mode == null || mode.equals("s")) {
            List<SemesterOverview> data = getSemestersOverview(cookieStore);
            if (data == null)
                apiResponse.addError(TAG + "Cant get semesters table");
            else
                apiResponse.setData(data.toString());
        }
        // Get semester course grade
        else if (mode.equals("g")) {
            String semId = query.get("s");
            if (semId == null)
                apiResponse.addError(TAG + "Semester not given");
            else {
                List<CourseGrade> data = getSemesterGradeTable(semId, cookieStore);
                if (data == null)
                    apiResponse.addError(TAG + "Cant get semester course grade");
                else
                    apiResponse.setData(data.toString());
            }
        }
        // Get semester course grade normal distribution
        else if (mode.equals("i")) {
            String imageQuery = null;
            String imageQueryRaw = query.get("q");
            if (imageQueryRaw == null)
                apiResponse.addError(TAG + "Image query not given");
            else {
                String[] values;
                values = imageQueryRaw.split(",", 4);
                if (values.length != 4)
                    apiResponse.addError(TAG + "Image query format error");
                else
                    imageQuery = "syear=" + values[0] + "&sem=" + values[1] + "&co_no=" + values[2] + "&class_code=" + values[3];
            }

            // Get image
            if (imageQuery != null) {
                String data = getDistributionGraph(imageQuery, cookieStore);
                if (data == null)
                    apiResponse.addError(TAG + "Cant get semester course grade normal distribution");
                else
                    apiResponse.setData(new JsonArray().add(data).toString());
            }
        }
        // Unknown mode
        else
            apiResponse.addError(TAG + "Unknown mode: " + mode);

        packStudentIdSysLoginStateCookie(responseHeader, loginState, originUrl, cookieStore);
    }

    private List<SemesterOverview> getSemestersOverview(CookieStore cookieStore) {
        Element tbody = null;
        try {
            Connection.Response gradesHistoryListPage = HttpConnection.connect(stuIdSysNckuOrg + "/ncku/qrys05.asp")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .execute();
            Elements tbodyElements = gradesHistoryListPage.parse().getElementsByTag("tbody");
            if (tbodyElements.size() < 4)
                return null;
            tbody = tbodyElements.get(3);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (tbody == null)
            return null;

        Elements tableRows = tbody.children();
        if (tableRows.size() < 3)
            return null;
        List<SemesterOverview> semesterOverviewList = new ArrayList<>(tableRows.size() - 3);
        for (int i = 2; i < tableRows.size() - 1; i++) {
            Element row = tableRows.get(i);
            if (row.childrenSize() < 13)
                return null;
            semesterOverviewList.add(new SemesterOverview(tableRows.get(i).children()));
        }

        return semesterOverviewList;
    }

    private List<CourseGrade> getSemesterGradeTable(String semesterID, CookieStore cookieStore) {
        Element tbody = null;
        try {
            Connection.Response gradesListPage = HttpConnection.connect(stuIdSysNckuOrg + "/ncku/qrys05.asp")
                    .header("Connection", "keep-alive")
                    .cookieStore(cookieStore)
                    .method(Connection.Method.POST)
                    .requestBody("submit1=" + semesterID)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .execute();
            Elements tbodyElements = gradesListPage.parse().getElementsByTag("tbody");
            if (tbodyElements.size() < 4)
                return null;
            tbody = tbodyElements.get(3);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (tbody == null)
            return null;

        Elements tableRows = tbody.children();
        if (tableRows.size() < 4)
            return null;
        List<CourseGrade> courseGradeList = new ArrayList<>(tableRows.size() - 4);
        for (int i = 2; i < tableRows.size() - 2; i++) {
            Element row = tableRows.get(i);
            if (row.childrenSize() < 10)
                return null;
            courseGradeList.add(new CourseGrade(row.children()));
        }

        return courseGradeList;
    }


    private String getDistributionGraph(String query, CookieStore cookieStore) {
        try {
            BufferedInputStream in = HttpConnection.connect(stuIdSysNckuOrg + "/ncku/histogram.asp?" + query)
                    .header("Connection", "keep-alive")
                    .ignoreContentType(true)
                    .cookieStore(cookieStore).execute().bodyStream();
            BufferedImage image = ImageIO.read(in);

            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            int[] imageRGB = image.getRGB(0, 0, imageWidth, imageHeight, null, 0, imageWidth);

            int[] studentCount = parseImage(imageRGB, imageWidth, imageHeight, false);
            image.setRGB(0, 0, imageWidth, imageHeight, imageRGB, 0, imageWidth);
            ImageIO.write(image, "png", new File("image1.png"));

            if (studentCount == null)
                return null;
            int totalStudentCount = 0;
            float highestPercent = 0;
            for (int i = 0; i < 11; i++)
                totalStudentCount += studentCount[i];
            for (int i = 0; i < 11; i++) {
                float percent = (float) studentCount[i] / totalStudentCount;
                if (percent > highestPercent)
                    highestPercent = percent;
            }
            highestPercent = 1 / highestPercent;

            int svgWidth = 600, svgHeight = 450;
            Svg svg = new Svg(svgWidth, svgHeight);
            svg.setBackgroundColor("#181A1B");
            int graphPaddingX = 20;
            int graphPaddingY = 35;
            int graphPaddingTopY = 80;
            int xAxisWidth = svgWidth - graphPaddingX - graphPaddingX;
            int yAxisHeight = svgHeight - graphPaddingY - graphPaddingTopY;
            float xAxisPadding = xAxisWidth / 11f;
            // X axis
            for (int i = 0; i < 11; i++) {
                float x = graphPaddingX + xAxisPadding * i;
                // Axis
                svg.appendChild(
                        new SvgLine(x, svgHeight - graphPaddingY, x, svgHeight - graphPaddingY + 10)
                                .setStrokeColor("#AAA")
                                .setStrokeWidth(2)
                );
                svg.appendChild(
                        new SvgText(x, svgHeight - graphPaddingY + 28, String.valueOf(i * 10))
                                .setTextAnchor(TextAnchor.MIDDLE)
                                .setFontSize("18px")
                                .setFontColor("#DDD")
                );
                // Bar
                float height = (float) yAxisHeight * studentCount[i] / totalStudentCount * highestPercent;
                svg.appendChild(
                        new SvgRect(x, svgHeight - graphPaddingY - height, xAxisPadding, height)
                                .setFill("#3376BD")
                                .setStrokeColor("#3390FF")
                                .setStrokeWidth(2)
                );
                if (studentCount[i] > 0)
                    svg.appendChild(
                            new SvgText(x + xAxisPadding * 0.5f, svgHeight - graphPaddingY - height - 5, String.valueOf(studentCount[i]))
                                    .setTextAnchor(TextAnchor.MIDDLE)
                                    .setFontSize("20px")
                                    .setFontColor("#DDD")
                    );
            }
            // Baseline
            svg.appendChild(
                    new SvgLine(graphPaddingX, svgHeight - graphPaddingY, svgWidth - graphPaddingX, svgHeight - graphPaddingY)
                            .setStrokeColor("#AAA")
                            .setStrokeWidth(2)
                            .setStrokeLinecap(StrokeLinecap.SQUARE)
            );

            double avg = 0, stdDev = 0;
            for (int i = 0; i < 11; i++) {
                float score = (i < 10 ? (i + i + 1) : (i * 2)) * 5;
                avg += score * studentCount[i];
                stdDev += score * score * studentCount[i];
            }
            avg /= totalStudentCount;
            stdDev = Math.sqrt(stdDev / totalStudentCount - avg * avg);
//            System.out.println(avg);
//            System.out.println(stdDev);

            int peakStudentCount = 0;
            for (int i = 0; i < 11; i++) {
                float score = (i < 10 ? (i + i + 1) : (i * 2)) * 5;
                if (avg - stdDev < score && score < avg + stdDev)
                    peakStudentCount += studentCount[i];
            }

            SvgPath stdDevLine = new SvgPath()
                    .setStrokeWidth(2)
                    .setStrokeColor("#85A894");
            float stdDevLineDelta = xAxisWidth / 110f;
            for (int i = 0; i <= 110; i++) {
                float baseY = svgHeight - graphPaddingY - 2;
                float y = (float) (stdDevFunction(i, stdDev, avg) * yAxisHeight * peakStudentCount / totalStudentCount);
                float x = graphPaddingX + i * stdDevLineDelta;
                if (i == 0)
                    stdDevLine.addPoint("M " + x + ' ' + (baseY - y));
                else
                    stdDevLine.addPoint("L " + x + ' ' + (baseY - y));

            }
            svg.appendChild(stdDevLine);

            return svg.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private int[] parseImage(int[] imageRGB, int imageWidth, int imageHeight, boolean debug) {
        int count = 0;
        int[] studentCount = new int[11];
        int lastX = -1;
        for (int y = imageHeight - 1; y > -1; y--) {
            boolean finedData = false;
            for (int x = 0; x < imageWidth; x++) {
                int offset = y * imageWidth + x;
                int pixel = imageRGB[offset] & 0xFFFFFF;

                if (count == studentCount.length)
                    break;

                // Find x split
                boolean last = false;
                if (pixel == 0 || (last = finedData && x == imageWidth - 1)) {
                    finedData = true;
                    if (last)
                        x = lastX + 40;

                    // Find number
                    if (lastX != -1) {
                        int rangeStartY = 0;
                        int rangeEndY = y;
                        // Find axis base
                        for (; rangeEndY > -1; rangeEndY--)
                            if ((imageRGB[rangeEndY * imageWidth + lastX + 1] & 0xFFFF00) != 0xFFFF00)
                                break;
                        rangeEndY--;
                        // Find text base
                        for (; rangeEndY > -1; rangeEndY--) {
                            boolean findStart = false;
                            for (int i = lastX + 1; i < x; i++) {
                                int p = imageRGB[rangeEndY * imageWidth + i] & 0xFFFF00;
                                if (p == 0) break;
                                // Is white
                                if (p == 0xFFFF00) {
                                    findStart = true;
                                    break;
                                }
                            }
                            if (findStart) break;
                        }
                        rangeEndY++;
                        // Find text start
                        int startX = x, startY = rangeEndY, endX = lastX, endY = rangeStartY;
                        boolean findNum = false;
                        boolean findStart = false;
                        for (int i = rangeEndY; i > -1 + 3; i--) {
                            if (findNum)
                                findStart = true;
                            for (int j = lastX + 1; j < x; j++) {
                                int p = imageRGB[i * imageWidth + j] & 0xFF00FF;
                                if (p == 0) break;
                                // Is blue text
                                if (p <= 0x0000FF) {
                                    findNum = true;
                                    findStart = false;
                                    break;
                                }
                                // Check no blue text after
                                int p1 = imageRGB[(i - 1) * imageWidth + j] & 0xFF00FF;
                                int p2 = imageRGB[(i - 2) * imageWidth + j] & 0xFF00FF;
                                int p3 = imageRGB[(i - 3) * imageWidth + j] & 0xFF00FF;
                                if (p1 > 0 && p1 <= 0x0000FF ||
                                        p2 > 0 && p2 <= 0x0000FF ||
                                        p3 > 0 && p3 <= 0x0000FF) {
                                    findStart = false;
                                    break;
                                }
                            }
                            if (findStart) {
                                rangeStartY = i;
                                break;
                            }
                        }
                        // Have zero people
                        if (!findStart) {
                            studentCount[count++] = 0;
                        } else {
                            rangeStartY++;
                            // Find text bound
                            for (int i = lastX + 1; i < x; i++) {
                                for (int j = rangeStartY; j < rangeEndY; j++) {
                                    int p = imageRGB[j * imageWidth + i] & 0xFF00FF;
                                    if (p > 0 && p <= 0x0000FF) {
                                        if (i < startX)
                                            startX = i;
                                        else if (i > endX)
                                            endX = i;
                                        if (j < startY)
                                            startY = j;
                                        else if (j > endY)
                                            endY = j;
                                    }
                                }
                            }
                            endX++;
                            endY++;

                            // Parse number
                            int num = getNumber(imageRGB, imageWidth, startX, startY, endX, endY);
                            if (num != -1)
                                studentCount[count++] = num;

                            // Debug mark
                            if (debug) {
                                for (int i = lastX + 1; i < x; i++) {
//                                    imageRGB[(rangeStartY - 1) * imageWidth + i] = 0xFF00FF;
                                    imageRGB[rangeEndY * imageWidth + i] = 0xFF00FF;
                                }
                                for (int i = startX; i < endX; i++) {
                                    imageRGB[(startY - 1) * imageWidth + i] = 0;
                                }
                                for (int i = startY; i < endY; i++)
                                    imageRGB[i * imageWidth + startX - 1] = 0xFF0000;
                            }
                        }
                    }

                    if (last)
                        break;
                    lastX = x;
                }
            }

            if (finedData)
                if (count < studentCount.length)
                    return null;
                else
                    return studentCount;
        }
        return null;
    }

    private int getNumber(int[] imageRGB, int imageWidth, int startX, int startY, int endX, int endY) {
//        if (endY - startY < 8)
//            return -1;

        int out = 0;
        while (startX < endX) {
            int[] score = new int[numbers.length];
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 5; x++) {
                    int p = imageRGB[(startY + y) * imageWidth + startX + x] & 0xFFFF00;
                    boolean isNum = p != 0xFFFF00;
                    for (int k = 0; k < numbers.length; k++) {
                        boolean is1 = ((numbers[k][y] >> (4 - x)) & 0x1) > 0;
                        if (isNum == is1)
                            score[k]++;
                    }
                }
            }
            int num = 0;
            for (int i = 0; i < score.length; i++) {
                if (score[i] > score[num])
                    num = i;
            }
            out = out * 10 + num;
            startX += 6;
        }

        return out;
    }

//    private final double sqrt2pi = Math.sqrt(2 * Math.PI);

    private double stdDevFunction(double x, double stdDev, double avg) {
        double a = x - avg;
//        return Math.pow(Math.E, -(a * a) / (2 * stdDev * stdDev)) / (stdDev * sqrt2pi);
        return Math.pow(Math.E, -(a * a) / (2 * stdDev * stdDev));
    }
}
