package com.burp.websocketlogger.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.burp.websocketlogger.model.DirectionType;
import com.burp.websocketlogger.model.WebSocketLogEntry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

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
                if (api.logging() != null) {
                    api.logging().logToOutput("WebSocket frame #" + entry.getId() + " sent to native WebSocket Repeater tab.");
                }
                return true;
            }
        } catch (Throwable t) {
            if (api.logging() != null) {
                api.logging().logToError("Native WebSocket Repeater dispatch error: " + t.getMessage() + ". Using fallback.");
            }
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

        // Strategy 1: Replicate native Burp Proxy action handler if raw Proxy message is present
        Object rawMsg = entry.getRawMessage();
        if (rawMsg != null && rawMsg.getClass().getName().equals("burp.Zou")) {
            try {
                Field zpField = rawMsg.getClass().getDeclaredField("ZP");
                zpField.setAccessible(true);
                Object zg_o = zpField.get(rawMsg);
                if (zg_o != null) {
                    Field zyField = zg_o.getClass().getDeclaredField("Zy");
                    Field zcField = zg_o.getClass().getDeclaredField("ZC");
                    zyField.setAccessible(true);
                    zcField.setAccessible(true);
                    Object zfc = zyField.get(zg_o);
                    Object zrta = zcField.get(zg_o);
                    if (zfc != null && zrta != null) {
                        Method zkMethod = zrta.getClass().getMethod("ZK", Class.forName("burp.Zzfc"));
                        Object zfni = zkMethod.invoke(zrta, zfc);
                        if (zfni != null) {
                            Method zofMethod = zfc.getClass().getMethod("Zof");
                            byte rawOpcode = ((Number) zofMethod.invoke(zfc)).byteValue();
                            Method zoqMethod = zfc.getClass().getMethod("Zoq");
                            Object rawZdj = zoqMethod.invoke(zfc);
                            if (rawZdj == null) {
                                rawZdj = zdjClass.getConstructor().newInstance();
                            }
                            Method zjMethod = zfni.getClass().getMethod("ZJ", byte.class, zdjClass, zub1Class, zq3hClass);
                            Object zcfObj = zjMethod.invoke(zfni, rawOpcode != 0 ? rawOpcode : opcode, rawZdj, dirEnum, zq3hPayload);
                            Method zbMethod = zgcs.getClass().getMethod("ZB", zcfClass);
                            zbMethod.invoke(zgcs, zcfObj);
                            return true;
                        }
                    }
                }
            } catch (Throwable t) {
                if (api.logging() != null) {
                    api.logging().logToError("Proxy raw message dispatch failed: " + t.getMessage());
                }
                // Fall through to Strategy 2
            }
        }

        // Strategy 2: Pool verification & dynamic registration via burp.Zk6w
        int targetWsId = -1;
        Object zk6w = findZk6w(zgcs);
        if (zk6w != null) {
            try {
                Field zvField = zk6w.getClass().getDeclaredField("ZV");
                zvField.setAccessible(true);
                List<?> connList = (List<?>) zvField.get(zk6w);
                int currentConnCount = connList != null ? connList.size() : 0;

                int desiredId = entry.getConnectionId();
                if (desiredId > 0 && desiredId <= currentConnCount) {
                    targetWsId = desiredId;
                } else if (connList != null && !connList.isEmpty()) {
                    // Check if any existing connection in the pool matches host
                    for (int i = 0; i < connList.size(); i++) {
                        Object gy8 = connList.get(i);
                        if (gy8 != null) {
                            try {
                                Method zeyMethod = gy8.getClass().getMethod("Zey");
                                Object zkke = zeyMethod.invoke(gy8);
                                if (zkke != null) {
                                    Method ze8 = zkke.getClass().getMethod("ZE8");
                                    String h = (String) ze8.invoke(zkke);
                                    if (h != null && entry.getHost() != null && h.equalsIgnoreCase(entry.getHost())) {
                                        targetWsId = i + 1; // 1-based index
                                        break;
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                    if (targetWsId <= 0) {
                        targetWsId = connList.size(); // Use most recent connection in pool
                    }
                } else {
                    // Pool is empty: register connection into Zk6w
                    targetWsId = registerConnectionInZk6w(zk6w, entry);
                }
            } catch (Throwable t) {
                if (api.logging() != null) {
                    api.logging().logToError("Zk6w connection resolution error: " + t.getMessage());
                }
            }
        }

        if (targetWsId <= 0) {
            targetWsId = entry.getConnectionId() > 0 ? entry.getConnectionId() : 1;
        }

        // Constructor: burp.Zcf(int webSocketId, byte opcode, net.portswigger.Zdj messageDetails, burp.Zq3h payload, burp.Zub1 direction)
        Object defaultZdj = zdjClass.getConstructor().newInstance();
        Constructor<?> zcfCtor = zcfClass.getConstructor(int.class, byte.class, zdjClass, zq3hClass, zub1Class);
        Object zcf = zcfCtor.newInstance(targetWsId, opcode, defaultZdj, zq3hPayload, dirEnum);

        // Invoke ZB(Zcf) on Zgcs
        Method zbMethod = zgcs.getClass().getMethod("ZB", zcfClass);
        zbMethod.invoke(zgcs, zcf);
        return true;
    }

    private static int registerConnectionInZk6w(Object zk6w, WebSocketLogEntry entry) {
        try {
            String host = entry.getHost() != null && !entry.getHost().isEmpty() ? entry.getHost() : "localhost";
            int port = entry.getPort() > 0 ? entry.getPort() : (entry.isSecure() ? 443 : 80);
            boolean secure = entry.isSecure();
            byte[] reqBytes = (entry.getUpgradeRequest() != null && entry.getUpgradeRequest().toByteArray() != null)
                    ? entry.getUpgradeRequest().toByteArray().getBytes()
                    : ("GET " + (entry.getPath() != null && !entry.getPath().isEmpty() ? entry.getPath() : "/") + " HTTP/1.1\r\nHost: " + host + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n").getBytes();

            Object zza2 = Class.forName("burp.Zza2").getConstructor(String.class, int.class, boolean.class).newInstance(host, port, secure);
            Object zzab = Class.forName("burp.Zzab").getConstructor(Class.forName("burp.Zdd"), byte[].class).newInstance(zza2, reqBytes);
            Method zvMethod = zk6w.getClass().getDeclaredMethod("ZV", Class.forName("burp.Zkke"));
            zvMethod.setAccessible(true);
            return (Integer) zvMethod.invoke(zk6w, zzab);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static Object findZk6w(Object zgcs) {
        if (zgcs == null) return null;
        try {
            // Zryy.ZF (Zgga) -> Zgga.ZX (Zf8u) -> Zf8u.ZN (Zkuo) -> Zkuo.ZR (Zy65) -> Zy65.ZA (Zk6w)
            Field zfField = zgcs.getClass().getDeclaredField("ZF");
            zfField.setAccessible(true);
            Object zgga = zfField.get(zgcs);
            if (zgga == null) return null;

            Field zxField = zgga.getClass().getDeclaredField("ZX");
            zxField.setAccessible(true);
            Object zf8u = zxField.get(zgga);
            if (zf8u == null) return null;

            Field znField = zf8u.getClass().getDeclaredField("ZN");
            znField.setAccessible(true);
            Object zkuo = znField.get(zf8u);
            if (zkuo == null) return null;

            Field zrField = zkuo.getClass().getDeclaredField("ZR");
            zrField.setAccessible(true);
            Object zy65 = zrField.get(zkuo);
            if (zy65 == null) return null;

            Field zaField = zy65.getClass().getDeclaredField("ZA");
            zaField.setAccessible(true);
            return zaField.get(zy65);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void sendFallbackToRepeater(MontoyaApi api, WebSocketLogEntry entry) {
        String tabName = "WS #" + entry.getId();
        HttpRequest upgradeReq = entry.getUpgradeRequest();
        if (upgradeReq != null) {
            api.repeater().sendToRepeater(upgradeReq, tabName);
        } else {
            // Construct a basic HTTP request carrying the message
            String host = entry.getHost() != null && !entry.getHost().isEmpty() ? entry.getHost() : "localhost";
            int port = entry.getPort() > 0 ? entry.getPort() : (entry.isSecure() ? 443 : 80);
            boolean secure = entry.isSecure();
            String path = entry.getPath() != null && !entry.getPath().isEmpty() ? entry.getPath() : "/";

            String rawHttp = "POST " + path + " HTTP/1.1\r\n" +
                    "Host: " + host + (port != 80 && port != 443 ? ":" + port : "") + "\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "X-WebSocket-Id: " + entry.getConnectionId() + "\r\n" +
                    "Content-Length: " + entry.getLength() + "\r\n\r\n" +
                    entry.getPayloadText();

            try {
                HttpRequest fallbackReq = HttpRequest.httpRequest(
                        burp.api.montoya.http.HttpService.httpService(host, port, secure),
                        rawHttp
                );
                api.repeater().sendToRepeater(fallbackReq, tabName);
            } catch (Throwable t) {
                if (api.logging() != null) {
                    api.logging().logToError("Could not build Montoya fallback HttpRequest: " + t.getMessage());
                }
            }
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
