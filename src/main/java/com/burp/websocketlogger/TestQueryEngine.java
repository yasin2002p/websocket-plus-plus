package com.burp.websocketlogger;

import com.burp.websocketlogger.model.DirectionType;
import com.burp.websocketlogger.model.WebSocketLogEntry;
import com.burp.websocketlogger.query.FilterEngine;

public class TestQueryEngine {
    public static void main(String[] args) {
        System.out.println("Running WebSocket Logger++ Query Engine Tests...");

        FilterEngine engine = new FilterEngine(url -> url.contains("example.com"));

        WebSocketLogEntry e1 = new WebSocketLogEntry(
                1, 101, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text",
                "chat.example.com", 443, "/ws/v1", "wss://chat.example.com/ws/v1",
                null, "{\"action\":\"login\",\"token\":\"secret123\"}", null
        );

        WebSocketLogEntry e2 = new WebSocketLogEntry(
                2, 101, "Proxy", DirectionType.SERVER_TO_CLIENT, "Text",
                "chat.example.com", 443, "/ws/v1", "wss://chat.example.com/ws/v1",
                null, "{\"status\":\"ok\",\"user\":\"admin\"}", null
        );

        WebSocketLogEntry e3 = new WebSocketLogEntry(
                3, 102, "Repeater", DirectionType.CLIENT_TO_SERVER, "Text",
                "api.external.com", 80, "/socket.io/", "ws://api.external.com/socket.io/",
                null, "2", null // Heartbeat
        );

        // Test 1: Empty query matches all
        assert engine.setQuery("");
        assert engine.matches(e1) : "Test 1 failed";
        assert engine.matches(e2) : "Test 1 failed";
        assert engine.matches(e3) : "Test 1 failed";

        // Test 2: Payload contains
        assert engine.setQuery("payload contains \"login\"");
        assert engine.matches(e1) : "Test 2 failed";
        assert !engine.matches(e2) : "Test 2 failed";

        // Test 3: Direction match
        boolean s3 = engine.setQuery("dir == client");
        System.out.println("s3 valid: " + s3 + ", err: " + engine.getLastError());
        boolean m3 = engine.matches(e1);
        System.out.println("m3 matches: " + m3);
        assert m3 : "Test 3 failed";
        assert !engine.matches(e2) : "Test 3 failed";

        // Test 4: Length comparison and boolean AND
        assert engine.setQuery("dir == server and length > 20");
        assert !engine.matches(e1) : "Test 4 failed";
        assert engine.matches(e2) : "Test 4 failed";

        // Test 5: Regex match
        boolean s5 = engine.setQuery("payload matches '.*\"user\":\\s*\"admin\".*'");
        assert s5 : "Test 5 setQuery failed: " + engine.getLastError();
        assert engine.matches(e2) : "Test 5 matches e2 failed";
        assert !engine.matches(e1) : "Test 5 matches e1 failed";

        // Test 6: Free text search
        assert engine.setQuery("secret123");
        assert engine.matches(e1) : "Test 6 failed";
        assert !engine.matches(e2) : "Test 6 failed";

        // Test 7: Parentheses and OR
        assert engine.setQuery("(dir == server or tool == \"Repeater\") and host contains \"chat\"");
        assert engine.matches(e2) : "Test 7 failed";
        assert !engine.matches(e3) : "Test 7 failed";

        // Test 8: Heartbeat toggle
        engine.setQuery("");
        engine.setHideHeartbeats(true);
        assert engine.matches(e1) : "Test 8 failed";
        assert !engine.matches(e3) : "Test 8 failed (heartbeat not hidden)";
        engine.setHideHeartbeats(false);

        // Test 10: Numeric comparison operators
        assert engine.setQuery("length > 10 and length <= 100");
        assert engine.matches(e1) : "Test 10 failed for e1";
        assert engine.matches(e2) : "Test 10 failed for e2";
        assert !engine.matches(e3) : "Test 10 failed for e3 (len is 1)";

        // Test 11: startswith and endswith
        assert engine.setQuery("host startswith \"chat.\" and path endswith \"/v1\"");
        assert engine.matches(e1) : "Test 11 failed";
        assert !engine.matches(e3) : "Test 11 failed for e3";

        // Test 12: !contains and NOT
        assert engine.setQuery("payload !contains \"secret\" and not dir == client");
        assert engine.matches(e2) : "Test 12 failed for e2";
        assert !engine.matches(e1) : "Test 12 failed for e1";

        // Test 13: Complex grouping and implicit AND
        assert engine.setQuery("((dir == client and length < 5) or host contains \"external\") not payload contains \"admin\"");
        assert engine.matches(e3) : "Test 13 failed for e3";
        assert !engine.matches(e2) : "Test 13 failed for e2";

        System.out.println("ALL QUERY ENGINE TESTS PASSED SUCCESSFULLY! ✓");
    }
}
