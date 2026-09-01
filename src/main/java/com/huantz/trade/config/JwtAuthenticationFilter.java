package com.huantz.trade.config;

import com.huantz.trade.common.CustomerProperties;
import com.huantz.trade.common.SecurityHolder;
import com.huantz.trade.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final CustomerProperties properties;

  @Override
  @NullMarked
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String header = request.getHeader("Authorization");

    // 1. 如果带有 Bearer Token，则进行解析
    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
      String token = header.substring(7);

      if (JwtUtils.parseAndVerifyToken(token, properties.security().getPublicKey())
          instanceof Long userId) {
        // 2. 从 Token 解析出用户信息 (比如 userId, role)
        //        var authorities = roles.stream().map(SimpleGrantedAuthority::new).toList();
        // 3. 👈 核心：手动给当前请求盖章认证！
        SecurityHolder.setAuthentication(userId);
      }
    }

    // 4. 放行给后面的 Filter 和 Controller
    chain.doFilter(request, response);
  }
}
