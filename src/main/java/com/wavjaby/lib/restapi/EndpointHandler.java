package com.wavjaby.lib.restapi;

import com.sun.net.httpserver.HttpExchange;
import com.wavjaby.json.JsonArrayStringBuilder;
import com.wavjaby.json.JsonException;
import com.wavjaby.json.JsonObject;
import com.wavjaby.json.JsonObjectStringBuilder;
import com.wavjaby.lib.ArraysLib;
import com.wavjaby.lib.restapi.request.RequestBody;
import com.wavjaby.lib.restapi.request.RequestParam;
import com.wavjaby.logger.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class EndpointHandler {
    private static final Logger logger = new Logger("Endpoint");
    public static final EndpointHandler EMPTY_ENDPOINT = new EndpointHandler();
    private final PathSegment[] absolutePath;
    private final RequestMethod method;
    private final Method handler;
    private final Object classObject;
    private final ParameterCreator[] handlerParams;
    public final boolean selfHandleResponse;

    private interface ParameterCreator {
        Object create(HttpExchange exchange, UrlQueryData query, JsonObject json);
    }

    private static class RequestBodyCreator implements ParameterCreator {
        final Class<?> targetClass;
        final Field[] keys;

        private RequestBodyCreator(Class<?> targetClass, Field[] keys) {
            this.targetClass = targetClass;
            this.keys = keys;
        }

        @Override
        public Object create(HttpExchange exchange, UrlQueryData query, JsonObject requestBody) {
            Object obj;
            try {
                obj = targetClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                logger.errTrace(e);
                return null;
            }
            for (Field field : keys) {
                try {
                    field.set(obj, field.getType().cast(requestBody.getObject(field.getName())));
                } catch (IllegalArgumentException | IllegalAccessException | ClassCastException e) {
                    logger.errTrace(e);
                    return null;
                }
            }
            return obj;
        }
    }

    private static class RequestParamCreator implements ParameterCreator {
        final String key;

        private RequestParamCreator(String key) {
            this.key = key;
        }

        @Override
        public Object create(HttpExchange exchange, UrlQueryData query, JsonObject requestBody) {
            return query.getFirst(key);
        }
    }

    private static class RequestHttpExchangeCreator implements ParameterCreator {
        @Override
        public Object create(HttpExchange exchange, UrlQueryData query, JsonObject requestBody) {
            return exchange;
        }
    }

    public EndpointHandler() {
        this.absolutePath = null;
        this.method = null;
        this.handler = null;
        this.selfHandleResponse = false;
        this.classObject = null;
        this.handlerParams = null;
    }

    public EndpointHandler(PathSegment[] absolutePath, RequestMethod method, boolean selfHandleResponse, Method handler, Object classObject) {
        this.absolutePath = absolutePath;
        this.method = method;
        this.selfHandleResponse = selfHandleResponse;
        this.handler = handler;
        this.classObject = classObject;

        // Parse request object
        List<ParameterCreator> handlerParams = new ArrayList<>();
        for (Parameter parameter : handler.getParameters()) {
            if (parameter.getType() == HttpExchange.class) {
                handlerParams.add(new RequestHttpExchangeCreator());
                continue;
            }
            RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
            if (requestBody != null) {
                Class<?> classType = parameter.getType();
                Field[] fields = classType.getDeclaredFields();
                for (Field key : fields) {
                    if (!key.isAccessible())
                        key.setAccessible(true);
                }
                handlerParams.add(new RequestBodyCreator(classType, fields));
                continue;
            }
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (requestParam != null) {
                String key = requestParam.value()[0];
                handlerParams.add(new RequestParamCreator(key));
                continue;
            }
        }
        this.handlerParams = handlerParams.toArray(new ParameterCreator[0]);
        handlerParams.clear();
    }

    public boolean checkPathMatch(String[] segments, RequestMethod method) {
        if (this.method != method || segments.length < absolutePath.length)
            return false;
        for (int i = 0; i < absolutePath.length; i++) {
            if (absolutePath[i].matchAllAfter)
                return true;
            if (!absolutePath[i].isMatch(segments[i]))
                return false;
        }
        return segments.length <= absolutePath.length;
    }


    public RestApiResponse onEndpoint(HttpExchange exchange) {
        // Read body
        String body;
        try {
            body = readInputStreamToString(exchange.getRequestBody(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.errTrace(e);
            return new RestApiResponse(500, false, "Failed to read payload: " + e.getMessage());
        }
        UrlQueryData query = new UrlQueryData(exchange.getRequestURI().getRawQuery());
        Object[] params = new Object[handlerParams.length];
        JsonObject json = null;
        if (!body.isEmpty()) {
            // Parse json
            try {
                json = new JsonObject(body);
            } catch (JsonException e) {
                logger.errTrace(e);
                return new RestApiResponse(400, false, "Failed to parse JSON payload: " + e.getMessage());
            }
        }
        // Create params
        for (int i = 0; i < params.length; i++) {
            params[i] = handlerParams[i].create(exchange, query, json);
        }

        // Invoke endpoint method
        Object result;
        try {
            result = handler.invoke(classObject, params);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            logger.errTrace(e);
            return new RestApiResponse(500, false, "Internal server error: " + e.getMessage());
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getCause();
            logger.errTrace(throwable);
            return new RestApiResponse(500, false, "Internal server error: " + throwable.getClass().getName() + ": " + throwable.getMessage());
        }
        if (selfHandleResponse)
            return null;

        if (result instanceof RestApiResponse) {
            return (RestApiResponse) result;
        }

        // Generate jsonObject
        return new RestApiResponse(200, result == null ? null : objetToJSON(result));
    }


    private static String objetToJSON(Object object) {
        if (object == null) return "null";
        else if (object instanceof String) {
            StringBuilder builder = new StringBuilder();
            JsonObject.makeQuote((String) object, builder);
            return builder.toString();
        } else if (object instanceof Number) return object.toString();
        else if (object instanceof boolean[]) return ArraysLib.toString((boolean[]) object);
        else if (object instanceof char[]) return ArraysLib.toString((char[]) object);
        else if (object instanceof byte[]) return ArraysLib.toString((byte[]) object);
        else if (object instanceof short[]) return ArraysLib.toString((short[]) object);
        else if (object instanceof int[]) return ArraysLib.toString((int[]) object);
        else if (object instanceof long[]) return ArraysLib.toString((long[]) object);
        else if (object instanceof float[]) return ArraysLib.toString((float[]) object);
        else if (object instanceof double[]) return ArraysLib.toString((double[]) object);
        else if (object instanceof Object[]) {
            JsonArrayStringBuilder builder = new JsonArrayStringBuilder();
            for (Object o : ((Object[]) object)) builder.appendRaw(objetToJSON(o));
            return builder.toString();
        } else if (object instanceof Iterable<?>) {
            JsonArrayStringBuilder builder = new JsonArrayStringBuilder();
            for (Object o : ((Iterable<?>) object)) builder.appendRaw(objetToJSON(o));
            return builder.toString();
        } else {
            JsonObjectStringBuilder builder = new JsonObjectStringBuilder();
            for (Field field : object.getClass().getFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !Modifier.isPublic(field.getModifiers()))
                    continue;
                Object value;
                try {
                    value = field.get(object);
                    if (value == object)
                        throw new IllegalArgumentException("Recursion found at: " + field.getDeclaringClass() + '(' + field.getType() + ' ' + field.getName());
                } catch (IllegalAccessException | IllegalArgumentException e) {
                    logger.errTrace(e);
                    continue;
                }
                builder.appendRaw(field.getName(), objetToJSON(value));
            }
            return builder.toString();
        }
    }

    public String getMethodName() {
        return method.name();
    }

    public String getPath() {
        StringBuilder builder = new StringBuilder();
        for (PathSegment segment : absolutePath) {
            boolean f = true;
            for (String s : segment.pattern) {
                if (f) f = false;
                else builder.append('*');
                builder.append(s);
            }
            builder.append('/');
        }
        return builder.substring(0, builder.length() - 1);
    }

    int getMethodIndex() {
        return method.ordinal();
    }

    int getMatchWordCount() {
        int sum = 0;
        for (PathSegment segment : absolutePath) {
            sum += segment.pattern.length;
        }
        return sum;
    }

    int getMatchWordLength() {
        int sum = 0;
        for (PathSegment segment : absolutePath) {
            for (String s : segment.pattern) {
                sum += s.length();
            }
        }
        return sum;
    }

    private static String readInputStreamToString(InputStream in, Charset charset) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buff = new byte[1024];
        int len;
        while ((len = in.read(buff, 0, buff.length)) > 0)
            out.write(buff, 0, len);
        in.close();
        return out.toString(charset.name());
    }
}
