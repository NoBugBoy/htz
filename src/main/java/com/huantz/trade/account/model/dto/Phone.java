package com.huantz.trade.account.model.dto;

public record Phone(String phoneNumber, String countryCode, Boolean phoneVerified) {
  public Phone(String phoneNumber) {
    this(phoneNumber, "86", false);
  }
}
