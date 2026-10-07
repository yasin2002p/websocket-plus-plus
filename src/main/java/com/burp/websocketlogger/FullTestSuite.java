package com.burp.websocketlogger;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Registration;
import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.proxy.Proxy;
import burp.api.montoya.proxy.ProxyWebSocketMessage;
import burp.api.montoya.scope.Scope;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.WebSocketMessageEditor;
import burp.api.montoya.websocket.*;
import com.burp.websocketlogger.export.LogExporter;
import com.burp.websocketlogger.model.DirectionType;
import com.burp.websocketlogger.model.HeartbeatRule;
import com.burp.websocketlogger.model.WebSocketLogEntry;
import com.burp.websocketlogger.query.*;
import com.burp.websocketlogger.ui.HeartbeatRuleEditDialog;
import com.burp.websocketlogger.ui.QueryHelpDialog;
import com.burp.websocketlogger.ui.WebSocketLoggerTab;
import com.burp.websocketlogger.ui.WebSocketTableModel;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class FullTestSuite {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    private static void check(boolean condition, String testName) {
        testsRun++;
        if (!condition) {
            System.err.println("[FAIL] " + testName);
            throw new AssertionError("Test failed: " + testName);
        }
        testsPassed++;
        System.out.println("[PASS] " + testName);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("STARTING COMPREHENSIVE WEBSOCKET++ TESTS");
        System.out.println("=================================================");

        testModelAndHeartbeats();
        testTokenizerEdgeCases();
        testParserEdgeCases();
        testAllFilterFieldsAndOperators();
        testComplexBooleanLogic();
        testFilterEngineToggles();
        testTableModelOperations();
        testLogExporterCsvAndJson();
        testMontoyaApiIntegrationAndUI();

        System.out.println("=================================================");
        System.out.printf("ALL TESTS PASSED: %d / %d tests passed successfully! ✓%n", testsPassed, testsRun);
        System.out.println("=================================================");
    }

    // --- PART 1: Model & Heartbeat tests ---
    private static void testModelAndHeartbeats() {
        System.out.println("\n--- Testing Model & Heartbeats ---");

        WebSocketLogEntry e = new WebSocketLogEntry(
                42, 10, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text",
                "ws.example.com", 443, "/chat", "wss://ws.example.com/chat",
                null, "Hello World!\r\nSecond line", null
        );

        check(e.getId() == 42, "LogEntry ID");
        check(e.getConnectionId() == 10, "LogEntry Connection ID");
        check(e.getTool().equals("Proxy"), "LogEntry Tool");
        check(e.getDirection() == DirectionType.CLIENT_TO_SERVER, "LogEntry Direction");
        check(e.getHost().equals("ws.example.com"), "LogEntry Host");
        check(e.getPort() == 443, "LogEntry Port");
        check(e.getPath().equals("/chat"), "LogEntry Path");
        check(e.getUrl().equals("wss://ws.example.com/chat"), "LogEntry URL");
        check(e.getLength() == 25, "LogEntry Length");
        check(e.getPreview(10).equals("Hello Worl..."), "LogEntry Preview truncation");
        check(!e.getPreview(100).contains("\r"), "LogEntry Preview strips carriage returns");
        check(!e.getPreview(100).contains("\n"), "LogEntry Preview strips newlines");

        // Comments & Highlights
        check(e.getComment().isEmpty(), "Initial comment empty");
        e.setComment("Important message");
        check(e.getComment().equals("Important message"), "Comment updated");
        e.setHighlightColor(Color.RED);
        check(Color.RED.equals(e.getHighlightColor()), "Highlight color updated");

        // Heartbeat detection
        String[] heartbeats = {"2", "3", "ping", "pong", "PING", "PONG", "{\"type\":\"ping\"}", "{\"type\":\"pong\"}", "{\"action\":\"ping\"}", "{\"event\":\"ping\"}", "  "};
        for (String hb : heartbeats) {
            WebSocketLogEntry hbEntry = new WebSocketLogEntry(1, 1, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text", "h", 80, "/", "/", null, hb, null);
            check(hbEntry.isHeartbeat(), "Detected heartbeat for: '" + hb + "'");
        }

        WebSocketLogEntry normalEntry = new WebSocketLogEntry(1, 1, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text", "h", 80, "/", "/", null, "{\"message\":\"hello\"}", null);
        check(!normalEntry.isHeartbeat(), "Normal payload is not detected as heartbeat");
    }

    // --- PART 2: Tokenizer Edge Cases ---
    private static void testTokenizerEdgeCases() throws Exception {
        System.out.println("\n--- Testing Tokenizer Edge Cases ---");

        // Escaped quotes in double quoted strings
        Tokenizer t1 = new Tokenizer("payload contains \"he said \\\"hello\\\"\"");
        List<Token> tokens1 = t1.tokenize();
        check(tokens1.size() == 4, "Tokens count for escaped quotes");
        check(tokens1.get(2).getValue().equals("he said \"hello\""), "Unescaped inner quote value: " + tokens1.get(2).getValue());

        // Single quoted string containing double quotes
        Tokenizer t2 = new Tokenizer("payload matches '.*\"status\":\"ok\".*'");
        List<Token> tokens2 = t2.tokenize();
        check(tokens2.get(2).getValue().equals(".*\"status\":\"ok\".*"), "Single quoted string preserves inner double quotes");

        // Preserving regex backslashes like \d, \s, \w, \.
        Tokenizer t3 = new Tokenizer("payload matches \"\\d+\\s+\\w+\\.\"");
        List<Token> tokens3 = t3.tokenize();
        check(tokens3.get(2).getValue().equals("\\d+\\s+\\w+\\."), "Regex backslashes preserved in Tokenizer: " + tokens3.get(2).getValue());

        // Symbols without spaces
        Tokenizer t4 = new Tokenizer("len>=100&&id!=5");
        List<Token> tokens4 = t4.tokenize();
        check(tokens4.get(0).getType() == TokenType.FIELD, "Field len without space");
        check(tokens4.get(1).getValue().equals(">="), "Operator >= without space");
        check(tokens4.get(2).getValue().equals("100"), "Number 100 without space");
        check(tokens4.get(3).getType() == TokenType.AND, "Operator && without space");
        check(tokens4.get(4).getType() == TokenType.FIELD, "Field id without space");
        check(tokens4.get(5).getValue().equals("!="), "Operator != without space");
        check(tokens4.get(6).getValue().equals("5"), "Number 5 without space");

        // Unterminated quote throws QueryParseException
        boolean caught = false;
        try {
            new Tokenizer("payload contains \"unterminated").tokenize();
        } catch (QueryParseException ex) {
            caught = true;
        }
        check(caught, "Unterminated quote raises QueryParseException");
    }

    // --- PART 3: Parser Edge Cases ---
    private static void testParserEdgeCases() throws Exception {
        System.out.println("\n--- Testing Parser Edge Cases ---");

        // Empty and whitespace queries
        QueryNode qEmpty = QueryParser.parse("");
        check(qEmpty instanceof QueryNode.AlwaysTrueNode, "Empty query produces AlwaysTrueNode");

        QueryNode qSpaces = QueryParser.parse("   \t  \n  ");
        check(qSpaces instanceof QueryNode.AlwaysTrueNode, "Whitespace query produces AlwaysTrueNode");

        // Free-text query with single term
        QueryNode qFree = QueryParser.parse("admin");
        check(qFree instanceof QueryNode.FullTextNode, "Single word produces FullTextNode");

        // Free-text with quoted phrase
        QueryNode qPhrase = QueryParser.parse("\"admin user\"");
        check(qPhrase instanceof QueryNode.FullTextNode, "Quoted phrase produces FullTextNode");

        // Nested parentheses
        QueryNode qNested = QueryParser.parse("((dir == client and length > 10) or (dir == server and length < 5))");
        check(qNested instanceof QueryNode.OrNode, "Nested parentheses parsed into OrNode");

        // Operator precedence: AND binds tighter than OR: a or b and c => a or (b and c)
        QueryNode qPrec = QueryParser.parse("host == 'a' or host == 'b' and host == 'c'");
        check(qPrec instanceof QueryNode.OrNode, "Root node of 'a or b and c' is OrNode");

        // Syntax error: missing value after operator
        boolean err1 = false;
        try {
            QueryParser.parse("payload contains");
        } catch (QueryParseException ex) {
            err1 = true;
        }
        check(err1, "Missing value after contains throws QueryParseException");

        // Syntax error: unclosed parenthesis
        boolean err2 = false;
        try {
            QueryParser.parse("(dir == client and length > 10");
        } catch (QueryParseException ex) {
            err2 = true;
        }
        check(err2, "Unclosed parenthesis throws QueryParseException");
    }

    // --- PART 4: All Filter Fields & Operators ---
    private static void testAllFilterFieldsAndOperators() throws Exception {
        System.out.println("\n--- Testing All Filter Fields & Operators ---");

        WebSocketLogEntry sample = new WebSocketLogEntry(
                100, 25, "Repeater", DirectionType.SERVER_TO_CLIENT, "Text",
                "api.service.io", 8443, "/socket.io/v2", "wss://api.service.io:8443/socket.io/v2",
                null, "{\"status\": 200, \"result\": \"success\", \"code\": \"AUTH_OK\"}", null
        );
        sample.setComment("Verified payload");

        FilterEngine fe = new FilterEngine(null);

        // Field tests
        check(testQuery(fe, sample, "payload contains \"AUTH_OK\""), "Field: payload contains");
        check(testQuery(fe, sample, "body contains \"result\""), "Field alias: body");
        check(testQuery(fe, sample, "data contains \"status\""), "Field alias: data");
        check(testQuery(fe, sample, "p contains \"200\""), "Field alias: p");

        check(testQuery(fe, sample, "host == 'api.service.io'"), "Field: host ==");
        check(testQuery(fe, sample, "h startswith 'api.'"), "Field alias: h startswith");
        check(testQuery(fe, sample, "path startswith '/socket.io'"), "Field: path startswith");
        check(testQuery(fe, sample, "path endswith '/v2'"), "Field: path endswith");
        check(testQuery(fe, sample, "url contains '8443'"), "Field: url contains");

        check(testQuery(fe, sample, "tool == 'Repeater'"), "Field: tool");
        check(testQuery(fe, sample, "type == 'Text'"), "Field: type");
        check(testQuery(fe, sample, "comment contains 'Verified'"), "Field: comment");

        // Numeric fields: id, conn, port, length
        check(testQuery(fe, sample, "id == 100"), "Field: id ==");
        check(testQuery(fe, sample, "id >= 100 and id <= 100"), "Field: id range");
        check(testQuery(fe, sample, "conn == 25"), "Field: conn ==");
        check(testQuery(fe, sample, "connection == 25"), "Field alias: connection ==");
        check(testQuery(fe, sample, "port == 8443"), "Field: port ==");
        check(testQuery(fe, sample, "len > 10"), "Field: len >");
        check(testQuery(fe, sample, "length < 1000"), "Field: length <");
        check(testQuery(fe, sample, "size >= 50"), "Field alias: size >=");

        // Operators: !=, !contains, matches, !matches
        check(testQuery(fe, sample, "host != 'other.com'"), "Operator !=");
        check(testQuery(fe, sample, "payload !contains 'error'"), "Operator !contains");
        check(testQuery(fe, sample, "payload matches '.*\"result\":\\s*\"success\".*'"), "Operator matches (regex)");
        check(testQuery(fe, sample, "payload !matches '.*\"status\":\\s*500.*'"), "Operator !matches (regex)");

        // Direction aliases: server, incoming, s2c, client, outgoing, c2s
        check(testQuery(fe, sample, "dir == server"), "Direction == server");
        check(testQuery(fe, sample, "dir == incoming"), "Direction == incoming");
        check(testQuery(fe, sample, "dir == s2c"), "Direction == s2c");
        check(!testQuery(fe, sample, "dir == client"), "Direction != client");
        check(!testQuery(fe, sample, "dir == outgoing"), "Direction != outgoing");
    }

    // --- PART 5: Complex Boolean Logic ---
    private static void testComplexBooleanLogic() throws Exception {
        System.out.println("\n--- Testing Complex Boolean Logic ---");

        FilterEngine fe = new FilterEngine(null);

        WebSocketLogEntry eClient = new WebSocketLogEntry(
                1, 1, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text", "chat.org", 443, "/ws", "wss://chat.org/ws",
                null, "ping", null
        );
        WebSocketLogEntry eServer = new WebSocketLogEntry(
                2, 1, "Proxy", DirectionType.SERVER_TO_CLIENT, "Text", "chat.org", 443, "/ws", "wss://chat.org/ws",
                null, "pong", null
        );

        // NOT operator
        check(testQuery(fe, eClient, "not dir == server"), "NOT operator on client");
        check(!testQuery(fe, eServer, "not dir == server"), "NOT operator on server");

        // AND with OR
        check(testQuery(fe, eClient, "(dir == client or dir == server) and payload == 'ping'"), "Parentheses (A or B) and C");
        check(!testQuery(fe, eServer, "(dir == client or dir == server) and payload == 'ping'"), "Parentheses mismatch C");

        // Implicit AND
        check(testQuery(fe, eClient, "host == chat.org path == /ws dir == client"), "Implicit AND across multiple terms");
        check(!testQuery(fe, eServer, "host == chat.org path == /ws dir == client"), "Implicit AND blocks non-matching term");
    }

    // --- PART 6: FilterEngine Toggles ---
    private static void testFilterEngineToggles() {
        System.out.println("\n--- Testing FilterEngine Toggles ---");

        AtomicBoolean scopeReturn = new AtomicBoolean(true);
        FilterEngine fe = new FilterEngine(url -> scopeReturn.get());

        WebSocketLogEntry eOut = new WebSocketLogEntry(1, 1, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text", "a.com", 80, "/", "/", null, "regular", null);
        WebSocketLogEntry eIn = new WebSocketLogEntry(2, 1, "Proxy", DirectionType.SERVER_TO_CLIENT, "Text", "a.com", 80, "/", "/", null, "regular", null);
        WebSocketLogEntry ePing = new WebSocketLogEntry(3, 1, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text", "a.com", 80, "/", "/", null, "ping", null);

        // Toggle Outgoing
        fe.setShowClientToServer(false);
        check(!fe.matches(eOut), "ShowClientToServer=false blocks outgoing");
        check(fe.matches(eIn), "ShowClientToServer=false allows incoming");
        fe.setShowClientToServer(true);

        // Toggle Incoming
        fe.setShowServerToClient(false);
        check(fe.matches(eOut), "ShowServerToClient=false allows outgoing");
        check(!fe.matches(eIn), "ShowServerToClient=false blocks incoming");
        fe.setShowServerToClient(true);

        // Toggle Heartbeats
        fe.setHideHeartbeats(true);
        check(!fe.matches(ePing), "HideHeartbeats=true blocks heartbeat");
        check(fe.matches(eOut), "HideHeartbeats=true allows normal payload");
        fe.setHideHeartbeats(false);

        // Toggle Scope
        fe.setInScopeOnly(true);
        scopeReturn.set(false);
        check(!fe.matches(eOut), "InScopeOnly=true blocks out-of-scope url");
        scopeReturn.set(true);
        check(fe.matches(eOut), "InScopeOnly=true allows in-scope url");
        fe.setInScopeOnly(false);
    }

    // --- PART 7: TableModel Operations ---
    private static void testTableModelOperations() {
        System.out.println("\n--- Testing TableModel Operations ---");

        FilterEngine fe = new FilterEngine(null);
        WebSocketTableModel model = new WebSocketTableModel(fe);

        check(model.getRowCount() == 0, "Initial row count is 0");
        check(model.getTotalCount() == 0, "Initial total count is 0");

        WebSocketLogEntry e1 = new WebSocketLogEntry(1, 10, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text", "h1", 80, "/", "/", null, "msg1", null);
        WebSocketLogEntry e2 = new WebSocketLogEntry(2, 10, "Proxy", DirectionType.SERVER_TO_CLIENT, "Text", "h1", 80, "/", "/", null, "msg2", null);

        model.addEntry(e1);
        model.addEntry(e2);

        check(model.getRowCount() == 2, "Row count after 2 adds is 2");
        check(model.getTotalCount() == 2, "Total count is 2");
        check(model.getFilteredCount() == 2, "Filtered count is 2");
        check(model.getOutgoingCount() == 1, "Outgoing count is 1");
        check(model.getIncomingCount() == 1, "Incoming count is 1");

        // Table column values
        check(model.getValueAt(0, 0).equals(1), "Column 0 is ID 1");
        check(model.getValueAt(0, 2).equals("Client -> Server"), "Column 2 is Client -> Server");
        check(model.getValueAt(1, 2).equals("Server -> Client"), "Column 2 is Server -> Client");
        check(model.getValueAt(0, 9).equals("msg1"), "Column 9 is preview msg1");

        // In-place Comment editing
        check(model.isCellEditable(0, 10), "Comment column is editable");
        check(!model.isCellEditable(0, 0), "ID column is not editable");
        model.setValueAt("New note", 0, 10);
        check(e1.getComment().equals("New note"), "Comment value updated via setValueAt");

        // Dynamic filtering in model
        fe.setQuery("msg1");
        model.reapplyFilter();
        check(model.getRowCount() == 1, "Filtered row count is 1 after reapplyFilter");
        check(model.getEntryAt(0).getId() == 1, "Filtered entry is ID 1");

        // Clear filter
        fe.setQuery("");
        model.reapplyFilter();
        check(model.getRowCount() == 2, "Row count back to 2 after filter cleared");

        // Remove row
        model.removeEntries(List.of(e1));
        check(model.getRowCount() == 1, "Row count is 1 after removing e1");
        check(model.getTotalCount() == 1, "Total count is 1 after removing e1");

        // Clear all
        model.clear();
        check(model.getRowCount() == 0, "Row count is 0 after clear");
        check(model.getTotalCount() == 0, "Total count is 0 after clear");
        check(model.getOutgoingCount() == 0, "Outgoing count is 0 after clear");
        check(model.getIncomingCount() == 0, "Incoming count is 0 after clear");
    }

    // --- PART 8: LogExporter CSV and JSON ---
    private static void testLogExporterCsvAndJson() throws IOException {
        System.out.println("\n--- Testing LogExporter CSV and JSON ---");

        List<WebSocketLogEntry> entries = new ArrayList<>();
        entries.add(new WebSocketLogEntry(
                1, 5, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text",
                "test.com", 443, "/ws", "wss://test.com/ws",
                null, "Hello \"World\", with comma and\nnewline!", null
        ));
        entries.add(new WebSocketLogEntry(
                2, 5, "Proxy", DirectionType.SERVER_TO_CLIENT, "Binary",
                "test.com", 443, "/ws", "wss://test.com/ws",
                null, "[Binary payload]", null
        ));

        File tempCsv = File.createTempFile("ws_log_test_", ".csv");
        File tempJson = File.createTempFile("ws_log_test_", ".json");
        tempCsv.deleteOnExit();
        tempJson.deleteOnExit();

        // Test CSV Export
        LogExporter.exportToCsv(tempCsv, entries);
        String csvContent = Files.readString(tempCsv.toPath());
        check(csvContent.contains("\"#\",\"Time\",\"Direction\",\"Tool\""), "CSV header present");
        check(csvContent.contains("\"Hello \"\"World\"\", with comma and\nnewline!\""), "CSV quotes and commas properly escaped");

        // Test JSON Export
        LogExporter.exportToJson(tempJson, entries);
        String jsonContent = Files.readString(tempJson.toPath());
        check(jsonContent.startsWith("[") && jsonContent.endsWith("]\n"), "JSON array format valid");
        check(jsonContent.contains("\"id\": 1"), "JSON contains entry 1");
        check(jsonContent.contains("\"id\": 2"), "JSON contains entry 2");
        check(jsonContent.contains("\\\"World\\\""), "JSON properly escapes double quotes");
        check(jsonContent.contains("\\nnewline!"), "JSON properly escapes newlines");
    }

    // --- PART 9: Mock Montoya API Integration & Headless UI ---
    @SuppressWarnings("unchecked")
    private static void testMontoyaApiIntegrationAndUI() throws Exception {
        System.out.println("\n--- Testing MontoyaApi Integration & UI Lifecycle ---");

        AtomicReference<String> registeredExtName = new AtomicReference<>();
        AtomicReference<String> registeredTabTitle = new AtomicReference<>();
        AtomicReference<Component> registeredTabComponent = new AtomicReference<>();
        AtomicReference<WebSocketCreatedHandler> capturedWsHandler = new AtomicReference<>();

        AtomicReference<burp.api.montoya.proxy.websocket.ProxyWebSocketCreationHandler> capturedProxyWsHandler = new AtomicReference<>();

        // Create Dynamic Mock for MontoyaApi and child interfaces
        InvocationHandler montoyaHandler = (proxy, method, methodArgs) -> {
            String mName = method.getName();
            Class<?> retType = method.getReturnType();

            if (mName.equals("extension")) {
                return createMock(burp.api.montoya.extension.Extension.class, (p, m, a) -> {
                    if (m.getName().equals("setName")) {
                        registeredExtName.set((String) a[0]);
                    }
                    return null;
                });
            } else if (mName.equals("userInterface")) {
                return createMock(UserInterface.class, (p, m, a) -> {
                    if (m.getName().equals("registerSuiteTab")) {
                        registeredTabTitle.set((String) a[0]);
                        registeredTabComponent.set((Component) a[1]);
                        return createMock(Registration.class, (p2, m2, a2) -> null);
                    } else if (m.getName().equals("createWebSocketMessageEditor")) {
                        return createMock(WebSocketMessageEditor.class, (p2, m2, a2) -> {
                            if (m2.getName().equals("uiComponent")) return new JPanel();
                            return null;
                        });
                    } else if (m.getName().equals("createHttpRequestEditor")) {
                        return createMock(HttpRequestEditor.class, (p2, m2, a2) -> {
                            if (m2.getName().equals("uiComponent")) return new JPanel();
                            return null;
                        });
                    } else if (m.getName().equals("applyThemeToComponent")) {
                        return null;
                    }
                    return null;
                });
            } else if (mName.equals("proxy")) {
                return createMock(Proxy.class, (p, m, a) -> {
                    if (m.getName().equals("webSocketHistory")) {
                        return new ArrayList<ProxyWebSocketMessage>();
                    } else if (m.getName().equals("registerWebSocketCreationHandler")) {
                        capturedProxyWsHandler.set((burp.api.montoya.proxy.websocket.ProxyWebSocketCreationHandler) a[0]);
                        return createMock(Registration.class, (p2, m2, a2) -> null);
                    }
                    return null;
                });
            } else if (mName.equals("websockets")) {
                return createMock(WebSockets.class, (p, m, a) -> {
                    if (m.getName().equals("registerWebSocketCreatedHandler")) {
                        capturedWsHandler.set((WebSocketCreatedHandler) a[0]);
                        return createMock(Registration.class, (p2, m2, a2) -> null);
                    }
                    return null;
                });
            } else if (mName.equals("logging")) {
                return createMock(Logging.class, (p, m, a) -> null);
            } else if (mName.equals("scope")) {
                return createMock(Scope.class, (p, m, a) -> {
                    if (m.getName().equals("isInScope")) return true;
                    return false;
                });
            }
            return null;
        };

        MontoyaApi mockApi = (MontoyaApi) java.lang.reflect.Proxy.newProxyInstance(
                MontoyaApi.class.getClassLoader(),
                new Class<?>[]{MontoyaApi.class},
                montoyaHandler
        );

        // Instantiate Extension
        WebSocketLoggerExtension extension = new WebSocketLoggerExtension();
        extension.initialize(mockApi);

        check("WebSocket++".equals(registeredExtName.get()), "Extension name registered properly: " + registeredExtName.get());
        check("WebSocket++".equals(registeredTabTitle.get()), "Suite tab registered with title: " + registeredTabTitle.get());
        check(registeredTabComponent.get() instanceof WebSocketLoggerTab, "Registered tab component is WebSocketLoggerTab");
        check(capturedWsHandler.get() != null, "WebSocketCreatedHandler registered with WebSockets service");

        // --- TEST 1: UTF-8 Encoding Verification for Persian & Unicode traffic ---
        String persianText = "متن ارسال شده: تست پیام فارسی با کاراکترهای خاص";
        byte[] persianBytes = persianText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteArray mockPersianPayload = createMock(ByteArray.class, (p, m, a) -> {
            if (m.getName().equals("getBytes")) return persianBytes;
            if (m.getName().equals("length")) return persianBytes.length;
            if (m.getName().equals("toString")) return persianText;
            return null;
        });

        WebSocketLogEntry persianEntry = new WebSocketLogEntry(
                999, 10, "Proxy", DirectionType.SERVER_TO_CLIENT, "Text",
                "example.com", 443, "/ws", "wss://example.com/ws",
                mockPersianPayload, null, null
        );

        check(persianText.equals(persianEntry.getPayloadText()), "Persian UTF-8 text decoded with zero Mojibake corruption: " + persianEntry.getPayloadText());
        check(persianEntry.getLength() == persianBytes.length, "Persian UTF-8 payload length matches exact byte count");

        // --- TEST 2: Live Repeater WebSocket traffic ---
        WebSocketCreatedHandler wsHandler = capturedWsHandler.get();
        AtomicReference<MessageHandler> capturedMsgHandler = new AtomicReference<>();

        WebSocketCreated mockWsCreated = createMock(WebSocketCreated.class, (p, m, a) -> {
            if (m.getName().equals("toolSource")) {
                return createMock(ToolSource.class, (p2, m2, a2) -> {
                    if (m2.getName().equals("toolType")) return ToolType.REPEATER;
                    return null;
                });
            } else if (m.getName().equals("upgradeRequest")) {
                return createMock(HttpRequest.class, (p2, m2, a2) -> {
                    if (m2.getName().equals("path")) return "/repeater/ws";
                    if (m2.getName().equals("url")) return "wss://target.com/repeater/ws";
                    if (m2.getName().equals("httpService")) {
                        return createMock(HttpService.class, (p3, m3, a3) -> {
                            if (m3.getName().equals("host")) return "target.com";
                            if (m3.getName().equals("port")) return 443;
                            return null;
                        });
                    }
                    return null;
                });
            } else if (m.getName().equals("webSocket")) {
                return createMock(WebSocket.class, (p2, m2, a2) -> {
                    if (m2.getName().equals("registerMessageHandler")) {
                        capturedMsgHandler.set((MessageHandler) a2[0]);
                        return createMock(Registration.class, (p3, m3, a3) -> null);
                    }
                    return null;
                });
            }
            return null;
        });

        wsHandler.handleWebSocketCreated(mockWsCreated);
        check(capturedMsgHandler.get() != null, "MessageHandler registered on Repeater WebSocket instance");

        // Simulate incoming text frame from Repeater
        MessageHandler msgHandler = capturedMsgHandler.get();
        TextMessage mockTextMsg = createMock(TextMessage.class, (p, m, a) -> {
            if (m.getName().equals("payload")) return "{\"repeater\":\"test\"}";
            if (m.getName().equals("direction")) return Direction.SERVER_TO_CLIENT;
            return null;
        });

        TextMessageAction action = msgHandler.handleTextMessage(mockTextMsg);
        check(action != null, "TextMessageAction returned from Repeater message");
        check("{\"repeater\":\"test\"}".equals(action.payload()), "Action payload matches repeater original");

        // --- TEST 3: Live Proxy WebSockets History Synchronization & Deduplication ---
        List<ProxyWebSocketMessage> mockProxyHistoryList = new ArrayList<>();
        ProxyWebSocketMessage historyMsg1 = createMock(ProxyWebSocketMessage.class, (p, m, a) -> {
            if (m.getName().equals("webSocketId")) return 555;
            if (m.getName().equals("direction")) return Direction.SERVER_TO_CLIENT;
            if (m.getName().equals("payload")) return null;
            if (m.getName().equals("upgradeRequest")) return null;
            return null;
        });
        mockProxyHistoryList.add(historyMsg1);

        AtomicInteger syncIdCounter = new AtomicInteger(500);
        WebSocketLoggerTab syncTestTab = new WebSocketLoggerTab(mockApi);
        WebSocketHistorySync testSync = new WebSocketHistorySync(createMock(MontoyaApi.class, (p, m, a) -> {
            if (m.getName().equals("proxy")) {
                return createMock(Proxy.class, (p2, m2, a2) -> {
                    if (m2.getName().equals("webSocketHistory")) return mockProxyHistoryList;
                    return null;
                });
            } else if (m.getName().equals("logging")) {
                return createMock(Logging.class, (p2, m2, a2) -> null);
            }
            return null;
        }), syncTestTab, syncIdCounter);

        int addedFirst = testSync.syncNow();
        check(addedFirst == 1, "WebSocketHistorySync added 1 message on first run");

        int addedSecond = testSync.syncNow();
        check(addedSecond == 0, "WebSocketHistorySync deduplicated and added 0 duplicate messages on second run");

        // Add a second new message to history
        ProxyWebSocketMessage historyMsg2 = createMock(ProxyWebSocketMessage.class, (p, m, a) -> {
            if (m.getName().equals("webSocketId")) return 556;
            if (m.getName().equals("direction")) return Direction.CLIENT_TO_SERVER;
            if (m.getName().equals("payload")) return null;
            if (m.getName().equals("upgradeRequest")) return null;
            return null;
        });
        mockProxyHistoryList.add(historyMsg2);

        int addedThird = testSync.syncNow();
        check(addedThird == 1, "WebSocketHistorySync picked up new message arriving on proxy connection");

        // --- TEST 4: Repeated Identical 'To Server' Messages Are NEVER Dropped ---
        for (int k = 1; k <= 5; k++) {
            final int seq = k;
            ByteArray mockPayload = createMockByteArray("2");
            ProxyWebSocketMessage repeatedClientMsg = createMock(ProxyWebSocketMessage.class, (p, m, a) -> {
                if (m.getName().equals("webSocketId")) return 777;
                if (m.getName().equals("direction")) return Direction.CLIENT_TO_SERVER;
                if (m.getName().equals("payload")) return mockPayload; // identical engine.io ping
                if (m.getName().equals("upgradeRequest")) return null;
                return null;
            });
            mockProxyHistoryList.add(repeatedClientMsg);
        }

        int addedRepeated = testSync.syncNow();
        check(addedRepeated == 5, "WebSocketHistorySync successfully captured all 5 identical 'To Server' heartbeats without dropping any (added: " + addedRepeated + ")");

        // --- TEST 5: Table Auto-Scroll Behavior with Sort Descending & Ascending ---
        final WebSocketLoggerTab scrollTab = new WebSocketLoggerTab(mockApi);
        final JTable tbl = scrollTab.getTable();
        final JScrollPane sp = scrollTab.getTableScrollPane();

        SwingUtilities.invokeAndWait(() -> {
            // Populate initial entries
            for (int i = 1; i <= 20; i++) {
                scrollTab.getTableModel().addEntry(new WebSocketLogEntry(
                        i, 1, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text",
                        "example.com", 443, "/ws", "wss://example.com/ws",
                        createMockByteArray("msg " + i), "msg " + i, null
                ));
            }

            // 1. Sort by column 0 (#) DESCENDING (newest on top)
            TableRowSorter<?> sorter = (TableRowSorter<?>) tbl.getRowSorter();
            sorter.setSortKeys(java.util.Collections.singletonList(new RowSorter.SortKey(0, SortOrder.DESCENDING)));
            sorter.sort();

            // Verify row 0 in view is indeed the newest (ID 20)
            int modelRowAtViewZero = tbl.convertRowIndexToModel(0);
            check(scrollTab.getTableModel().getEntryAt(modelRowAtViewZero).getId() == 20,
                    "Table sorted descending puts newest entry (ID 20) at view row 0");

            // Add a new entry (ID 21) while sorted descending
            scrollTab.addEntry(new WebSocketLogEntry(
                    21, 1, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text",
                    "example.com", 443, "/ws", "wss://example.com/ws",
                    createMockByteArray("msg 21"), "msg 21", null
            ));
        });

        // Flush EDT so that scrollTab.addEntry has executed
        SwingUtilities.invokeAndWait(() -> {
            // Verify that entry 21 is now at view row 0
            int modelRowNew = tbl.convertRowIndexToModel(0);
            check(scrollTab.getTableModel().getEntryAt(modelRowNew).getId() == 21,
                    "After adding entry 21 while sorted descending, newest entry is at view row 0 (top)");

            // Verify auto-scroll kept the vertical scroll bar at 0 (top)
            int scrollValue = sp.getVerticalScrollBar().getValue();
            check(scrollValue == 0, "Vertical scroll bar remains at top (value: " + scrollValue + ")");

            // 2. Sort by column 0 (#) ASCENDING (newest on bottom)
            TableRowSorter<?> sorter = (TableRowSorter<?>) tbl.getRowSorter();
            sorter.setSortKeys(java.util.Collections.singletonList(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
            sorter.sort();

            // Add entry 22 while sorted ascending
            scrollTab.addEntry(new WebSocketLogEntry(
                    22, 1, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text",
                    "example.com", 443, "/ws", "wss://example.com/ws",
                    createMockByteArray("msg 22"), "msg 22", null
            ));
        });

        // Flush EDT so that entry 22 addition has executed
        SwingUtilities.invokeAndWait(() -> {
            // Verify entry 22 is at the bottom of the table
            int bottomViewRow = tbl.getRowCount() - 1;
            int modelRowBottom = tbl.convertRowIndexToModel(bottomViewRow);
            check(scrollTab.getTableModel().getEntryAt(modelRowBottom).getId() == 22,
                    "After adding entry 22 while sorted ascending, newest entry is at the bottom row");
        });

        // --- TEST 6: Direction Query Filter Aliases (to_server, to_client) ---
        FilterEngine feDirectionTest = new FilterEngine(null);
        WebSocketLogEntry clientEntry = new WebSocketLogEntry(
                100, 1, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text",
                "target.com", 443, "/ws", "wss://target.com/ws",
                createMockByteArray("client payload"), "client payload", null
        );
        WebSocketLogEntry serverEntry = new WebSocketLogEntry(
                101, 1, "Proxy", DirectionType.SERVER_TO_CLIENT, "Text",
                "target.com", 443, "/ws", "wss://target.com/ws",
                createMockByteArray("server payload"), "server payload", null
        );

        check(testQuery(feDirectionTest, clientEntry, "dir == to_server"), "Query 'dir == to_server' matches Client -> Server");
        check(!testQuery(feDirectionTest, serverEntry, "dir == to_server"), "Query 'dir == to_server' rejects Server -> Client");
        check(testQuery(feDirectionTest, clientEntry, "dir == \"to server\""), "Query 'dir == \"to server\"' matches Client -> Server");
        check(testQuery(feDirectionTest, serverEntry, "dir == to_client"), "Query 'dir == to_client' matches Server -> Client");
        check(!testQuery(feDirectionTest, clientEntry, "dir == to_client"), "Query 'dir == to_client' rejects Client -> Server");
        check(testQuery(feDirectionTest, serverEntry, "dir == \"to client\""), "Query 'dir == \"to client\"' matches Server -> Client");

        // --- TEST 7: Query History (Max 10, Deduplication, Most Recent First) ---
        SwingUtilities.invokeAndWait(() -> {
            WebSocketLoggerTab tabForHistory = new WebSocketLoggerTab(mockApi);
            check(tabForHistory.getRecentQueries().isEmpty(), "Initial query history is empty");
            check(tabForHistory.getHistoryBtn() != null, "History dropdown button is initialized");

            // Apply several queries
            JTextField qf = tabForHistory.getQueryField();
            JButton hBtn = tabForHistory.getHistoryBtn();

            qf.setText("payload contains \"token\"");
            tabForHistory.getTableModel().reapplyFilter();
            // Trigger through action listener
            qf.postActionEvent();
            check(tabForHistory.getRecentQueries().size() == 1, "First query saved in history");
            check(tabForHistory.getRecentQueries().get(0).equals("payload contains \"token\""), "Query content matches in history");

            // Add duplicate query -> should move to top without duplicating
            qf.setText("length > 100");
            qf.postActionEvent();
            check(tabForHistory.getRecentQueries().size() == 2, "Second query saved in history");
            check(tabForHistory.getRecentQueries().get(0).equals("length > 100"), "Most recent query is at index 0");

            qf.setText("payload contains \"token\"");
            qf.postActionEvent();
            check(tabForHistory.getRecentQueries().size() == 2, "Duplicate query does not increase list size");
            check(tabForHistory.getRecentQueries().get(0).equals("payload contains \"token\""), "Duplicate moved to most recent position (index 0)");

            // Add 12 different queries to test max limit 10
            for (int i = 1; i <= 12; i++) {
                qf.setText("dir == client and length == " + i);
                qf.postActionEvent();
            }
            check(tabForHistory.getRecentQueries().size() == 10, "Query history capped strictly at 10 items");
            check(tabForHistory.getRecentQueries().get(0).equals("dir == client and length == 12"), "Newest query is at top of history");
            check(tabForHistory.getRecentQueries().get(9).equals("dir == client and length == 3"), "Oldest retained query is at bottom (index 9)");
        });

        // --- TEST 8: Heartbeat Analyzer & Custom Heartbeat Rules ---
        // Test 8.1: Analyzer with empty / {}
        WebSocketLogEntry emptyJsonEntry = new WebSocketLogEntry(
                200, 1, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text",
                "target.com", 443, "/ws", "wss://target.com/ws",
                createMockByteArray("{}"), "{}", null
        );
        String qEmpty = HeartbeatAnalyzer.generateQuery(emptyJsonEntry);
        check(qEmpty.contains("payload == \"{}\""), "Analyzer generates exact {} query for empty JSON object");
        check(qEmpty.contains("dir == client"), "Analyzer captures client direction for outgoing empty JSON");

        // Test 8.2: Analyzer with complex user payload (time variable)
        String userNoiseJson = "{\"id\":0,\"senderId\":0,\"type\":0,\"time\":1791314970659,\"retryCount\":0,\"arnstep\":0}";
        WebSocketLogEntry noiseEntry1 = new WebSocketLogEntry(
                201, 1, "Proxy", DirectionType.SERVER_TO_CLIENT, "Text",
                "target.com", 443, "/ws", "wss://target.com/ws",
                createMockByteArray(userNoiseJson), userNoiseJson, null
        );
        String qNoise = HeartbeatAnalyzer.generateQuery(noiseEntry1);
        check(qNoise.contains("dir == server"), "Analyzer captures server direction for noise payload");
        check(qNoise.contains("payload contains"), "Analyzer produces ultra-fast non-backtracking contains query for JSON");
        check(qNoise.contains("\"id\":0") || qNoise.contains("\\\"id\\\":0"), "Analyzer includes static id:0 signature");
        check(qNoise.contains("\"time\":") || qNoise.contains("\\\"time\\\":"), "Analyzer includes dynamic time signature");

        // Test 8.3: Custom HeartbeatRule compilation and matching
        HeartbeatRule ruleNoise = new HeartbeatRule("rule-1", "User Heartbeat Noise", qNoise, true);
        check(ruleNoise.isValid(), "Generated rule compiled successfully without syntax error");
        check(ruleNoise.matches(noiseEntry1), "Rule matches the original noise packet");

        // Noise packet with a different timestamp and retryCount
        String userNoiseJson2 = "{\"id\":0,\"senderId\":0,\"type\":0,\"time\":1791399999999,\"retryCount\":5,\"arnstep\":0}";
        WebSocketLogEntry noiseEntry2 = new WebSocketLogEntry(
                202, 1, "Proxy", DirectionType.SERVER_TO_CLIENT, "Text",
                "target.com", 443, "/ws", "wss://target.com/ws",
                createMockByteArray(userNoiseJson2), userNoiseJson2, null
        );
        check(ruleNoise.matches(noiseEntry2), "Rule matches another noise packet with different timestamp!");

        // Normal packet that should NOT match the noise rule
        String legitimateJson = "{\"id\":105,\"action\":\"chat_message\",\"text\":\"Hello world\"}";
        WebSocketLogEntry legitEntry = new WebSocketLogEntry(
                203, 1, "Proxy", DirectionType.SERVER_TO_CLIENT, "Text",
                "target.com", 443, "/ws", "wss://target.com/ws",
                createMockByteArray(legitimateJson), legitimateJson, null
        );
        check(!ruleNoise.matches(legitEntry), "Rule does NOT match legitimate business message");

        // Test 8.4: FilterEngine with Custom HeartbeatRule
        FilterEngine feHeartbeat = new FilterEngine(null);
        feHeartbeat.addHeartbeatRule(ruleNoise);

        // When hideHeartbeats is false -> everything shows
        feHeartbeat.setHideHeartbeats(false);
        check(feHeartbeat.matches(noiseEntry1), "When hideHeartbeats=false, noiseEntry1 is shown");
        check(feHeartbeat.matches(noiseEntry2), "When hideHeartbeats=false, noiseEntry2 is shown");
        check(feHeartbeat.matches(legitEntry), "When hideHeartbeats=false, legitEntry is shown");

        // When hideHeartbeats is true -> noise packets are hidden, legitimate packet is shown
        feHeartbeat.setHideHeartbeats(true);
        check(!feHeartbeat.matches(noiseEntry1), "When hideHeartbeats=true, noiseEntry1 is HIDDEN by custom rule");
        check(!feHeartbeat.matches(noiseEntry2), "When hideHeartbeats=true, noiseEntry2 is HIDDEN by custom rule");
        check(feHeartbeat.matches(legitEntry), "When hideHeartbeats=true, legitimate packet remains VISIBLE");

        // When rule is disabled in the dialog -> noise packets become visible again
        ruleNoise.setEnabled(false);
        check(feHeartbeat.matches(noiseEntry1), "When rule is disabled, noiseEntry1 is visible again");
        ruleNoise.setEnabled(true);

        // Test 8.5: Editing rule query dynamically
        ruleNoise.setQuery("payload contains \"chat_message\"");
        check(ruleNoise.matches(legitEntry), "After editing rule, it matches the updated query");
        check(!ruleNoise.matches(noiseEntry1), "After editing rule, it no longer matches old noise");

        // Test 8.6: Length-based filter generation and dialog option switching
        String lengthQuery = HeartbeatAnalyzer.generateLengthQuery(noiseEntry1);
        check(lengthQuery.equals("dir == server and length == " + noiseEntry1.getLength()), "Length query generated accurately: " + lengthQuery);
        HeartbeatRule lengthRule = new HeartbeatRule("rule-len", "Length Rule", lengthQuery, true);
        check(lengthRule.isValid(), "Length rule is valid syntax");
        check(lengthRule.matches(noiseEntry1), "Length rule matches noiseEntry1 with same length");

        // Verify Dialog with payload vs length options in GUI thread
        SwingUtilities.invokeAndWait(() -> {
            HeartbeatRuleEditDialog dualDialog = new HeartbeatRuleEditDialog(
                    null, lengthRule, true, qNoise, lengthQuery
            );
            check(dualDialog.getByPayloadRadio() != null, "Dialog has by-payload radio option");
            check(dualDialog.getByLengthRadio() != null, "Dialog has by-length radio option");

            // Click by-length radio
            dualDialog.getByLengthRadio().doClick();
            check(dualDialog.getQueryArea().getText().equals(lengthQuery), "Switching to length radio updates query area to length query");

            // Click by-payload radio
            dualDialog.getByPayloadRadio().doClick();
            check(dualDialog.getQueryArea().getText().equals(qNoise), "Switching to payload radio updates query area to payload query");

            dualDialog.dispose();
        });

        // --- TEST 9: PayloadBeautifier & JWT/Base64 Decoded Inspector ---
        String rawMinifiedJson = "{\"status\":\"ok\",\"user\":{\"id\":10,\"role\":\"admin\"},\"tags\":[\"sec\",\"pentest\"]}";
        String beautifiedJson = com.burp.websocketlogger.analysis.PayloadBeautifier.beautify(rawMinifiedJson);
        check(beautifiedJson.contains("\n  \"status\": \"ok\""), "Beautifier properly indents JSON with newlines and 2 spaces");
        check(beautifiedJson.contains("  \"user\": {\n"), "Beautifier formats nested objects cleanly");

        // Socket.io formatted packet
        String socketIoPacket = "42[\"chat\",{\"msg\":\"hello\"}]";
        String beautifiedSocketIo = com.burp.websocketlogger.analysis.PayloadBeautifier.beautify(socketIoPacket);
        check(beautifiedSocketIo.contains("Socket.io frame"), "Beautifier recognizes and handles Socket.io packet prefix");

        // JWT Token detection and decoding
        String fakeJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkFsaWNlIiwicm9sZSI6ImFkbWluIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String jwtPayload = "{\"auth_token\":\"" + fakeJwt + "\"}";
        String decodedInspection = com.burp.websocketlogger.analysis.PayloadBeautifier.decodeInspect(jwtPayload);
        check(decodedInspection.contains("JWT Token #1"), "Decoder identifies JWT token");
        check(decodedInspection.contains("\"name\": \"Alice\""), "Decoder unpacks and formats JWT payload claims");
        check(decodedInspection.contains("\"role\": \"admin\""), "Decoder reveals admin role in JWT claim");

        // --- TEST 10: SecurityScanner (Passive Token, PII & Server Error Detection) ---
        WebSocketLogEntry jwtEntry = new WebSocketLogEntry(
                301, 1, "Proxy", DirectionType.SERVER_TO_CLIENT, "Text",
                "app.com", 443, "/ws", "wss://app.com/ws",
                createMockByteArray(jwtPayload), jwtPayload, null
        );
        check(jwtEntry.getSecurityTags().contains("Token"), "SecurityScanner tags JWT entry with 🔑 Token");

        String piiPayload = "{\"user\":\"john\",\"email\":\"john.doe@target.com\",\"phone\":\"09123456789\"}";
        WebSocketLogEntry piiEntry = new WebSocketLogEntry(
                302, 1, "Proxy", DirectionType.SERVER_TO_CLIENT, "Text",
                "app.com", 443, "/ws", "wss://app.com/ws",
                createMockByteArray(piiPayload), piiPayload, null
        );
        check(piiEntry.getSecurityTags().contains("PII"), "SecurityScanner tags entry containing email and phone with 👤 PII");

        String errorPayload = "{\"error\":\"Fatal error: Unhandled Exception in thread main: NullPointerException at com.app.Service\"}";
        WebSocketLogEntry errorEntry = new WebSocketLogEntry(
                303, 1, "Proxy", DirectionType.SERVER_TO_CLIENT, "Text",
                "app.com", 443, "/ws", "wss://app.com/ws",
                createMockByteArray(errorPayload), errorPayload, null
        );
        check(errorEntry.getSecurityTags().contains("Error"), "SecurityScanner tags server exception leak with ⚠ Error");

        // Query filtering by security tags
        FilterEngine feTag = new FilterEngine(null);
        check(testQuery(feTag, jwtEntry, "tag contains \"Token\""), "Query 'tag contains \"Token\"' matches JWT message");
        check(!testQuery(feTag, piiEntry, "tag contains \"Token\""), "Query 'tag contains \"Token\"' rejects PII message");
        check(testQuery(feTag, piiEntry, "tag contains \"PII\""), "Query 'tag contains \"PII\"' matches PII message");
        check(testQuery(feTag, errorEntry, "tag contains \"Error\""), "Query 'tag contains \"Error\"' matches Error message");

        // --- TEST 11: Send to Repeater & Intruder Bridge Verification ---
        HttpRequest mockHandshake = createMock(HttpRequest.class, (p, m, a) -> {
            if (m.getName().equals("toString")) return "GET /ws HTTP/1.1\r\nHost: target.com\r\n\r\n";
            return null;
        });
        WebSocketLogEntry repeaterTestEntry = new WebSocketLogEntry(
                304, 1, "Proxy", DirectionType.CLIENT_TO_SERVER, "Text",
                "target.com", 443, "/ws", "wss://target.com/ws",
                createMockByteArray("{\"action\":\"test\"}"), "{\"action\":\"test\"}", mockHandshake
        );
        check(repeaterTestEntry.getUpgradeRequest() != null, "WebSocketLogEntry holds handshake upgrade request");

        // --- TEST 12: Decoder & Comparer Payload Verification ---
        check(repeaterTestEntry.getPayload() != null && repeaterTestEntry.getPayload().length() > 0, "LogEntry has valid byte payload for Decoder/Comparer");
        check(repeaterTestEntry.getPayloadText().equals("{\"action\":\"test\"}"), "LogEntry string representation matches for Decoder/Comparer");

        // --- TEST 13: ExportDialog Construction & Scope Logic ---
        if (!GraphicsEnvironment.isHeadless()) {
            List<WebSocketLogEntry> testExportList = new ArrayList<>();
            testExportList.add(repeaterTestEntry);
            com.burp.websocketlogger.ui.ExportDialog exportDlg = new com.burp.websocketlogger.ui.ExportDialog(mockApi, scrollTab.getTableModel(), testExportList);
            check(exportDlg.getTitle().equals("Export WebSocket Logs"), "ExportDialog title matches");
            exportDlg.dispose();

            QueryHelpDialog helpDialog = new QueryHelpDialog(null);
            check(helpDialog.getTitle().contains("Query Syntax Guide"), "QueryHelpDialog title matches");
            helpDialog.dispose();
        } else {
            System.out.println("[SKIP] GUI dialog tests skipped in headless mode");
        }
    }

    private static boolean testQuery(FilterEngine fe, WebSocketLogEntry entry, String query) {
        boolean valid = fe.setQuery(query);
        if (!valid) {
            System.err.println("Query syntax error for: " + query + " -> " + fe.getLastError());
            return false;
        }
        return fe.matches(entry);
    }

    @SuppressWarnings("unchecked")
    private static <T> T createMock(Class<T> iface, InvocationHandler handler) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler);
    }

    private static ByteArray createMockByteArray(String text) {
        return createMock(ByteArray.class, (p, m, a) -> {
            if (m.getName().equals("toString")) return text;
            if (m.getName().equals("getBytes")) return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (m.getName().equals("length")) return text.length();
            return null;
        });
    }
}
