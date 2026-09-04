package com.huantz.trade.account.event;

import org.springframework.modulith.NamedInterface;

@NamedInterface
public record RegisterEmailEvent(String email) {}
