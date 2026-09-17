package com.huantz.trade.account.model.response;

import java.time.LocalDateTime;

public record AccountImageResponse(
    Long id,
    Long accountId,
    String imageUrl,
    String originalFileName,
    Integer sortOrder,
    LocalDateTime createTime) {}
