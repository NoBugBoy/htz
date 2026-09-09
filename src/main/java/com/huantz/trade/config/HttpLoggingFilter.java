package com.huantz.trade.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 全局 HTTP 请求与响应日志打印过滤器
 *
 * @author yujian
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpLoggingFilter extends OncePerRequestFilter {

  private static final int MAX_PAYLOAD_LENGTH = 10000;

  private static final Set<String> EXCLUDE_PREFIXES =
      Set.of("/actuator", "/v3/api-docs", "/swagger-ui", "/favicon.ico");

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return EXCLUDE_PREFIXES.stream().anyMatch(path::startsWith);
  }

  @Override
  @NullMarked
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 包装 Request 和 Response 以支持重复读取 Payload
    ContentCachingRequestWrapper requestWrapper =
        request instanceof ContentCachingRequestWrapper req
            ? req
            : new ContentCachingRequestWrapper(request, MAX_PAYLOAD_LENGTH);

    ContentCachingResponseWrapper responseWrapper =
        response instanceof ContentCachingResponseWrapper resp
            ? resp
            : new ContentCachingResponseWrapper(response);

    long startTime = System.currentTimeMillis();
    String uri = request.getRequestURI();
    String queryString = request.getQueryString();
    String fullUri = StringUtils.hasText(queryString) ? uri + "?" + queryString : uri;
    String method = request.getMethod();
    String clientIp = getClientIp(request);

    try {
      filterChain.doFilter(requestWrapper, responseWrapper);
    } finally {
      long duration = System.currentTimeMillis() - startTime;
      int status = responseWrapper.getStatus();

      String requestBody =
          getPayload(requestWrapper.getContentAsByteArray(), request.getContentType());
      String responseBody =
          getPayload(responseWrapper.getContentAsByteArray(), responseWrapper.getContentType());

      log.info(
          """

          ====================== [HTTP LOG] ======================
          --> {} {} (IP: {})
          Request Body : {}
          <-- {} (Cost: {} ms)
          Response Body: {}
          =======================================================
          """,
          method,
          fullUri,
          clientIp,
          StringUtils.hasText(requestBody) ? requestBody : "[EMPTY]",
          status,
          duration,
          StringUtils.hasText(responseBody) ? responseBody : "[EMPTY]");

      // 必须将缓存的响应体内容回写至真实 Response
      responseWrapper.copyBodyToResponse();
    }
  }

  private String getPayload(byte[] buf, String contentType) {
    if (buf == null || buf.length == 0) {
      return "";
    }
    // 仅打印文本/JSON/XML/表单类内容，避免二进制内容乱码
    if (contentType != null) {
      String lower = contentType.toLowerCase();
      boolean isTextOrJson =
          lower.contains("json")
              || lower.contains("xml")
              || lower.contains("text")
              || lower.contains("form");
      if (!isTextOrJson) {
        return "[Binary Data: " + buf.length + " bytes]";
      }
    }
    int length = Math.min(buf.length, MAX_PAYLOAD_LENGTH);
    String content = new String(buf, 0, length, StandardCharsets.UTF_8);
    return buf.length > MAX_PAYLOAD_LENGTH ? content + "... [TRUNCATED]" : content;
  }

  private String getClientIp(HttpServletRequest request) {
    String ip = request.getHeader("X-Forwarded-For");
    if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getHeader("X-Real-IP");
    }
    if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getRemoteAddr();
    }
    if (StringUtils.hasText(ip) && ip.contains(",")) {
      ip = ip.split(",")[0].trim();
    }
    return ip;
  }
}
