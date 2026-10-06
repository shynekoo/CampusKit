package io.qzxy.lite.core;

/** Minimum authenticated identity. Never contains the user's password or SMS code. */
public final class AccountSession {
    private final String userId;
    private final String loginCode;
    private final String projectId;

    public AccountSession(String userId, String loginCode) {
        this(userId, loginCode, "");
    }

    public AccountSession(String userId, String loginCode, String projectId) {
        if (userId == null || !userId.matches("[1-9][0-9]{0,19}")) {
            throw new IllegalArgumentException("Invalid user identity");
        }
        if (loginCode == null || loginCode.trim().isEmpty() || loginCode.length() > 4096) {
            throw new IllegalArgumentException("Missing or invalid session credential");
        }
        this.userId = userId;
        this.loginCode = loginCode;
        if (projectId == null || (!projectId.isEmpty() && !projectId.matches("[0-9]{1,20}")))
            throw new IllegalArgumentException("Invalid project identity");
        this.projectId = projectId;
    }

    public String userId() { return userId; }
    public String loginCode() { return loginCode; }
    public String projectId() { return projectId; }
    @Override public String toString() { return "AccountSession[redacted]"; }
}
