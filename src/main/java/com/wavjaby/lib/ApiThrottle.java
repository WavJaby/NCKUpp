package com.wavjaby.lib;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ApiThrottle {
    private static final int FETCH_PER_MIN = 20;

    public static class ClientInfo {
        public long lastCleanFetchTime;
        public long lastFetchTime;
        public int fetchCount;

        public ClientInfo(long cleanFetchTime) {
            lastCleanFetchTime = cleanFetchTime;
            fetchCount = 0;
        }

        public void addFetchCount() {
            fetchCount++;
        }

        public boolean checkPass() {
            long now = System.currentTimeMillis();
            long timePass = now - lastFetchTime;
            lastFetchTime = now;

            if (timePass > 60000f / FETCH_PER_MIN * 1.1f) {
                fetchCount -= (int) (timePass / FETCH_PER_MIN);
                lastCleanFetchTime = now;
                return true;
            }

            if (now - lastCleanFetchTime > 60000) {
                lastCleanFetchTime = now;
                fetchCount = 0;
            }

            // Throttle
            if (fetchCount >= FETCH_PER_MIN) {
                fetchCount = FETCH_PER_MIN;
                return false;
            }

            return true;
        }
    }

    // Ip, info
    private static final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();

    public static boolean checkIpThrottle(String ip) {
        ClientInfo clientInfo = clients.get(ip);
        if (clientInfo == null) {
            clients.put(ip, new ClientInfo(System.currentTimeMillis()));
            return true;
        }
        clientInfo.addFetchCount();

        return clientInfo.checkPass();
    }
}
