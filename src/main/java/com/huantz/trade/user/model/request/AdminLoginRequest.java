package com.huantz.trade.user.model.request;

public record AdminLoginRequest(
    String email, String password, String credentialId, String publicKey) {}
