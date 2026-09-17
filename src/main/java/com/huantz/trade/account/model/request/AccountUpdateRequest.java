package com.huantz.trade.account.model.request;

import com.huantz.trade.account.model.dto.SellerContactDto;
import com.huantz.trade.enums.EmailTypeEnum;
import com.huantz.trade.enums.GenderEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AccountUpdateRequest(
    @NotBlank(message = "商品标题不能为空") @Size(min = 10, max = 50, message = "标题长度为10~50") String title,
    String comment,
    @NotNull(message = "等级不能为空")
        @Min(value = 1, message = "等级不能小于1")
        @Max(value = 150, message = "等级不能大于150")
        Integer level,
    @NotNull(message = "门派不能为空") Long sectId,
    @NotNull(message = "区服不能为空") Long serverId,
    @NotNull(message = "性别不能为空") GenderEnum gender,
    @NotNull(message = "售价不能为空") BigDecimal price,
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
    @NotNull(message = "卖家联系方式不能为空") @Valid SellerContactDto seller) {}
