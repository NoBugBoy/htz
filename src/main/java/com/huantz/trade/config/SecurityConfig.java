package com.huantz.trade.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
// @EnableMethodSecurity // 👈 核心：开启方法级权限控制 (@PreAuthorize)
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthFilter;
  private final ObjectMapper objectMapper;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        // 1. 现代无状态 API 标配：关闭 CSRF、CORS 放行、关闭默认 Session
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 2. 彻底禁用默认自带的 FormLogin 和 Basic 认证
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)

        // 3. 粗粒度 URL 白名单路由
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/auth/**", // 登录、注册、短信验证码
                        "/v3/api-docs/**", // OpenAPI / Swagger 文档
                        "/swagger-ui/**",
                        "/actuator/health" // 探活检查
                        )
                    .permitAll()
                    .anyRequest()
                    .authenticated() // 剩下的请求只需要登录凭证即可通过
            )

        // 4. 统一异常响应 (401 未登录 / 403 权限不足)
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (req, resp, e) ->
                            writeErrorJson(resp, HttpStatus.UNAUTHORIZED, "未登录或登录已过期"))
                    .accessDeniedHandler(
                        (req, resp, e) -> writeErrorJson(resp, HttpStatus.FORBIDDEN, "权限不足，拒绝访问")))

        // 5. 挂载唯一的 Token 验票过滤器
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private void writeErrorJson(HttpServletResponse resp, HttpStatusCode status, String msg)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, msg);
    resp.setContentType("application/json;charset=UTF-8");
    resp.setStatus(status.value());
    resp.getWriter().write(objectMapper.writeValueAsString(problem));
  }
}
