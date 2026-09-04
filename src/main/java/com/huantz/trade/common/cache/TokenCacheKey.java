package com.huantz.trade.common.cache;

import java.time.Duration;

/** JWT 会话相关的缓存 Key，供过滤器与登出服务共用 */
public final class TokenCacheKey {

  private TokenCacheKey() {}

  /** 并发续期防抖：同一 Token 在防抖窗口内只签一次（与 RegisterBeanConfig 的 2 分钟 TTL 对齐） */
  public static final CacheKey<String, String> RENEW_DEBOUNCE =
      CacheKey.of("tokenRenew", "renew_%s", String.class, Duration.ofMinutes(2));

  /** 退出登录后的 jti 黑名单：覆盖最长 1 小时的 token 有效期 */
  public static final CacheKey<String, String> BLACKLIST =
      CacheKey.of("tokenBlacklist", "blacklist_%s", String.class, Duration.ofHours(2));
}
