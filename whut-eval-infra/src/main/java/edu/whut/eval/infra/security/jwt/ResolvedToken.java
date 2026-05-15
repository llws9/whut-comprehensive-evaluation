package edu.whut.eval.infra.security.jwt;

public class ResolvedToken {

    private final String token;
    private final String source;

    public ResolvedToken(String token, String source) {
        this.token = token;
        this.source = source;
    }

    public String getToken() {
        return token;
    }

    public String getSource() {
        return source;
    }
}
