package com.burp.websocketlogger.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.burp.websocketlogger.model.DirectionType;
import com.burp.websocketlogger.model.WebSocketLogEntry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bridges WebSocket log entries directly into Burp Repeater as native WebSocket tabs.
 * Falls back to HTTP representation if native WebSocket tab reflection is unavailable.
 */
public class RepeaterBridge {

    /**
     * Sends the WebSocket message to Burp Repeater.
     * Attempts native WebSocket tab opening first; falls back to HTTP request if unavailable.
     *
     * @param api   MontoyaApi instance
     * @param entry The log entry to send
     * @return true if opened as native WebSocket tab, false if fallen back to HTTP
     * @throws Exception if sending fails entirely
     */
    public static boolean sendToRepeater(MontoyaApi api, WebSocketLogEntry entry) throws Exception {
        if (api == null || entry == null) {
            throw new IllegalArgumentException("MontoyaApi and WebSocketLogEntry cannot be null");
        }

        // Try Native WebSocket Repeater tab via reflection
        try {
            boolean nativeSuccess = sendNativeWebSocketToRepeater(api, entry);
            if (nativeSuccess) {
                return true;
            }
        } catch (Throwable ignored) {
            // Fall through to fallback
        }

        // Fallback: send through standard Repeater API
        sendFallbackToRepeater(api, entry);
        return false;
    }

    private static boolean sendNativeWebSocketToRepeater(MontoyaApi api, WebSocketLogEntry entry) throws Exception {
        Object repeaterService = api.repeater();
        if (repeaterService == null) return false;

        Object zgcs = findRepeaterController(repeaterService);
        if (zgcs == null) return false;

        Class<?> zcfClass = Class.forName("burp.Zcf");
        Class<?> zub1Class = Class.forName("burp.Zub1");
        Class<?> zdjClass = Class.forName("net.portswigger.Zdj");
        Class<?> zq3hClass = Class.forName("burp.Zq3h");
        Class<?> zjieClass = Class.forName("burp.Zjie");

        // Convert payload to internal Zq3h
        Method zbConverter = zjieClass.getMethod("Zb", ByteArray.class);
        ByteArray payload = entry.getPayload();
        if (payload == null) {
            payload = ByteArray.byteArray(entry.getPayloadText());
        }
        Object zq3hPayload = zbConverter.invoke(null, payload);

        // Direction enum
        String dirName = entry.getDirection() == DirectionType.CLIENT_TO_SERVER ? "CLIENT_TO_SERVER" : "SERVER_TO_CLIENT";
        @SuppressWarnings("unchecked")
        Object dirEnum = Enum.valueOf((Class<Enum>) zub1Class, dirName);

        // Opcode (1 = text, 2 = binary)
        byte opcode = (byte) ("Binary".equalsIgnoreCase(entry.getType()) ? 2 : 1);

        // Connection / WebSocket ID
        int wsId = entry.getConnectionId();
        if (wsId <= 0) {
            wsId = entry.getId();
        }

        // Constructor: burp.Zcf(int webSocketId, byte opcode, net.portswigger.Zdj messageDetails, burp.Zq3h payload, burp.Zub1 direction)
        Constructor<?> zcfCtor = zcfClass.getConstructor(int.class, byte.class, zdjClass, zq3hClass, zub1Class);
        Object zcf = zcfCtor.newInstance(wsId, opcode, null, zq3hPayload, dirEnum);

        // Invoke ZB(Zcf) on Zgcs
        Method zbMethod = zgcs.getClass().getMethod("ZB", zcfClass);
        zbMethod.invoke(zgcs, zcf);
        return true;
    }

    private static void sendFallbackToRepeater(MontoyaApi api, WebSocketLogEntry entry) {
        String tabName = "WS #" + entry.getId();
        HttpRequest upgradeReq = entry.getUpgradeRequest();
        if (upgradeReq != null) {
            api.repeater().sendToRepeater(upgradeReq, tabName);
        } else {
            // Construct a basic HTTP request carrying the message
            String host = entry.getHost() != null ? entry.getHost() : "localhost";
            int port = entry.getPort() > 0 ? entry.getPort() : 80;
            boolean secure = entry.getUrl() != null && entry.getUrl().toLowerCase().startsWith("wss://");
            String path = entry.getPath() != null ? entry.getPath() : "/";

            String rawHttp = "POST " + path + " HTTP/1.1\r\n" +
                    "Host: " + host + (port != 80 && port != 443 ? ":" + port : "") + "\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "X-WebSocket-Id: " + entry.getConnectionId() + "\r\n" +
                    "Content-Length: " + entry.getLength() + "\r\n\r\n" +
                    entry.getPayloadText();

            HttpRequest fallbackReq = HttpRequest.httpRequest(
                    burp.api.montoya.http.HttpService.httpService(host, port, secure),
                    rawHttp
            );
            api.repeater().sendToRepeater(fallbackReq, tabName);
        }
    }

    private static Object findRepeaterController(Object repeaterObj) {
        if (repeaterObj == null) return null;
        Class<?> zgcsClass;
        try {
            zgcsClass = Class.forName("burp.Zgcs");
        } catch (ClassNotFoundException e) {
            return null;
        }

        if (zgcsClass.isInstance(repeaterObj)) return repeaterObj;

        Class<?> curr = repeaterObj.getClass();
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object val = f.get(repeaterObj);
                    if (val != null) {
                        if (zgcsClass.isInstance(val)) {
                            return val;
                        }
                        // Check one level deeper (e.g. Zbel -> Zk18 -> Zgcs)
                        for (Field f2 : val.getClass().getDeclaredFields()) {
                            f2.setAccessible(true);
                            try {
                                Object val2 = f2.get(val);
                                if (val2 != null && zgcsClass.isInstance(val2)) {
                                    return val2;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }
            curr = curr.getSuperclass();
        }
        return null;
    }
}
