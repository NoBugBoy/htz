package com.huantz.trade.user.model.dto;

public record Phone(String phoneNumber, String countryCode, Boolean phoneVerified) {
  public Phone(String phoneNumber) {
    this(phoneNumber, "86", false);
  }
}
