package com.huantz.trade.account.model.dto;

import com.huantz.trade.enums.GenderEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SellerContactDto(
    @NotBlank(message = "卖家姓名不能为空") @Size(max = 50, message = "卖家姓名长度不能超过50") String name,
    @NotNull(message = "卖家称谓(先生/女士)不能为空") GenderEnum gender,
    @NotBlank(message = "卖家手机号不能为空") @Size(max = 30, message = "卖家手机号长度不能超过30") String phone,
    @NotBlank(message = "卖家微信号不能为空") @Size(max = 50, message = "卖家微信号长度不能超过50") String wechat) {}
