package com.huantz.trade.account.model.request;

public record AdminLoginRequest(
    String email, String password, String credentialId, String publicKey) {}
