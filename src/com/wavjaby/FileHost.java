package com.wavjaby;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import static com.wavjaby.Main.pool;

public class FileHost implements HttpHandler {
    @Override
    public void handle(HttpExchange req) {
        pool.submit(() -> {
            String path = req.getRequestURI().getPath();
            Headers responseHeader = req.getResponseHeaders();
            try {
                if (path.equals("/NCKU/")) {
                    responseHeader.set("Content-Type", "text/html; charset=utf-8");
                    File file = new File("./index.html");

                    InputStream in = Files.newInputStream(file.toPath());
                    req.sendResponseHeaders(200, in.available());
                    OutputStream response = req.getResponseBody();
                    byte[] buff = new byte[1024];
                    int len;
                    while ((len = in.read(buff, 0, buff.length)) > 0)
                        response.write(buff, 0, len);
                    response.flush();
                } else {
                    File file = new File("./", path.substring(6));
                    if (!file.exists())
                        req.sendResponseHeaders(404, 0);
                    else {
                        if (path.endsWith(".js"))
                            responseHeader.set("Content-Type", "text/javascript; charset=utf-8");
                        else if (path.endsWith(".css"))
                            responseHeader.set("Content-Type", "text/css; charset=utf-8");

                        InputStream in = Files.newInputStream(file.toPath());
                        req.sendResponseHeaders(200, in.available());
                        OutputStream response = req.getResponseBody();
                        byte[] buff = new byte[1024];
                        int len;
                        while ((len = in.read(buff, 0, buff.length)) > 0)
                            response.write(buff, 0, len);
                        response.flush();
                    }
                }
                req.close();
            } catch (Exception e) {
                req.close();
                e.printStackTrace();
            }
            System.out.println(path);
        });
    }
}
