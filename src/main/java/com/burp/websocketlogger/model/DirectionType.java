package com.burp.websocketlogger.model;

public enum DirectionType {
    CLIENT_TO_SERVER("Client -> Server", "Outgoing", "Client"),
    SERVER_TO_CLIENT("Server -> Client", "Incoming", "Server");

    private final String displayName;
    private final String shortName;
    private final String alias;

    DirectionType(String displayName, String shortName, String alias) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.alias = alias;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortName() {
        return shortName;
    }

    public String getAlias() {
        return alias;
    }

    public static DirectionType fromBurpDirection(burp.api.montoya.websocket.Direction direction) {
        if (direction == burp.api.montoya.websocket.Direction.CLIENT_TO_SERVER) {
            return CLIENT_TO_SERVER;
        } else {
            return SERVER_TO_CLIENT;
        }
    }
}
