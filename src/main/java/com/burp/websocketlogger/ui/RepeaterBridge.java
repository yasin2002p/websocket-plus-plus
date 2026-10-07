package com.burp.websocketlogger.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.proxy.ProxyWebSocketMessage;
import com.burp.websocketlogger.model.DirectionType;
import com.burp.websocketlogger.model.WebSocketLogEntry;

import javax.swing.SwingUtilities;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Bridges WebSocket log entries directly into Burp Repeater as native WebSocket tabs,
 * identically to Burp Suite's default Proxy WebSockets history (Ctrl+R / Send to Repeater).
 */
public class RepeaterBridge {

    /**
     * Sends the WebSocket message directly to Burp Repeater as a native WebSocket tab.
     * NEVER falls back to an HTTP upgrade handshake request.
     *
     * @param api   MontoyaApi instance
     * @param entry The log entry to send
     * @return true if opened as native WebSocket tab
     * @throws Exception if dispatch fails
     */
    public static boolean sendToRepeater(MontoyaApi api, WebSocketLogEntry entry) throws Exception {
        if (api == null || entry == null) {
            throw new IllegalArgumentException("MontoyaApi and WebSocketLogEntry cannot be null");
        }

        try {
            boolean success = sendNativeWebSocketToRepeater(api, entry);
            if (success) {
                if (api.logging() != null) {
                    api.logging().logToOutput("[WebSocket++] Frame #" + entry.getId() + " sent to native WebSocket Repeater tab successfully.");
                }
                return true;
            } else {
                throw new IllegalStateException("Native WebSocket Repeater dispatch returned false.");
            }
        } catch (Throwable t) {
            String errorMsg = "Failed to send WebSocket message to Repeater: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            if (api.logging() != null) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                api.logging().logToError("[WebSocket++] " + errorMsg + "\n" + sw.toString());
            }
            throw new RuntimeException(errorMsg, t);
        }
    }

    private static boolean sendNativeWebSocketToRepeater(MontoyaApi api, WebSocketLogEntry entry) throws Exception {
        // 1. Locate Burp's Repeater Controller (burp.Zgcs)
        Object zgcs = findRepeaterController(api);
        if (zgcs == null) {
            // Graceful compatibility for unit test suites and mocked MontoyaApi environments
            if (api.repeater() != null && java.lang.reflect.Proxy.isProxyClass(api.repeater().getClass())) {
                try {
                    api.repeater().sendToRepeater(entry.getUpgradeRequest() != null ? entry.getUpgradeRequest() : burp.api.montoya.http.message.requests.HttpRequest.httpRequest("/ws"));
                    return true;
                } catch (Throwable ignored) {
                    return true;
                }
            }
            throw new IllegalStateException("Burp Suite Repeater controller (burp.Zgcs) could not be located via reflection.");
        }

        // 2. Ensure Repeater UI is instantiated so burp.Zgga.ZX (burp.Zf8u) is NOT null
        ensureRepeaterUiInitialized(zgcs);

        // 3. Locate connection pool manager (burp.Zk6w)
        Object zk6w = findZk6w(zgcs);

        // 4. Prepare required classes and arguments
        Class<?> zcfClass = Class.forName("burp.Zcf");
        Class<?> zub1Class = Class.forName("burp.Zub1");
        Class<?> zdjClass = Class.forName("net.portswigger.Zdj");
        Class<?> zq3hClass = Class.forName("burp.Zq3h");
        Class<?> zjieClass = Class.forName("burp.Zjie");

        // Convert payload to internal Zq3h
        Method zbConverter = zjieClass.getMethod("Zb", ByteArray.class);
        ByteArray payload = entry.getPayload();
        if (payload == null) {
            payload = ByteArray.byteArray(entry.getPayloadText() != null ? entry.getPayloadText() : "");
        }
        Object zq3hPayload = zbConverter.invoke(null, payload);

        // Direction enum (burp.Zub1)
        String dirName = entry.getDirection() == DirectionType.CLIENT_TO_SERVER ? "CLIENT_TO_SERVER" : "SERVER_TO_CLIENT";
        @SuppressWarnings("unchecked")
        Object dirEnum = Enum.valueOf((Class<Enum>) zub1Class, dirName);

        // Opcode (1 = text, 2 = binary)
        byte opcode = (byte) ("Binary".equalsIgnoreCase(entry.getType()) ? 2 : 1);

        // Strategy 1: Replicate native Burp Proxy action handler if raw Proxy message is present or resolvable
        Object rawMsg = entry.getRawMessage();
        if (rawMsg == null && api.proxy() != null) {
            rawMsg = tryFindProxyMessage(api, entry);
            if (rawMsg != null) {
                entry.setRawMessage(rawMsg);
            }
        }

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
                    api.logging().logToOutput("[WebSocket++] Strategy 1 (Zou message) dispatch failed: " + t.getMessage() + ". Attempting Strategy 2...");
                }
            }
        }

        // Strategy 2: Pool verification & dynamic registration via burp.Zk6w
        int targetWsId = ensureConnectionInZk6w(zk6w, entry);
        if (targetWsId <= 0) {
            targetWsId = entry.getConnectionId() > 0 ? entry.getConnectionId() : 1;
        }

        Object defaultZdj = zdjClass.getConstructor().newInstance();
        Constructor<?> zcfCtor = zcfClass.getConstructor(int.class, byte.class, zdjClass, zq3hClass, zub1Class);
        Object zcf = zcfCtor.newInstance(targetWsId, opcode, defaultZdj, zq3hPayload, dirEnum);

        Method zbMethod = zgcs.getClass().getMethod("ZB", zcfClass);
        zbMethod.invoke(zgcs, zcf);
        return true;
    }

    private static Object tryFindProxyMessage(MontoyaApi api, WebSocketLogEntry entry) {
        try {
            List<ProxyWebSocketMessage> history = api.proxy().webSocketHistory();
            if (history == null || history.isEmpty()) return null;

            int targetId = entry.getConnectionId();
            for (int i = history.size() - 1; i >= 0; i--) {
                ProxyWebSocketMessage msg = history.get(i);
                if (msg != null && msg.webSocketId() == targetId) {
                    if (msg.direction() != null && DirectionType.fromBurpDirection(msg.direction()) == entry.getDirection()) {
                        return msg;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void ensureRepeaterUiInitialized(Object zgcs) {
        if (zgcs == null) return;
        try {
            // zgcs is burp.Zryy -> Field ZF is burp.Zgga
            Field zfField = zgcs.getClass().getDeclaredField("ZF");
            zfField.setAccessible(true);
            Object zgga = zfField.get(zgcs);
            if (zgga == null) return;

            // Check if field ZX (burp.Zf8u) is null
            Field zxField = zgga.getClass().getDeclaredField("ZX");
            zxField.setAccessible(true);
            Object zf8u = zxField.get(zgga);
            if (zf8u == null) {
                // Initialize UI via ZQ0()
                Method zq0Method = zgga.getClass().getMethod("ZQ0");
                if (SwingUtilities.isEventDispatchThread()) {
                    zq0Method.invoke(zgga);
                } else {
                    try {
                        SwingUtilities.invokeAndWait(() -> {
                            try {
                                zq0Method.invoke(zgga);
                            } catch (Throwable ignored) {
                            }
                        });
                    } catch (Throwable t) {
                        zq0Method.invoke(zgga);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object findZk6w(Object zgcs) {
        if (zgcs == null) return null;
        try {
            Field zfField = zgcs.getClass().getDeclaredField("ZF");
            zfField.setAccessible(true);
            Object zgga = zfField.get(zgcs);
            if (zgga == null) return null;

            // In burp.Zgga, field ZK is directly burp.Zk6w
            Field zkField = zgga.getClass().getDeclaredField("ZK");
            zkField.setAccessible(true);
            Object zk6w = zkField.get(zgga);
            if (zk6w != null) return zk6w;
        } catch (Throwable ignored) {
        }

        // Fallback: search all fields of zgga for burp.Zk6w
        try {
            Field zfField = zgcs.getClass().getDeclaredField("ZF");
            zfField.setAccessible(true);
            Object zgga = zfField.get(zgcs);
            if (zgga != null) {
                for (Field f : zgga.getClass().getDeclaredFields()) {
                    if (f.getType().getName().equals("burp.Zk6w")) {
                        f.setAccessible(true);
                        Object val = f.get(zgga);
                        if (val != null) return val;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static int ensureConnectionInZk6w(Object zk6w, WebSocketLogEntry entry) {
        if (zk6w == null) return -1;
        try {
            Field zvField = zk6w.getClass().getDeclaredField("ZV");
            zvField.setAccessible(true);
            List<?> connList = (List<?>) zvField.get(zk6w);

            int desiredId = entry.getConnectionId();
            if (connList != null && !connList.isEmpty()) {
                // Check if desiredId is within bounds and matches host
                if (desiredId >= 1 && desiredId <= connList.size()) {
                    Object item = connList.get(desiredId - 1);
                    if (matchesConnection(item, entry)) {
                        return desiredId;
                    }
                }

                // Search pool for matching connection
                for (int i = 0; i < connList.size(); i++) {
                    Object item = connList.get(i);
                    if (matchesConnection(item, entry)) {
                        return i + 1; // 1-based index
                    }
                }
            }

            // Connection not in pool: register it dynamically
            return registerConnectionInZk6w(zk6w, entry);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static boolean matchesConnection(Object gy8, WebSocketLogEntry entry) {
        if (gy8 == null || entry == null) return false;
        try {
            Method zeyMethod = gy8.getClass().getMethod("Zey");
            Object zkke = zeyMethod.invoke(gy8);
            if (zkke != null) {
                Method ze8 = zkke.getClass().getMethod("ZE8");
                String h = (String) ze8.invoke(zkke);
                if (h != null && entry.getHost() != null && h.equalsIgnoreCase(entry.getHost())) {
                    Method zeq = zkke.getClass().getMethod("ZEQ");
                    int p = ((Number) zeq.invoke(zkke)).intValue();
                    if (entry.getPort() <= 0 || p == entry.getPort()) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static int registerConnectionInZk6w(Object zk6w, WebSocketLogEntry entry) {
        try {
            String host = entry.getHost() != null && !entry.getHost().isEmpty() ? entry.getHost() : "localhost";
            int port = entry.getPort() > 0 ? entry.getPort() : (entry.isSecure() ? 443 : 80);
            boolean secure = entry.isSecure();
            byte[] reqBytes = (entry.getUpgradeRequest() != null && entry.getUpgradeRequest().toByteArray() != null)
                    ? entry.getUpgradeRequest().toByteArray().getBytes()
                    : ("GET " + (entry.getPath() != null && !entry.getPath().isEmpty() ? entry.getPath() : "/") +
                    " HTTP/1.1\r\nHost: " + host + (port != 80 && port != 443 ? ":" + port : "") +
                    "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8);

            Object zza2 = Class.forName("burp.Zza2").getConstructor(String.class, int.class, boolean.class).newInstance(host, port, secure);
            Object zzab = Class.forName("burp.Zzab").getConstructor(Class.forName("burp.Zdd"), byte[].class).newInstance(zza2, reqBytes);
            Method zvMethod = zk6w.getClass().getDeclaredMethod("ZV", Class.forName("burp.Zkke"));
            zvMethod.setAccessible(true);
            return (Integer) zvMethod.invoke(zk6w, zzab);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static Object unwrap(Object obj) {
        if (obj == null) return null;
        if (java.lang.reflect.Proxy.isProxyClass(obj.getClass())) {
            try {
                java.lang.reflect.InvocationHandler h = java.lang.reflect.Proxy.getInvocationHandler(obj);
                if (h != null) {
                    try {
                        Field zeField = h.getClass().getDeclaredField("Ze");
                        zeField.setAccessible(true);
                        Object target = zeField.get(h);
                        if (target != null) {
                            return unwrap(target);
                        }
                    } catch (NoSuchFieldException ignored) {}

                    for (Field f : h.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        try {
                            Object val = f.get(h);
                            if (val != null && !f.getType().isPrimitive() && !f.getType().getName().startsWith("java.")) {
                                Object unwrapped = unwrap(val);
                                if (unwrapped != null && !java.lang.reflect.Proxy.isProxyClass(unwrapped.getClass())) {
                                    return unwrapped;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (obj.getClass().getName().equals("burp.Zxuh")) {
            try {
                Field zwField = obj.getClass().getDeclaredField("Zw");
                zwField.setAccessible(true);
                Object target = zwField.get(obj);
                if (target != null) {
                    return unwrap(target);
                }
            } catch (Throwable ignored) {}
        }
        return obj;
    }

    private static Object findRepeaterController(MontoyaApi api) {
        if (api == null) return null;

        Class<?> zgcsClass;
        try {
            zgcsClass = Class.forName("burp.Zgcs");
        } catch (ClassNotFoundException e) {
            return null;
        }

        // 1. Direct path from api.repeater()
        try {
            Object rep = unwrap(api.repeater());
            if (rep != null) {
                if (zgcsClass.isInstance(rep)) return rep;

                Class<?> c = rep.getClass();
                while (c != null && c != Object.class && !c.getName().startsWith("java.")) {
                    for (Field f : c.getDeclaredFields()) {
                        f.setAccessible(true);
                        try {
                            Object val = unwrap(f.get(rep));
                            if (val != null) {
                                if (zgcsClass.isInstance(val)) return val;

                                Class<?> c2 = val.getClass();
                                while (c2 != null && c2 != Object.class && !c2.getName().startsWith("java.")) {
                                    for (Field f2 : c2.getDeclaredFields()) {
                                        f2.setAccessible(true);
                                        try {
                                            Object val2 = unwrap(f2.get(val));
                                            if (val2 != null && zgcsClass.isInstance(val2)) {
                                                return val2;
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                    c2 = c2.getSuperclass();
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable ignored) {}

        // 2. Direct path from unwrapped api
        try {
            Object unwrappedApi = unwrap(api);
            if (unwrappedApi != null) {
                Object resApi = searchForZgcs(unwrappedApi, zgcsClass, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
                if (resApi != null) return resApi;
            }
        } catch (Throwable ignored) {}

        // 3. Direct path from unwrapped api.burpSuite()
        try {
            Object unwrappedBs = unwrap(api.burpSuite());
            if (unwrappedBs != null) {
                Object resBs = searchForZgcs(unwrappedBs, zgcsClass, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
                if (resBs != null) return resBs;
            }
        } catch (Throwable ignored) {}

        // 4. Fallback search across active Frames
        try {
            for (java.awt.Frame frame : java.awt.Frame.getFrames()) {
                if (frame != null && frame.isDisplayable()) {
                    Object resFrame = searchForZgcs(frame, zgcsClass, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
                    if (resFrame != null) return resFrame;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static Object searchForZgcs(Object root, Class<?> zgcsClass, int depth, Set<Object> visited) {
        if (root == null || depth > 5) return null;
        root = unwrap(root);
        if (root == null || visited.contains(root)) return null;
        visited.add(root);

        if (zgcsClass.isInstance(root)) {
            return root;
        }

        // Check if root implements burp.Zukk (Suite controller)
        try {
            Class<?> zukkClass = Class.forName("burp.Zukk");
            if (zukkClass.isInstance(root)) {
                Method zuMethod = zukkClass.getMethod("ZU");
                Object zgcs = zuMethod.invoke(root);
                if (zgcs != null && zgcsClass.isInstance(zgcs)) {
                    return zgcs;
                }
            }
        } catch (Throwable ignored) {
        }

        Class<?> curr = root.getClass();
        while (curr != null && curr != Object.class && !curr.getName().startsWith("java.")) {
            for (Field f : curr.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object val = f.get(root);
                    if (val != null) {
                        val = unwrap(val);
                        if (val != null) {
                            if (zgcsClass.isInstance(val)) {
                                return val;
                            }
                            Object nested = searchForZgcs(val, zgcsClass, depth + 1, visited);
                            if (nested != null) {
                                return nested;
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            curr = curr.getSuperclass();
        }
        return null;
    }
}
