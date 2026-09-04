package com.huantz.trade.account.usecase;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import com.huantz.trade.account.model.entity.UserEntity;
import com.huantz.trade.account.service.UserCommandService;
import com.huantz.trade.account.service.UserQueryService;
import com.huantz.trade.account.service.impl.UserCommandServiceImpl.UserRegister;
import com.huantz.trade.account.usecase.LoginUseCase.LoginCommand;
import com.huantz.trade.common.CustomerProperties;
import com.huantz.trade.common.UseCase;
import com.huantz.trade.exception.BusinessException;
import com.huantz.trade.utils.JwtUtils;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author yujian
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginUseCase implements UseCase<LoginCommand, String> {
  private final UserCommandService userCommandService;
  private final UserQueryService userQueryService;
  private final WxMaService wxMaService;
  private final CustomerProperties customerProperties;

  public record LoginCommand(String loginCode) {}

  @Transactional(rollbackFor = Throwable.class)
  @Override
  public String execute(LoginCommand loginCommand) {
    final var loginCode = loginCommand.loginCode();
    String openid, unionId;

    try {
      var sessionInfo = wxMaService.getUserService().getSessionInfo(loginCode);
      openid = sessionInfo.getOpenid();
      unionId = sessionInfo.getUnionid();

    } catch (WxErrorException e) {
      log.error("微信 code2Session 调用失败, loginCode: {}", loginCode, e);
      throw handleWxException(e, "登录异常,请稍后重试");
    }

    var user = userQueryService.isFirstLogin(openid);
    //    if (StringUtils.hasText(registerCode) && ObjectUtils.isEmpty(user)) {
    //      try {
    //        phoneInfo = wxMaService.getUserService().getPhoneNoInfo(registerCode);
    //      } catch (WxErrorException e) {
    //        log.error("微信 code2phoneInfo 调用失败, registerCode: {}", registerCode, e);
    //        throw handleWxException(e, "获取手机号失败");
    //      }
    //    }
    return doRegisterAndLogin(user, openid, unionId, null);
  }

  private String doRegisterAndLogin(
      Optional<UserEntity> user, String openid, String unionId, WxMaPhoneNumberInfo phone) {
    var userId =
        user.orElseGet(
                () -> {
                  log.info("首次登录，开始注册 openId = {} ", openid);
                  return userCommandService.register(new UserRegister(openid, unionId, null, null));
                })
            .getId();
    log.info(">>> userId = {} 登录", userId);
    return JwtUtils.createToken(userId, customerProperties.security().getPrivateKey());
  }

  private BusinessException handleWxException(WxErrorException e, String defaultMsg) {
    int errCode = e.getError().getErrorCode();
    return switch (errCode) {
      case 40029 -> new BusinessException("微信登录凭证已失效，请重新打开小程序");
      case 45011 -> new BusinessException("操作过于频繁，请稍后再试");
      case -1 -> new BusinessException("微信系统繁忙，请稍后重试");
      default -> new BusinessException(defaultMsg + " (错误码: " + errCode + ")");
    };
  }
}
