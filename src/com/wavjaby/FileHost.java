package com.wavjaby;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.wavjaby.Lib.setAllowOrigin;
import static com.wavjaby.Main.pool;

public class FileHost implements HttpHandler {
    @Override
    public void handle(HttpExchange req) {
        pool.submit(() -> {
            String path = req.getRequestURI().getPath();
            Headers responseHeader = req.getResponseHeaders();
            try {
                InputStream in = null;
                if (path.equals("/NCKU/")) {
                    responseHeader.set("Content-Type", "text/html; charset=utf-8");
                    in = Files.newInputStream(Paths.get("./index.html"));
                } else {
                    File file = new File("./", path.substring(6));
                    if (!file.exists())
                        req.sendResponseHeaders(404, 0);
                    else {
                        if (path.endsWith(".js"))
                            responseHeader.set("Content-Type", "text/javascript; charset=utf-8");
                        else if (path.endsWith(".css"))
                            responseHeader.set("Content-Type", "text/css; charset=utf-8");

                        in = Files.newInputStream(file.toPath());
                    }
                }
                if (in != null) {
                    setAllowOrigin(req.getRequestHeaders(), responseHeader);
                    req.sendResponseHeaders(200, in.available());
                    OutputStream response = req.getResponseBody();
                    byte[] buff = new byte[1024];
                    int len;
                    while ((len = in.read(buff, 0, buff.length)) > 0)
                        response.write(buff, 0, len);
                    response.flush();
                } else
                    req.sendResponseHeaders(404, 0);
                req.close();
            } catch (Exception e) {
                req.close();
                e.printStackTrace();
            }
        });
    }
}
