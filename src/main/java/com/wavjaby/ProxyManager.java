package com.wavjaby;

import com.wavjaby.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;

public class ProxyManager {
    private static final String TAG = "[ProxyManager] ";
    private final Proxy proxy;

    ProxyManager() {
        String proxiesString = null;
        try {
            File proxyTxtFile = new File("proxy.txt");
            if (proxyTxtFile.exists())
                proxiesString = new String(Files.readAllBytes(proxyTxtFile.toPath()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (proxiesString != null) {
            String[] proxies = proxiesString.split("\r?\n");
            String[] proxyUrl = proxies[0].split("[@:]");
            proxy = new Proxy(Proxy.Type.valueOf(proxyUrl[0]), new InetSocketAddress(proxyUrl[1], Integer.parseInt(proxyUrl[2])));
        } else
            proxy = null;

        if (proxy != null)
            Logger.log(TAG, "Using proxy: " + proxy);
    }

    public Proxy getProxy() {
        return null;
    }

}
