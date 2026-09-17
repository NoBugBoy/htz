package com.huantz.trade.account.contoller.admin;

import com.huantz.trade.account.AccountQueryService;
import com.huantz.trade.account.model.request.AccountCreateRequest;
import com.huantz.trade.account.model.request.AccountPageRequest;
import com.huantz.trade.account.model.request.AccountUpdateRequest;
import com.huantz.trade.account.model.response.AccountDetailResponse;
import com.huantz.trade.account.model.response.AccountImageResponse;
import com.huantz.trade.account.model.response.AccountPageResponse;
import com.huantz.trade.account.service.AccountCommandService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/account")
@Slf4j
@RequiredArgsConstructor
public class AccountController {

  private final AccountCommandService accountCommandService;
  private final AccountQueryService accountQueryService;

  /** 创建游戏账号（关联卖家联系方式：姓名、先生/女士、手机号、微信号） */
  @PostMapping
  public ResponseEntity<Void> create(
      @Validated @RequestBody AccountCreateRequest accountCreateRequest) {
    accountCommandService.create(accountCreateRequest);
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }

  /** 分页查询账号列表（支持按标题关键字、状态、门派、区服多条件筛选） */
  @GetMapping("/page")
  public Page<AccountPageResponse> page(@Validated AccountPageRequest request) {
    return accountQueryService.page(request);
  }

  /** 查询账号详情（包含完整账号信息、卖家联系方式及截图） */
  @GetMapping("/{id}")
  public ResponseEntity<AccountDetailResponse> getDetail(@PathVariable Long id) {
    return ResponseEntity.ok(accountQueryService.getDetail(id));
  }

  /** 编辑游戏账号信息（包含卖家联系方式） */
  @PutMapping("/{id}")
  public ResponseEntity<Void> update(
      @PathVariable Long id, @Validated @RequestBody AccountUpdateRequest request) {
    accountCommandService.update(id, request);
    return ResponseEntity.noContent().build();
  }

  /** 下架账号 */
  @PutMapping("/{id}/unlist")
  public ResponseEntity<Void> unlist(@PathVariable Long id) {
    accountCommandService.unlist(id);
    return ResponseEntity.noContent().build();
  }

  /** 上架账号 */
  @PutMapping("/{id}/list")
  public ResponseEntity<Void> list(@PathVariable Long id) {
    accountCommandService.list(id);
    return ResponseEntity.noContent().build();
  }

  /** 删除账号（仅在下架状态才能删除） */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    accountCommandService.delete(id);
    return ResponseEntity.noContent().build();
  }

  /** 为账号上传截图 */
  @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AccountImageResponse> uploadScreenshot(
      @PathVariable Long id, @RequestParam("file") MultipartFile file) {
    AccountImageResponse response = accountCommandService.uploadScreenshot(id, file);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /** 获取账号所有截图（供前端截图展示页面直接调用） */
  @GetMapping("/{id}/images")
  public ResponseEntity<List<AccountImageResponse>> getScreenshots(@PathVariable Long id) {
    return ResponseEntity.ok(accountQueryService.getScreenshots(id));
  }

  /** 删除某张账号截图 */
  @DeleteMapping("/{id}/images/{imageId}")
  public ResponseEntity<Void> deleteScreenshot(@PathVariable Long id, @PathVariable Long imageId) {
    accountCommandService.deleteScreenshot(id, imageId);
    return ResponseEntity.noContent().build();
  }
}
