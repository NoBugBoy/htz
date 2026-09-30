package com.knowflow.application.common;

import com.knowflow.application.utils.RsaKeyUtils;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author yujian
 */
@ConfigurationProperties(prefix = "kf")
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
