package com.sujitha.livebidspringboot.dto;

public class LoginResponse {

    private String token;
    private String tokenType;
    private long userId;
    private String name;
    private String email;

    public LoginResponse() {
    }

    public LoginResponse(
            String token,
            String tokenType,
            long userId,
            String name,
            String email) {

        this.token = token;
        this.tokenType = tokenType;
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}