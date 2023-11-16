package com.wavjaby.api.search;

import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObjectStringBuilder;

public class CourseData {
    final String semester;
    final String departmentName; // Can be null
    final String serialNumber; // Can be null
    final String courseAttributeCode;
    final String courseSystemNumber;
    final Integer forGrade;  // Can be null
    final String forClass; // Can be null
    final String group;  // Can be null
    final String category;
    final String courseName;
    final String courseNote; // Can be null
    final String courseLimit; // Can be null
    final TagData[] tags; // Can be null
    final Float credits; // Can be null
    final Boolean required; // Can be null
    final String[] instructors; // Can be null
    final Integer selected; // Can be null
    final Integer available; // Can be null
    final TimeData[] timeList; // Can be null
    final String moodle; // Can be null
    final String btnPreferenceEnter; // Can be null
    final String btnAddCourse; // Can be null
    final String btnPreRegister; // Can be null
    final String btnAddRequest; // Can be null

    public CourseData(String semester,
                      String departmentName,
                      String serialNumber, String courseAttributeCode, String courseSystemNumber,
                      Integer forGrade, String forClass, String group,
                      String category,
                      String courseName, String courseNote, String courseLimit, TagData[] tags,
                      Float credits, Boolean required,
                      String[] instructors,
                      Integer selected, Integer available,
                      TimeData[] timeList,
                      String moodle,
                      String btnPreferenceEnter, String btnAddCourse, String btnPreRegister, String btnAddRequest) {
        this.semester = semester;
        this.departmentName = departmentName;
        this.serialNumber = serialNumber;
        this.courseAttributeCode = courseAttributeCode;
        this.courseSystemNumber = courseSystemNumber;
        this.forGrade = forGrade;
        this.forClass = forClass;
        this.group = group;
        this.category = category;
        this.courseName = courseName;
        this.courseNote = courseNote;
        this.courseLimit = courseLimit;
        this.tags = tags;
        this.credits = credits;
        this.required = required;
        this.instructors = instructors;
        this.selected = selected;
        this.available = available;
        this.timeList = timeList;
        this.moodle = moodle;
        this.btnPreferenceEnter = btnPreferenceEnter;
        this.btnAddCourse = btnAddCourse;
        this.btnPreRegister = btnPreRegister;
        this.btnAddRequest = btnAddRequest;
    }

    static class TagData {
        final String tag;
        final String url; // Can be null
        final String colorID;

        public TagData(String tag, String colorID, String url) {
            this.tag = tag;
            this.url = url;
            this.colorID = colorID;
        }

        @Override
        public String toString() {
            if (url == null)
                return tag + ',' + colorID + ',';
            return tag + ',' + colorID + ',' + url;
        }
    }

    static class TimeData {
        /**
         * Can be null, 0 ~ 6
         */
        final Byte dayOfWeek;
        /**
         * Can be null, 0 ~ 15
         */
        final Byte sectionStart;
        /**
         * Can be null, 0 ~ 15
         */
        final Byte sectionEnd;
        final String mapLocation; // Can be null
        final String mapRoomNo; // Can be null
        final String mapRoomName; // Can be null
        // Detailed time data
        final String detailedTimeData; // Can be null

        public TimeData(Byte dayOfWeek, Byte sectionStart, Byte sectionEnd,
                        String mapLocation, String mapRoomNo, String mapRoomName) {
            this.dayOfWeek = dayOfWeek;
            this.sectionStart = sectionStart;
            this.sectionEnd = sectionEnd;
            this.mapLocation = mapLocation;
            this.mapRoomNo = mapRoomNo;
            this.mapRoomName = mapRoomName;
            this.detailedTimeData = null;
        }

        public TimeData(String detailedTimeData) {
            this.dayOfWeek = null;
            this.sectionStart = null;
            this.sectionEnd = null;
            this.mapLocation = null;
            this.mapRoomNo = null;
            this.mapRoomName = null;
            this.detailedTimeData = detailedTimeData;
        }

        @Override
        public String toString() {
            if (detailedTimeData != null)
                return detailedTimeData;

            StringBuilder builder = new StringBuilder();
            if (dayOfWeek != null) builder.append(dayOfWeek);
            builder.append(',');
            if (sectionStart != null) builder.append(sectionStart);
            builder.append(',');
            if (sectionEnd != null) builder.append(sectionEnd);
            builder.append(',');
            if (mapLocation != null) builder.append(mapLocation);
            builder.append(',');
            if (mapRoomNo != null) builder.append(mapRoomNo);
            builder.append(',');
            if (mapRoomName != null) builder.append(mapRoomName);
            return builder.toString();
        }
    }

    private JsonArrayStringBuilder toJsonArray(Object[] array) {
        if (array == null) return null;
        JsonArrayStringBuilder builder = new JsonArrayStringBuilder();
        for (Object i : array)
            builder.append(i.toString());
        return builder;
    }

    @Override
    public String toString() {
        // output
        JsonObjectStringBuilder jsonBuilder = new JsonObjectStringBuilder();
        jsonBuilder.append("y", semester);
        jsonBuilder.append("dn", departmentName);

        jsonBuilder.append("sn", serialNumber);
        jsonBuilder.append("ca", courseAttributeCode);
        jsonBuilder.append("cs", courseSystemNumber);

        if (forGrade == null) jsonBuilder.append("g");
        else jsonBuilder.append("g", forGrade);
        jsonBuilder.append("co", forClass);
        jsonBuilder.append("cg", group);

        jsonBuilder.append("ct", category);

        jsonBuilder.append("cn", courseName);
        jsonBuilder.append("ci", courseNote);
        jsonBuilder.append("cl", courseLimit);
        jsonBuilder.append("tg", toJsonArray(tags));

        if (credits == null) jsonBuilder.append("c");
        else jsonBuilder.append("c", credits);
        if (required == null) jsonBuilder.append("r");
        else jsonBuilder.append("r", required);

        jsonBuilder.append("i", toJsonArray(instructors));

        if (selected == null) jsonBuilder.append("s");
        else jsonBuilder.append("s", selected);
        if (available == null) jsonBuilder.append("a");
        else jsonBuilder.append("a", available);

        jsonBuilder.append("t", toJsonArray(timeList));
        jsonBuilder.append("m", moodle);
        jsonBuilder.append("pe", btnPreferenceEnter);
        jsonBuilder.append("ac", btnAddCourse);
        jsonBuilder.append("pr", btnPreRegister);
        jsonBuilder.append("ar", btnAddRequest);
        return jsonBuilder.toString();
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getGroup() {
        return group;
    }

    public String getForClass() {
        return forClass;
    }

    public String getBtnAddCourse() {
        return btnAddCourse;
    }

    public String getBtnPreRegister() {
        return btnPreRegister;
    }

    public String getBtnAddRequest() {
        return btnAddRequest;
    }

    public String getTimeString() {
        if (timeList == null)
            return null;

        StringBuilder builder = new StringBuilder();
        for (TimeData i : timeList) {
            if (i.detailedTimeData != null || i.dayOfWeek == null) continue;
            if (builder.length() > 0)
                builder.append(',');

            builder.append('[').append(i.dayOfWeek + 1).append(']');
            if (i.sectionStart != null) {
                if (i.sectionEnd != null)
                    builder.append(i.sectionStart).append('~').append(i.sectionEnd);
                else
                    builder.append(i.sectionStart);
            }
        }
        return builder.toString();
    }

    public String getCourseName() {
        return courseName;
    }

    public Integer getSelected() {
        return selected;
    }

    public Integer getAvailable() {
        return available;
    }
}
