package com.burp.websocketlogger.query;

import com.burp.websocketlogger.model.DirectionType;
import com.burp.websocketlogger.model.HeartbeatRule;
import com.burp.websocketlogger.model.WebSocketLogEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FilterEngine {
    private volatile boolean showClientToServer = true;
    private volatile boolean showServerToClient = true;
    private volatile boolean hideHeartbeats = false;
    private volatile boolean inScopeOnly = false;

    private final List<HeartbeatRule> heartbeatRules = Collections.synchronizedList(new ArrayList<>());

    private volatile String currentQueryText = "";
    private volatile QueryNode compiledQuery = new QueryNode.AlwaysTrueNode();
    private volatile String lastError = null;

    private final ScopeChecker scopeChecker;

    public interface ScopeChecker {
        boolean isInScope(String url);
    }

    public FilterEngine(ScopeChecker scopeChecker) {
        this.scopeChecker = scopeChecker;
    }

    public List<HeartbeatRule> getHeartbeatRules() {
        synchronized (heartbeatRules) {
            return new ArrayList<>(heartbeatRules);
        }
    }

    public void addHeartbeatRule(HeartbeatRule rule) {
        if (rule != null) {
            heartbeatRules.add(rule);
        }
    }

    public void removeHeartbeatRule(String ruleId) {
        if (ruleId == null) return;
        heartbeatRules.removeIf(r -> ruleId.equals(r.getId()));
    }

    public void clearHeartbeatRules() {
        heartbeatRules.clear();
    }

    public void setHeartbeatRules(List<HeartbeatRule> rules) {
        heartbeatRules.clear();
        if (rules != null) {
            heartbeatRules.addAll(rules);
        }
    }

    public boolean isCustomHeartbeat(WebSocketLogEntry entry) {
        if (entry == null) return false;
        synchronized (heartbeatRules) {
            for (HeartbeatRule rule : heartbeatRules) {
                if (rule.isEnabled() && rule.matches(entry)) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized boolean setQuery(String queryText) {
        if (queryText == null) queryText = "";
        this.currentQueryText = queryText.trim();

        if (this.currentQueryText.isEmpty()) {
            this.compiledQuery = new QueryNode.AlwaysTrueNode();
            this.lastError = null;
            return true;
        }

        try {
            this.compiledQuery = QueryParser.parse(this.currentQueryText);
            this.lastError = null;
            return true;
        } catch (QueryParseException e) {
            this.lastError = e.getMessage();
            return false;
        } catch (Exception e) {
            this.lastError = "Syntax error: " + e.getMessage();
            return false;
        }
    }

    public boolean matches(WebSocketLogEntry entry) {
        if (entry == null) return false;

        // Direction toggles
        if (entry.getDirection() == DirectionType.CLIENT_TO_SERVER && !showClientToServer) {
            return false;
        }
        if (entry.getDirection() == DirectionType.SERVER_TO_CLIENT && !showServerToClient) {
            return false;
        }

        // Heartbeat toggle (built-in default heartbeats OR custom rules)
        if (hideHeartbeats) {
            if (entry.isHeartbeat() || isCustomHeartbeat(entry)) {
                return false;
            }
        }

        // Scope toggle
        if (inScopeOnly && scopeChecker != null) {
            if (!scopeChecker.isInScope(entry.getUrl())) {
                return false;
            }
        }

        // Query AST match
        QueryNode q = this.compiledQuery;
        if (q != null) {
            return q.matches(entry);
        }

        return true;
    }

    public boolean isShowClientToServer() {
        return showClientToServer;
    }

    public void setShowClientToServer(boolean showClientToServer) {
        this.showClientToServer = showClientToServer;
    }

    public boolean isShowServerToClient() {
        return showServerToClient;
    }

    public void setShowServerToClient(boolean showServerToClient) {
        this.showServerToClient = showServerToClient;
    }

    public boolean isHideHeartbeats() {
        return hideHeartbeats;
    }

    public void setHideHeartbeats(boolean hideHeartbeats) {
        this.hideHeartbeats = hideHeartbeats;
    }

    public boolean isInScopeOnly() {
        return inScopeOnly;
    }

    public void setInScopeOnly(boolean inScopeOnly) {
        this.inScopeOnly = inScopeOnly;
    }

    public String getCurrentQueryText() {
        return currentQueryText;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean hasError() {
        return lastError != null;
    }
}
