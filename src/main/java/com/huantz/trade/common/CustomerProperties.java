package com.huantz.trade.common;

import com.huantz.trade.utils.RsaKeyUtils;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author yujian
 */
@ConfigurationProperties(prefix = "htz")
public record CustomerProperties(Security security) {

  public record Security(String publicKey, String privateKey) {
    public PublicKey getPublicKey() {
      return RsaKeyUtils.getPublicKey(publicKey);
    }

    public PrivateKey getPrivateKey() {
      return RsaKeyUtils.getPrivateKey(privateKey);
    }
  }
}
