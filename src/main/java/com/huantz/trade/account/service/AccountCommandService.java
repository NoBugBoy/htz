package com.huantz.trade.account.service;

import com.huantz.trade.account.model.request.AccountCreateRequest;
import com.huantz.trade.account.model.request.AccountUpdateRequest;
import com.huantz.trade.account.model.response.AccountImageResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AccountCommandService {

  /** 创建账号 */
  void create(AccountCreateRequest accountCreateRequest);

  /** 编辑账号 */
  void update(Long id, AccountUpdateRequest request);

  /** 下架账号 */
  void unlist(Long id);

  /** 上架账号 */
  void list(Long id);

  /** 删除账号（仅在下架状态才允许删除） */
  void delete(Long id);

  /** 上传账号截图 */
  AccountImageResponse uploadScreenshot(Long accountId, MultipartFile file);

  /** 删除某张账号截图 */
  void deleteScreenshot(Long accountId, Long imageId);
}
