package com.wavjaby.lib.restapi;

import com.sun.net.httpserver.HttpHandler;
import com.wavjaby.lib.HttpServer;
import com.wavjaby.lib.PropertiesReader;
import com.wavjaby.lib.restapi.request.CustomResponse;
import com.wavjaby.logger.Logger;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.wavjaby.lib.restapi.EndpointHandler.EMPTY_ENDPOINT;

public class RestApiServer extends HttpServer {
    private static final Logger logger = new Logger("REST API");
    private final PathSegmentNode root = new PathSegmentNode();
    private final RestApiResponse endpointNotFoundError =
            new RestApiResponse(404, false, "Endpoint not found");
    private final RestApiResponse endpointRuntimeError =
            new RestApiResponse(500, false, "Internal Server Error");

    private final HttpHandler handler = exchange -> {
        String endpointPath = exchange.getRequestURI().getRawPath();
        String method = exchange.getRequestMethod();
        EndpointHandler handler = root.getEndpoint(splitPath(endpointPath), RequestMethod.getMethod(method));

        boolean error = false;
        RestApiResponse response;
        // Endpoint not found
        if (handler == null) {
            response = endpointNotFoundError;
            error = true;
        }
        // Endpoint method not allowed
        else if (handler == EMPTY_ENDPOINT) {
            response = new RestApiResponse(
                    405,
                    false, "Method '" + method + "' not allowed at: '" + endpointPath + "'"
            );
            error = true;
        } else {
            try {
                response = handler.onEndpoint(exchange);
            } catch (Exception e) {
                logger.errTrace(e);
                response = endpointRuntimeError;
                error = true;
            }
        }
        if (handler == null || !handler.selfHandleResponse && !error) {
            response.sendResponse(exchange);
        }
    };

    public RestApiServer(PropertiesReader serverSettings) {
        super(serverSettings);
    }

    @Override
    @Deprecated
    public void createContext(String path, HttpHandler handler) {
        throw new NotImplementedException();
    }

    @Override
    public boolean start() {
        if (!super.start()) {
            return false;
        }
        super.createContext("/", handler);
        logger.log("Server started");
        return true;
    }

    synchronized public <T> void addEndpoint(T controller) {
        RequestMapping classMapping = controller.getClass().getDeclaredAnnotation(RequestMapping.class);
        if (classMapping == null) {
            logger.err("Failed to add endpoint: \"" + controller.getClass().getName() + "\"");
            return;
        }

        PathSegment[] segments = pathToSegmentArr(classMapping.value()[0]);
        PathSegmentNode controllerRoot = root.createPath(segments);
        if (controllerRoot == null)
            return;

        // Extract handler
        Method[] methods = controller.getClass().getDeclaredMethods();
        for (Method func : methods) {
            RequestMapping mapping = func.getAnnotation(RequestMapping.class);
            if (mapping == null)
                continue;
            PathSegment[] subPathArr = pathToSegmentArr(mapping.value()[0]);

            PathSegment[] absolutePath = new PathSegment[segments.length + subPathArr.length];
            System.arraycopy(segments, 0, absolutePath, 0, segments.length);
            System.arraycopy(subPathArr, 0, absolutePath, segments.length, subPathArr.length);

            PathSegmentNode subNode = controllerRoot.createPath(subPathArr);
            // Create path failed
            if (subNode == null) continue;
            boolean selfHandleResponse = func.isAnnotationPresent(CustomResponse.class);
            for (RequestMethod method : mapping.method()) {
                subNode.addEndpoint(new EndpointHandler(absolutePath, method, selfHandleResponse, func, controller));
            }
        }
    }

    public void printStructure() {
        root.printStructure();
    }

    private static PathSegment[] pathToSegmentArr(String path) {
        // Skip first slash
        int last, i = 0;
        while (i < path.length() && path.charAt(i) == '/') ++i;
        // Split slash
        List<PathSegment> out = new ArrayList<>();
        for (last = i; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                out.add(new PathSegment(path.substring(last, i)));
                while (i < path.length() && path.charAt(i) == '/') ++i;
                last = i;
            }
        }
        // Add last path
        out.add(new PathSegment(path.substring(last)));
        return out.toArray(new PathSegment[0]);
    }

    private static String[] splitPath(String path) {
        if (path.isEmpty())
            return new String[0];
        // Skip first slash
        int last, i = 0;
        while (i < path.length() && path.charAt(i) == '/') ++i;
        // Split slash
        List<String> out = new ArrayList<>();
        for (last = i; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                out.add(path.substring(last, i));
                while (i < path.length() && path.charAt(i) == '/') ++i;
                last = i;
            }
        }
        // Add last path
        out.add(path.substring(last));
        return out.toArray(new String[0]);
    }
}
