package com.wavjaby.api.search;

import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;

import java.util.Objects;

public class CourseData {
    final String semester;
    final String departmentId;
    final Integer serialNumber; // Nullable
    final String attributeCode;
    final String systemNumber;
    final Integer forGrade;  // Nullable
    final String forClass; // Nullable
    final String forClassGroup;  // Nullable
    final String category;  // Nullable
    final String courseName; // Maybe empty
    final String courseNote; // Nullable
    final String courseLimit; // Nullable
    final TagData[] tags; // Nullable
    final Float credits; // Nullable
    final Boolean required; // Nullable
    final String[] instructors; // Nullable
    final Integer selected; // Nullable
    final Integer available; // Nullable
    final TimeData[] timeList; // Nullable

    // User action token(require login)
    final String btnPreferenceEnter; // Nullable
    final String btnAddCourse; // Nullable
    final String btnPreRegister; // Nullable
    final String btnAddRequest; // Nullable


    public CourseData(JsonObject jsonObject) {
        // output
        this.semester = jsonObject.getString("y");
        this.departmentId = jsonObject.getString("dn");

        this.serialNumber = (Integer) jsonObject.getObject("sn");
        this.attributeCode = jsonObject.getString("ca");
        this.systemNumber = jsonObject.getString("cs");

        if (jsonObject.getObject("g") != null)
            this.forGrade = jsonObject.getInt("g");
        else
            this.forGrade = null;
        this.forClass = jsonObject.getString("co");
        this.forClassGroup = jsonObject.getString("cg");

        this.category = jsonObject.getString("ct");

        this.courseName = jsonObject.getString("cn");
        this.courseNote = jsonObject.getString("ci");
        this.courseLimit = jsonObject.getString("cl");
        if (jsonObject.getObject("tg") != null)
            this.tags = jsonObject.getArray("tg").stream().map(i -> TagData.fromString((String) i)).toArray(TagData[]::new);
        else this.tags = null;

        if (jsonObject.getObject("c") != null)
            this.credits = jsonObject.getFloat("c");
        else this.credits = null;
        if (jsonObject.getObject("r") != null)
            this.required = jsonObject.getBoolean("r");
        else this.required = null;

        if (jsonObject.getObject("i") != null)
            this.instructors = jsonObject.getArray("i").stream().map(i -> (String) i).toArray(String[]::new);
        else this.instructors = null;

        if (jsonObject.getObject("s") != null)
            this.selected = jsonObject.getInt("s");
        else this.selected = null;
        if (jsonObject.getObject("a") != null)
            this.available = jsonObject.getInt("a");
        else this.available = null;

        if (jsonObject.getObject("t") != null)
            this.timeList = jsonObject.getArray("t").stream().map(i -> TimeData.fromString((String) i)).toArray(TimeData[]::new);
        else this.timeList = null;

        this.btnPreferenceEnter = jsonObject.getString("pe");
        this.btnAddCourse = jsonObject.getString("ac");
        this.btnPreRegister = jsonObject.getString("pr");
        this.btnAddRequest = jsonObject.getString("ar");
    }

    public CourseData(String semester,
                      String departmentId,
                      Integer serialNumber, String systemNumber, String attributeCode,
                      Integer forGrade, String forClass, String forClassGroup,
                      String category,
                      String courseName, String courseNote, String courseLimit, TagData[] tags,
                      Float credits, Boolean required,
                      String[] instructors,
                      Integer selected, Integer available,
                      TimeData[] timeList,
                      String btnPreferenceEnter, String btnAddCourse, String btnPreRegister, String btnAddRequest) {
        this.semester = semester;
        this.departmentId = departmentId;
        this.serialNumber = serialNumber;
        this.systemNumber = systemNumber;
        this.attributeCode = attributeCode;
        this.forGrade = forGrade;
        this.forClass = forClass;
        this.forClassGroup = forClassGroup;
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
        this.btnPreferenceEnter = btnPreferenceEnter;
        this.btnAddCourse = btnAddCourse;
        this.btnPreRegister = btnPreRegister;
        this.btnAddRequest = btnAddRequest;
    }

    public static class TagData {
        final String name;
        final String colorID;
        final String url; // Can be null

        public TagData(String name, String colorID, String url) {
            this.name = name;
            this.colorID = colorID;
            this.url = url;
        }

        public static TagData fromString(String raw) {
            String[] s = raw.split("\\|");
            return new TagData(s[0], s[1], s.length == 3 ? s[2] : null);
        }

        @Override
        public String toString() {
            if (url == null)
                return name + '|' + colorID + '|';
            return name + '|' + colorID + '|' + url;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TagData) {
                TagData other = (TagData) obj;
                return Objects.equals(other.name, name) &&
                        Objects.equals(other.colorID, colorID) &&
                        Objects.equals(other.url, url);
            }
            return false;
        }
    }

    public static class TimeData {
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
        final String buildingId; // Can be null
        final String roomId; // Can be null
        final String roomName; // Can be null
        // Detailed time data
        final String detailedTimeData; // Can be null

        public TimeData(Byte dayOfWeek, Byte sectionStart, Byte sectionEnd,
                        String buildingId, String roomId, String roomName) {
            this.dayOfWeek = dayOfWeek;
            this.sectionStart = sectionStart;
            this.sectionEnd = sectionEnd;
            this.buildingId = buildingId;
            this.roomId = roomId;
            this.roomName = roomName;
            this.detailedTimeData = null;
        }

        public TimeData(String detailedTimeData) {
            this.dayOfWeek = null;
            this.sectionStart = null;
            this.sectionEnd = null;
            this.buildingId = null;
            this.roomId = null;
            this.roomName = null;
            this.detailedTimeData = detailedTimeData;
        }

        public static TimeData fromString(String raw) {
            String[] s = raw.split(",", 6);
            if (s.length == 1)
                return new TimeData(s[0]);
            return new TimeData(
                    s[0].isEmpty() ? null : Byte.parseByte(s[0]),
                    s[1].isEmpty() ? null : Byte.parseByte(s[1]),
                    s[2].isEmpty() ? null : Byte.parseByte(s[2]),
                    s[3].isEmpty() ? null : s[3],
                    s[4].isEmpty() ? null : s[4],
                    s[5].isEmpty() ? null : s[5]);
        }

        public boolean roomExist() {
            return this.buildingId != null &&
                    this.roomId != null &&
                    this.roomName != null;
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
            if (buildingId != null) builder.append(buildingId);
            builder.append(',');
            if (roomId != null) builder.append(roomId);
            builder.append(',');
            if (roomName != null) builder.append(roomName);
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
        jsonBuilder.append("dn", departmentId);

        jsonBuilder.append("sn", serialNumber);
        jsonBuilder.append("ca", attributeCode);
        jsonBuilder.append("cs", systemNumber);

        if (forGrade == null) jsonBuilder.append("g");
        else jsonBuilder.append("g", forGrade);
        jsonBuilder.append("co", forClass);
        jsonBuilder.append("cg", forClassGroup);

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

        if (btnPreferenceEnter != null)
            jsonBuilder.append("pe", btnPreferenceEnter);
        if (btnAddCourse != null)
            jsonBuilder.append("ac", btnAddCourse);
        if (btnPreRegister != null)
            jsonBuilder.append("pr", btnPreRegister);
        if (btnAddRequest != null)
            jsonBuilder.append("ar", btnAddRequest);
        return jsonBuilder.toString();
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public Integer getSerialNumber() {
        return serialNumber;
    }

    public String getSystemNumber() {
        return systemNumber;
    }

    public String[] getInstructors() {
        return instructors;
    }

    public String getForClassGroup() {
        return forClassGroup;
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
