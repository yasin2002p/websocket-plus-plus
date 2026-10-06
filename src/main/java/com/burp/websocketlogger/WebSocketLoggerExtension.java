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
        api.extension().setName("WebSocket Logger++");

        tab = new WebSocketLoggerTab(api);
        api.userInterface().registerSuiteTab("WebSocket Logger++", tab);

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

        // 2. Register live WebSocket handler specifically for the PROXY tool (browsers & intercepted traffic)
        try {
            api.proxy().registerWebSocketCreationHandler(creation -> {
                int connId = connectionIdCounter.incrementAndGet();
                HttpRequest upgradeReq = creation.upgradeRequest();
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

                creation.proxyWebSocket().registerProxyMessageHandler(new ProxyMessageHandler() {
                    @Override
                    public TextMessageReceivedAction handleTextMessageReceived(InterceptedTextMessage msg) {
                        return safeContinueProxyTextMessage(msg);
                    }

                    @Override
                    public TextMessageToBeSentAction handleTextMessageToBeSent(InterceptedTextMessage msg) {
                        return safeContinueProxyTextMessageToBeSent(msg);
                    }

                    @Override
                    public BinaryMessageReceivedAction handleBinaryMessageReceived(InterceptedBinaryMessage msg) {
                        return safeContinueProxyBinaryMessage(msg);
                    }

                    @Override
                    public BinaryMessageToBeSentAction handleBinaryMessageToBeSent(InterceptedBinaryMessage msg) {
                        return safeContinueProxyBinaryMessageToBeSent(msg);
                    }
                });
            });
        } catch (Throwable e) {
            api.logging().logToError("Could not register Proxy WebSocket creation handler: " + e.getMessage());
        }

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

        api.logging().logToOutput("WebSocket Logger++ initialized successfully.");
    }

    private TextMessageReceivedAction safeContinueProxyTextMessage(InterceptedTextMessage msg) {
        try {
            return TextMessageReceivedAction.continueWith(msg);
        } catch (Throwable t) {
            return new TextMessageReceivedAction() {
                @Override
                public MessageReceivedAction action() {
                    return MessageReceivedAction.CONTINUE;
                }

                @Override
                public String payload() {
                    return msg.payload();
                }
            };
        }
    }

    private TextMessageToBeSentAction safeContinueProxyTextMessageToBeSent(InterceptedTextMessage msg) {
        try {
            return TextMessageToBeSentAction.continueWith(msg);
        } catch (Throwable t) {
            return new TextMessageToBeSentAction() {
                @Override
                public MessageToBeSentAction action() {
                    return MessageToBeSentAction.CONTINUE;
                }

                @Override
                public String payload() {
                    return msg.payload();
                }
            };
        }
    }

    private BinaryMessageReceivedAction safeContinueProxyBinaryMessage(InterceptedBinaryMessage msg) {
        try {
            return BinaryMessageReceivedAction.continueWith(msg);
        } catch (Throwable t) {
            return new BinaryMessageReceivedAction() {
                @Override
                public MessageReceivedAction action() {
                    return MessageReceivedAction.CONTINUE;
                }

                @Override
                public ByteArray payload() {
                    return msg.payload();
                }
            };
        }
    }

    private BinaryMessageToBeSentAction safeContinueProxyBinaryMessageToBeSent(InterceptedBinaryMessage msg) {
        try {
            return BinaryMessageToBeSentAction.continueWith(msg);
        } catch (Throwable t) {
            return new BinaryMessageToBeSentAction() {
                @Override
                public MessageToBeSentAction action() {
                    return MessageToBeSentAction.CONTINUE;
                }

                @Override
                public ByteArray payload() {
                    return msg.payload();
                }
            };
        }
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
