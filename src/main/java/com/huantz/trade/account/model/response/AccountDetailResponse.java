package com.huantz.trade.account.model.response;

import com.huantz.trade.account.model.dto.SellerContactDto;
import com.huantz.trade.enums.AccountStatusEnum;
import com.huantz.trade.enums.EmailTypeEnum;
import com.huantz.trade.enums.GenderEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AccountDetailResponse(
    Long id,
    String title,
    String comment,
    Integer level,
    Long sectId,
    String sectName,
    Long serverId,
    String serverName,
    GenderEnum gender,
    BigDecimal firstPrice,
    BigDecimal price,
    Boolean hasDragon,
    Boolean hasHolyBeast,
    Integer holyBeastCount,
    Integer dragonCount,
    Boolean hasDragonDived,
    EmailTypeEnum emailType,
    Boolean hasTwoFactorAuthEnabled,
    Boolean hasSevenDayRebinding,
    Boolean emailRebindable,
    Boolean emailRealNameVerified,
    AccountStatusEnum accountStatus,
    SellerContactDto seller,
    List<AccountImageResponse> images,
    LocalDateTime createTime,
    LocalDateTime updateTime) {}
