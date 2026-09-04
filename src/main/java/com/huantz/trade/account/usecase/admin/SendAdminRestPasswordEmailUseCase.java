package com.huantz.trade.account.usecase.admin;

import cn.hutool.core.util.RandomUtil;
import com.huantz.trade.account.cache.AccountCacheKey;
import com.huantz.trade.account.model.dto.AdminResetPasswordDTO;
import com.huantz.trade.account.model.request.ResetPasswordRequest;
import com.huantz.trade.account.service.AdminQueryService;
import com.huantz.trade.common.UseCase;
import com.huantz.trade.common.cache.CacheHelper;
import com.huantz.trade.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SendAdminRestPasswordEmailUseCase implements UseCase<ResetPasswordRequest, Void> {
  private final JavaMailSender mailSender;
  private final CacheHelper cacheHelper;
  private final AdminQueryService adminQueryService;

  private static final String EMAIL_TEMPLATE = loadEmailTemplate();

  private static String loadEmailTemplate() {
    try {
      return new ClassPathResource("templates/mail/reset-password-email.html")
          .getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("无法加载模板 templates/mail/reset-password-email.html", e);
    }
  }

  @Override
  public Void execute(ResetPasswordRequest command) {
    adminQueryService
        .findAdminByEmail(command.email())
        .orElseThrow(() -> BusinessException.badRequest("该账号不存在"));

    String tokenValue = RandomUtil.randomNumbers(4);
    cacheHelper.put(
        AccountCacheKey.OTT_RESET_PASSWORD,
        tokenValue,
        new AdminResetPasswordDTO(command.email(), command.newPassword()));

    try {
      sendRestPasswordEmailLink(command.email(), tokenValue);
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  private void sendRestPasswordEmailLink(String email, String tokenValue)
      throws MessagingException {

    MimeMessage mimeMessage = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
    helper.setFrom("754369677@qq.com");
    helper.setTo(email);

    resetEmail(helper, tokenValue);

    mailSender.send(mimeMessage);
  }

  private void resetEmail(MimeMessageHelper helper, String tokenValue) throws MessagingException {
    helper.setSubject("【Htz Trade】登录/注册一次性验证链接");
    String magicLink = "http://localhost:5173/complete-reset?ott=" + tokenValue;

    String htmlContent =
        EMAIL_TEMPLATE
            .replace("{{magicLink}}", magicLink)
            .replace("{{tokenValue}}", tokenValue)
            .replace("{{expireMinutes}}", "10");

    helper.setText(htmlContent, true);
  }
}
