package com.huantz.trade.hooks.model.request;

import com.huantz.trade.enums.EmailTypeEnum;
import com.huantz.trade.enums.GenderEnum;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AccountCreateRequest(
    @Size(min = 10, max = 50, message = "标题长度为10~50") @NotNull(message = "商品标题不能为空") String title,
    String comment,
    @NotNull(message = "等级不能为空") @Size(min = 1, max = 150, message = "等级范围1~150") Integer level,
    @NotNull(message = "门派不能为空") Long sectId,
    @NotNull(message = "区服不能为空") Long serverId,
    @NotNull(message = "性别不能为空") @Enumerated(EnumType.STRING) GenderEnum gender,
    @NotNull(message = "售价不能为空") BigDecimal price,
    Boolean hasDragon,
    Boolean hasHolyBeast,
    Integer holyBeastCount,
    Integer dragonCount,
    Boolean hasDragonDived,
    @Enumerated(EnumType.STRING) EmailTypeEnum emailType,
    Boolean hasTwoFactorAuthEnabled,
    Boolean hasSevenDayRebinding,
    Boolean emailRebindable,
    Boolean emailRealNameVerified) {}
