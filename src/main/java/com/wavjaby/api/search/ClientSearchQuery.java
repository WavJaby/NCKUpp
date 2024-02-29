package com.wavjaby.api.search;

import com.wavjaby.lib.ApiResponse;

import java.util.*;

import static com.wavjaby.lib.Cookie.getCookie;
import static com.wavjaby.lib.Lib.simpleSplit;

public class ClientSearchQuery {
    final String searchID;
    final String departmentId;      // 系所 AX
    final String courseName;        // 課程名稱
    final String instructor;        // 教師姓名
    final Integer grade;             // 年級 1 ~ 4
    final CourseSearchQuery.TimeQuery[] time;

    final Map<String, Set<String>> serialIdNumber;     // <系號,[序號]>
    final boolean getAll;
    final CourseHistorySearchQuery historySearch;

    ClientSearchQuery(String courseSearchID, boolean getAll,
                      Map<String, Set<String>> deptWithSerials,
                      String departmentId,
                      CourseSearchQuery.TimeQuery[] time,
                      String courseName, String instructor, Integer grade,
                      CourseHistorySearchQuery historySearch) {
        this.searchID = courseSearchID;
        this.getAll = getAll;
        this.departmentId = departmentId;
        this.serialIdNumber = deptWithSerials;
        this.time = time;
        this.courseName = courseName;
        this.instructor = instructor;
        this.grade = grade;

        this.historySearch = historySearch;
    }

    public ClientSearchQuery(CourseData courseData) {
        this.departmentId = courseData.departmentId;
        // TODO: Get search ID
        this.searchID = null;
        this.courseName = courseData.courseName;
        this.instructor = courseData.instructors == null ? null : courseData.instructors[0];
        this.grade = courseData.forGrade;
        Byte dayOfWeek = null, sectionOfDay = null;
        if (courseData.timeList != null) {
            for (CourseData.TimeData time : courseData.timeList) {
                if (time.sectionStart == null) continue;
                dayOfWeek = time.dayOfWeek;
                sectionOfDay = time.sectionStart;
                break;
            }
            // if no section
            if (dayOfWeek == null)
                dayOfWeek = courseData.timeList[0].dayOfWeek;
            this.time = CourseSearchQuery.TimeQuery.singleSection(dayOfWeek, sectionOfDay);
        } else
            this.time = null;

        this.getAll = false;
        this.serialIdNumber = null;
        // TODO: Support history course
        this.historySearch = null;
    }

    public ClientSearchQuery(ClientSearchQuery clientSearchQuery) {
        this.searchID = clientSearchQuery.searchID;
        this.departmentId = clientSearchQuery.departmentId;
        this.courseName = clientSearchQuery.courseName;
        this.instructor = clientSearchQuery.instructor;
        this.grade = clientSearchQuery.grade;

        if (clientSearchQuery.time != null) {
            this.time = new CourseSearchQuery.TimeQuery[clientSearchQuery.time.length];
            for (int i = 0; i < this.time.length; i++)
                this.time[i] = new CourseSearchQuery.TimeQuery(clientSearchQuery.time[i]);
        } else
            this.time = null;

        if (clientSearchQuery.serialIdNumber != null) {
            this.serialIdNumber = new HashMap<>(clientSearchQuery.serialIdNumber.size());
            // Deep clone
            for (Map.Entry<String, Set<String>> entry : clientSearchQuery.serialIdNumber.entrySet())
                this.serialIdNumber.put(entry.getKey(), new HashSet<>(entry.getValue()));
        } else
            serialIdNumber = null;

        this.getAll = clientSearchQuery.getAll;

        this.historySearch = clientSearchQuery.historySearch;
    }

    public static ClientSearchQuery fromRequest(Map<String, String> query, String[] cookieIn, ApiResponse response) {
        // Get search ID
        String searchID = getCookie("searchID", cookieIn);

        // Get with serial
        String rawSerial = query.get("serial");
        Map<String, Set<String>> serialIdNumber = null;
        if (rawSerial != null) {
            serialIdNumber = new HashMap<>();
            // Text type
            String[] deptSerialArr = simpleSplit(rawSerial, ',');
            for (String i : deptSerialArr) {
                int index = i.indexOf('-');
                if (index == -1) {
                    response.errorBadQuery("'serial' Format error: '" + i + "', use '-' to separate dept id and serial number, EX: F7-001.");
                    return null;
                }
                Set<String> serial = serialIdNumber.computeIfAbsent(i.substring(0, index), k -> new HashSet<>());
                String serialId = i.substring(index + 1);
                // TODO: Check dept id and serial number
                serial.add(serialId);
            }
        }

        // History search
        String queryTimeBegin = query.get("semBegin"), queryTimeEnd = query.get("semEnd");
        CourseHistorySearchQuery historySearch = null;
        if (queryTimeBegin != null || queryTimeEnd != null) {
            int yearBegin = -1, semBegin = -1, yearEnd = -1, semEnd = -1;
            if (queryTimeBegin == null) {
                response.errorBadQuery("History search missing 'timeBegin' key in query.");
                return null;
            } else if (queryTimeEnd == null) {
                response.errorBadQuery("History search missing 'timeEnd' key in query.");
                return null;
            } else {
                int startSplit = queryTimeBegin.indexOf('-');
                if (startSplit == -1) {
                    response.errorBadQuery("'semBegin' Format error: use '-' to separate year and semester, EX: 111-1.");
                    return null;
                } else {
                    try {
                        yearBegin = Integer.parseInt(queryTimeBegin.substring(0, startSplit));
                        semBegin = Integer.parseInt(queryTimeBegin.substring(startSplit + 1));
                    } catch (NumberFormatException e) {
                        response.errorBadQuery("'semBegin' Format error: " + e.getMessage() + '.');
                        return null;
                    }
                }

                int endSplit = queryTimeEnd.indexOf('-');
                if (endSplit == -1) {
                    response.errorBadQuery("'semEnd' Format error: use '-' to separate year and semester, EX: 111-1.");
                    return null;
                } else {
                    try {
                        yearEnd = Integer.parseInt(queryTimeEnd.substring(0, endSplit));
                        semEnd = Integer.parseInt(queryTimeEnd.substring(endSplit + 1));
                    } catch (NumberFormatException e) {
                        response.errorBadQuery("'semEnd' Format error: " + e.getMessage() + '.');
                        return null;
                    }
                }
            }
            historySearch = new CourseHistorySearchQuery(yearBegin, semBegin, yearEnd, semEnd);
        }

        // Parse time query
        String timeRaw = query.get("time");                   // 時間 [星期)節次~節次_節次_節次...] (星期: 0~6, 節次: 0~15)
        CourseSearchQuery.TimeQuery[] timeQueries = null;
        if (timeRaw != null) {
            List<CourseSearchQuery.TimeQuery> timeQuerieList = new ArrayList<>();
            boolean[][] timeTable = new boolean[7][];
            String[] timeArr = simpleSplit(timeRaw, ',');
            for (String time : timeArr) {
                int dayOfWeekIndex = time.indexOf(')');

                byte dayOfWeek;
                boolean allDay = false;
                boolean noSection = false;
                if (dayOfWeekIndex != -1 || (noSection = time.indexOf('~') == -1 && time.indexOf('_') == -1)) {
                    if (noSection)
                        dayOfWeekIndex = time.length();
                    try {
                        int d = Integer.parseInt(time.substring(0, dayOfWeekIndex));
                        if (d < 0 || d > 6) {
                            response.errorBadQuery("'time' Format error: day of week should >= 0 and <= 6.");
                            return null;
                        }
                        dayOfWeek = (byte) d;
                    } catch (NumberFormatException e) {
                        response.errorBadQuery("'time' Format error: day of week '" + time.substring(0, dayOfWeekIndex) + "' is not a number.");
                        return null;
                    }
                    if (noSection)
                        Arrays.fill(timeTable[dayOfWeek] = new boolean[16], true);
                } else {
                    dayOfWeek = 6;
                    allDay = true;
                }
                if (!noSection) {
                    // Parse section
                    String sectionOfDayRaw = time.substring(dayOfWeekIndex + 1);
                    for (int i = allDay ? 0 : dayOfWeek; i <= dayOfWeek; i++) {
                        boolean[] sections = timeTable[i];
                        if (sections == null)
                            sections = timeTable[i] = new boolean[16];
                        String[] sectionOfDays = simpleSplit(sectionOfDayRaw, '_');
                        // [section~section, section, section]
                        for (String section : sectionOfDays) {
                            int index = section.indexOf('~');
                            byte sectionStart, sectionEnd;
                            // Parse section end
                            try {
                                int s = Integer.parseInt(section.substring(index + 1));
                                if (s < 0 || s > 15) {
                                    response.errorBadQuery("'time' Format error: section start should >= 0 and <= 15.");
                                    return null;
                                }
                                sectionEnd = (byte) s;
                            } catch (NumberFormatException e) {
                                response.errorBadQuery("'time' Format error: section start '" + section + "' is not a number.");
                                return null;
                            }
                            // Parse section start
                            if (index == -1) {
                                sectionStart = sectionEnd;
                            } else {
                                try {
                                    int s = Integer.parseInt(section.substring(0, index));
                                    if (s < 0 || s > 15) {
                                        response.errorBadQuery("'time' Format error: section end should >= 0 and <= 15.");
                                        return null;
                                    }
                                    sectionStart = (byte) s;
                                } catch (NumberFormatException e) {
                                    response.errorBadQuery("'time' Format error: section end '" + section + "' is not a number.");
                                    return null;
                                }
                            }
                            for (int j = sectionStart; j <= sectionEnd; j++)
                                sections[j] = true;
                        }
                    }
                }
            }
            List<Byte> selectedSections = new ArrayList<>();
            for (byte i = 0; i < timeTable.length; i++) {
                boolean[] sections = timeTable[i];
                if (sections == null)
                    continue;
                selectedSections.clear();
                for (byte j = 0; j < sections.length; j++) {
                    if (!sections[j])
                        continue;
                    selectedSections.add(j);
                }
                byte[] b = new byte[selectedSections.size()];
                for (int j = 0; j < selectedSections.size(); j++)
                    b[j] = selectedSections.get(j);
                timeQuerieList.add(new CourseSearchQuery.TimeQuery(i, b));
            }
            if (!timeQuerieList.isEmpty())
                timeQueries = timeQuerieList.toArray(new CourseSearchQuery.TimeQuery[0]);
        }

        // Normal parameters
        String courseName = query.get("courseName");          // 課程名稱
        String instructor = query.get("instructor");          // 教師姓名
        String gradeRaw = query.get("grade");                 // 年級 1 ~ 4
        Integer grade = null;
        if (gradeRaw != null)
            try {
                grade = Integer.parseInt(gradeRaw);
            } catch (NumberFormatException e) {
                response.errorBadQuery("'grade' Format error: '" + gradeRaw + "' is not a number.");
                return null;
            }
        String departmentId = query.get("dept");

        boolean getAll = "ALL".equals(departmentId);

        if (!getAll &&
                departmentId == null &&
                courseName == null &&
                instructor == null &&
                grade == null &&
                timeQueries == null &&
                serialIdNumber == null) {
            response.errorBadQuery("Empty query");
            return null;
        }

        return new ClientSearchQuery(searchID,
                getAll,
                serialIdNumber,
                departmentId,
                timeQueries,
                courseName,
                instructor,
                grade,
                historySearch
        );
    }

    public CourseSearchQuery[] toCourseQueriesSerial() {
        CourseSearchQuery[] searchQueries = new CourseSearchQuery[serialIdNumber.size()];
        int i = 0;
        for (Map.Entry<String, Set<String>> entry : serialIdNumber.entrySet()) {
            searchQueries[i++] = new CourseSearchQuery(this, entry.getKey(), entry.getValue());
        }
        return searchQueries;
    }

    public CourseSearchQuery[] toCourseQueriesMultiTime() {
        CourseSearchQuery[] searchQueries = new CourseSearchQuery[time.length];
        for (int i = 0; i < time.length; i++) {
            searchQueries[i] = new CourseSearchQuery(this, time[i]);
        }
        return searchQueries;
    }

    public CourseSearchQuery toCourseQuery() {
        return new CourseSearchQuery(this, time == null ? null : time[0]);
    }

    boolean getAll() {
        return getAll;
    }

    boolean getSerial() {
        return serialIdNumber != null;
    }

    boolean multipleTime() {
        return time != null && time.length > 1;
    }
}
