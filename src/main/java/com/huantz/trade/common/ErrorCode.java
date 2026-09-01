package com.huantz.trade.common;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
  String getCode(); // 业务错误码 (如 USER_NOT_FOUND)

  String getMessage(); // 默认展示文案 (如 用户不存在)

  HttpStatus getHttpStatus(); // 对应的 HTTP 状态 (如 404 NOT_FOUND)

  // 默认内置一组常用枚举
  enum Common implements ErrorCode {
    BAD_REQUEST("BAD_REQUEST", "请求参数有误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "无权限访问", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND),
    INTERNAL_ERROR("INTERNAL_ERROR", "系统开小差了，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    Common(String code, String message, HttpStatus httpStatus) {
      this.code = code;
      this.message = message;
      this.httpStatus = httpStatus;
    }

    @Override
    public String getCode() {
      return code;
    }

    @Override
    public String getMessage() {
      return message;
    }

    @Override
    public HttpStatus getHttpStatus() {
      return httpStatus;
    }
  }
}
