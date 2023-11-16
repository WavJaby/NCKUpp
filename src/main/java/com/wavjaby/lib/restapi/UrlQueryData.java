package com.wavjaby.lib.restapi;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UrlQueryData {
    private static final String charset = StandardCharsets.UTF_8.name();
    public final Map<String, List<String>> keyVal = new HashMap<>();
    public final String rawQuery;

    public UrlQueryData() {
        rawQuery = "";
    }


    public UrlQueryData(String data) {
        rawQuery = data;
        if (data == null || data.isEmpty())
            return;

        char[] arr = data.toCharArray();
        int start = 0;
        String key = null;
        try {
            for (int i = 0; i < arr.length; i++) {
                if (key == null && arr[i] == '=') {
                    key = URLDecoder.decode(data.substring(start, i), charset);
                    start = i + 1;
                }
                boolean end = i + 1 == arr.length;
                if (arr[i] == '&' || end) {
                    String value = URLDecoder.decode(data.substring(start, end ? i + 1 : i), charset);
                    if (key != null) {
                        List<String> values = keyVal.computeIfAbsent(key, (j) -> new ArrayList<>(1));
                        values.add(value);
                        key = null;
                    } else if (!value.isEmpty()) {
                        List<String> values = keyVal.computeIfAbsent(value, (j) -> new ArrayList<>(1));
                        values.add("");
                    }
                    start = i + 1;
                }
            }
        } catch (UnsupportedEncodingException ignored) {
        }
    }

    public void put(String key, String value) {
        if ((key == null || key.isEmpty()) && (value == null || value.isEmpty()))
            return;
        List<String> values = keyVal.computeIfAbsent(key == null ? "" : key, (j) -> new ArrayList<>(1));
        values.add(value == null ? "" : value);
    }

    public String getFirst(String key) {
        List<String> values = keyVal.get(key);
        return values == null ? null : values.get(0);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : keyVal.entrySet()) {
            for (String s : entry.getValue()) {
                try {
                    builder.append(URLEncoder.encode(entry.getKey(), charset)).append('=')
                            .append(URLEncoder.encode(s, charset)).append('&');
                } catch (UnsupportedEncodingException ignored) {
                }
            }
        }
        return builder.length() == 0 ? "" : builder.substring(0, builder.length() - 1);
    }
}
