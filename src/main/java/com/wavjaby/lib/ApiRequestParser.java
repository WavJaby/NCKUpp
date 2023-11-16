package com.wavjaby.lib;

import com.wavjaby.logger.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.Map;

import static com.wavjaby.lib.Lib.parseUrlEncodedForm;

public class ApiRequestParser {
    private static final String TAG = "ApiRequestParser";
    private static final Logger logger = new Logger(TAG);

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Required {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Payload {
    }

    public static class ApiRequestLib {
        private final Field[] fields = getClass().getDeclaredFields();
    }

    public static <T> T parseApiRequest(T container, String rawQuery, String rawPayload, ApiResponse response) {
        StringBuilder queryError = response == null ? null : new StringBuilder();
        StringBuilder payloadError = response == null ? null : new StringBuilder();
        ApiRequestLib lib = (ApiRequestLib) container;
        Map<String, String> query = parseUrlEncodedForm(rawQuery);
        Map<String, String> payload = parseUrlEncodedForm(rawPayload);
        for (Field field : lib.fields) {
            boolean fromPayload = field.isAnnotationPresent(Payload.class);
            if (fromPayload && rawPayload == null)
                continue;

            String data = fromPayload
                    ? payload.get(field.getName())
                    : query.get(field.getName());
            if (data == null && field.isAnnotationPresent(Required.class)) {
                if (queryError == null)
                    return null;
                else {
                    if (fromPayload)
                        payloadError.append(", \"").append(field.getName()).append('"');
                    else
                        queryError.append(", \"").append(field.getName()).append('"');
                }
            } else {
                try {
                    if (field.isAccessible())
                        field.set(container, data);
                    else {
                        field.setAccessible(true);
                        field.set(container, data);
                        field.setAccessible(false);
                    }
                } catch (IllegalAccessException e) {
                    logger.errTrace(e);
                }
            }
        }
        if (queryError != null && queryError.length() > 0) {
            response.errorBadQuery("Missing key " + queryError.substring(2));
            return null;
        }
        if (payloadError != null && payloadError.length() > 0) {
            response.errorBadPayload("Missing key " + payloadError.substring(2));
            return null;
        }

        return container;
    }
}
