package edu.whut.eval.application.auth.model;

public class ScopeAccessDecision {

    private final boolean allowed;
    private final String matchedScopeType;
    private final String reason;

    public ScopeAccessDecision(boolean allowed, String matchedScopeType, String reason) {
        this.allowed = allowed;
        this.matchedScopeType = matchedScopeType;
        this.reason = reason;
    }

    public static ScopeAccessDecision allow(String matchedScopeType, String reason) {
        return new ScopeAccessDecision(true, matchedScopeType, reason);
    }

    public static ScopeAccessDecision deny(String reason) {
        return new ScopeAccessDecision(false, null, reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getMatchedScopeType() {
        return matchedScopeType;
    }

    public String getReason() {
        return reason;
    }
}
