package com.burp.websocketlogger;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.MessageReceivedAction;
import burp.api.montoya.proxy.MessageToBeSentAction;
import burp.api.montoya.proxy.websocket.*;
import burp.api.montoya.websocket.*;
import com.burp.websocketlogger.model.DirectionType;
import com.burp.websocketlogger.model.WebSocketLogEntry;
import com.burp.websocketlogger.ui.WebSocketLoggerTab;

import java.util.concurrent.atomic.AtomicInteger;

public class WebSocketLoggerExtension implements BurpExtension {
    private final AtomicInteger messageIdCounter = new AtomicInteger(0);
    private final AtomicInteger connectionIdCounter = new AtomicInteger(0);
    private WebSocketLoggerTab tab;
    private WebSocketHistorySync historySync;

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("WebSocket++");

        tab = new WebSocketLoggerTab(api);
        api.userInterface().registerSuiteTab("WebSocket++", tab);

        // 1. Start live background 250ms synchronizer with Burp Proxy WebSockets history
        // This guarantees that any and all traffic appearing in Proxy -> WebSockets history
        // is captured live and added to the extension table without delay.
        try {
            historySync = new WebSocketHistorySync(api, tab, messageIdCounter);
            tab.setHistorySync(historySync);
            historySync.start();

            api.extension().registerUnloadingHandler(() -> {
                if (historySync != null) {
                    historySync.stop();
                }
            });
        } catch (Throwable t) {
            api.logging().logToError("Could not start WebSocket history sync: " + t.getMessage());
        }

        // 2. Register live WebSocket handler across non-Proxy tools (Repeater, Extensions, Intruder)
        // Proxy traffic is safely, passively captured via WebSocketHistorySync without intercepting the live wire.

        // 3. Register live WebSocket handler across ALL other Burp tools (Repeater, Extensions, Intruder)
        try {
            api.websockets().registerWebSocketCreatedHandler(webSocketCreated -> {
                // If it's already handled by the Proxy creation handler, skip to prevent double-logging
                if (webSocketCreated.toolSource() != null && webSocketCreated.toolSource().toolType() == ToolType.PROXY) {
                    return;
                }

                int connId = connectionIdCounter.incrementAndGet();
                HttpRequest upgradeReq = webSocketCreated.upgradeRequest();
                String tool = webSocketCreated.toolSource() != null ? webSocketCreated.toolSource().toolType().toolName() : "Burp";
                String host = "";
                int port = 0;
                String path = "";
                String url = "";
                if (upgradeReq != null) {
                    try {
                        if (upgradeReq.httpService() != null) {
                            host = upgradeReq.httpService().host();
                            port = upgradeReq.httpService().port();
                        }
                    } catch (Throwable ignored) {
                    }
                    try {
                        path = upgradeReq.path();
                    } catch (Throwable ignored) {
                    }
                    try {
                        url = upgradeReq.url();
                    } catch (Throwable ignored) {
                    }
                }

                final String finalHost = host;
                final int finalPort = port;
                final String finalPath = path;
                final String finalUrl = url;

                webSocketCreated.webSocket().registerMessageHandler(new MessageHandler() {
                    @Override
                    public TextMessageAction handleTextMessage(TextMessage textMessage) {
                        try {
                            String pText = textMessage.payload() != null ? textMessage.payload() : "";
                            ByteArray bytes = null;
                            try {
                                bytes = ByteArray.byteArray(pText);
                            } catch (Throwable ignored) {
                            }

                            WebSocketLogEntry entry = new WebSocketLogEntry(
                                    messageIdCounter.incrementAndGet(),
                                    connId,
                                    tool,
                                    DirectionType.fromBurpDirection(textMessage.direction()),
                                    "Text",
                                    finalHost,
                                    finalPort,
                                    finalPath,
                                    finalUrl,
                                    bytes,
                                    pText,
                                    upgradeReq
                            );
                            tab.addEntry(entry);
                        } catch (Throwable e) {
                            api.logging().logToError("Error logging WebSocket text message: " + e.getMessage());
                        }
                        return safeContinueTextMessage(textMessage);
                    }

                    @Override
                    public BinaryMessageAction handleBinaryMessage(BinaryMessage binaryMessage) {
                        try {
                            String pText = binaryMessage.payload() != null ? binaryMessage.payload().toString() : "";

                            WebSocketLogEntry entry = new WebSocketLogEntry(
                                    messageIdCounter.incrementAndGet(),
                                    connId,
                                    tool,
                                    DirectionType.fromBurpDirection(binaryMessage.direction()),
                                    "Binary",
                                    finalHost,
                                    finalPort,
                                    finalPath,
                                    finalUrl,
                                    binaryMessage.payload(),
                                    pText,
                                    upgradeReq
                            );
                            tab.addEntry(entry);
                        } catch (Throwable e) {
                            api.logging().logToError("Error logging WebSocket binary message: " + e.getMessage());
                        }
                        return safeContinueBinaryMessage(binaryMessage);
                    }
                });
            });
        } catch (Throwable e) {
            api.logging().logToError("Could not register application WebSocket created handler: " + e.getMessage());
        }

        api.logging().logToOutput("WebSocket++ initialized successfully.");
    }

    private TextMessageAction safeContinueTextMessage(TextMessage textMessage) {
        try {
            return TextMessageAction.continueWith(textMessage.payload());
        } catch (Throwable t) {
            return new TextMessageAction() {
                @Override
                public MessageAction action() {
                    return MessageAction.CONTINUE;
                }

                @Override
                public String payload() {
                    return textMessage.payload();
                }
            };
        }
    }

    private BinaryMessageAction safeContinueBinaryMessage(BinaryMessage binaryMessage) {
        try {
            return BinaryMessageAction.continueWith(binaryMessage.payload());
        } catch (Throwable t) {
            return new BinaryMessageAction() {
                @Override
                public MessageAction action() {
                    return MessageAction.CONTINUE;
                }

                @Override
                public ByteArray payload() {
                    return binaryMessage.payload();
                }
            };
        }
    }
}
