package com.huantz.trade.account.contoller;

import static org.hamcrest.Matchers.notNullValue;

import com.huantz.trade.BaseControllerIntegrationTest;
import com.huantz.trade.account.model.dto.SellerContactDto;
import com.huantz.trade.account.model.request.AccountCreateRequest;
import com.huantz.trade.account.repository.AccountRepository;
import com.huantz.trade.enums.EmailTypeEnum;
import com.huantz.trade.enums.GenderEnum;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountControllerTest extends BaseControllerIntegrationTest {

  @Autowired private AccountRepository accountRepository;
  @Autowired private com.huantz.trade.lookup.repository.SectRepository sectRepository;
  @Autowired private com.huantz.trade.lookup.repository.GameServerRepository gameServerRepository;

  @Test
  @DisplayName("未认证访问管理后台账号接口返回401")
  void testUnauthorized() {
    givenAnonymous()
        .when()
        .get("/admin/account/page")
        .then()
        .statusCode(401);
  }

  @Test
  @DisplayName("普通用户访问管理后台账号接口返回403")
  void testForbiddenForRegularUser() {
    givenUser(999L)
        .when()
        .get("/admin/account/page")
        .then()
        .statusCode(403);
  }

  @Test
  @DisplayName("管理员分页查询账号列表成功返回200")
  void testPageAccounts() {
    givenAdmin()
        .when()
        .get("/admin/account/page")
        .then()
        .statusCode(200)
        .body(notNullValue());
  }

  @Test
  @DisplayName("管理员创建账号并进行上下架与详情操作")
  void testAdminAccountLifecycle() {
    var sect = new com.huantz.trade.lookup.model.entity.SectEntity();
    sect.setSectName("测试门派_" + java.util.UUID.randomUUID().toString().substring(0, 6));
    Long sectId = sectRepository.save(sect).getId();

    var server = new com.huantz.trade.lookup.model.entity.GameServerEntity();
    server.setServerName("测试服务器_" + java.util.UUID.randomUUID().toString().substring(0, 6));
    Long serverId = gameServerRepository.save(server).getId();

    SellerContactDto seller = new SellerContactDto("张三", GenderEnum.MALE, "13800138000", "wx123456");
    AccountCreateRequest createRequest =
        new AccountCreateRequest(
            "测试游戏商品标题至少十个汉字以上详情",
            "测试备注信息",
            100,
            sectId,
            serverId,
            GenderEnum.MALE,
            new BigDecimal("999.00"),
            false,
            false,
            0,
            0,
            false,
            EmailTypeEnum.MAIL_163,
            false,
            false,
            true,
            true,
            seller);

    // 1. 创建账号
    givenAdmin()
        .body(createRequest)
        .when()
        .post("/admin/account")
        .then()
        .statusCode(202);

    var entity = accountRepository.findAll().stream()
        .filter(a -> a.getTitle().contains("测试游戏商品标题至少十个汉字以上详情"))
        .findFirst()
        .orElseThrow();
    Long accountId = entity.getId();

    // 2. 查看详情
    givenAdmin()
        .when()
        .get("/admin/account/{id}", accountId)
        .then()
        .statusCode(200)
        .body("id", notNullValue());

    // 3. 下架账号
    givenAdmin()
        .when()
        .put("/admin/account/{id}/unlist", accountId)
        .then()
        .statusCode(204);

    // 4. 上架账号
    givenAdmin()
        .when()
        .put("/admin/account/{id}/list", accountId)
        .then()
        .statusCode(204);

    // 5. 再次下架后删除账号
    givenAdmin()
        .when()
        .put("/admin/account/{id}/unlist", accountId)
        .then()
        .statusCode(204);

    givenAdmin()
        .when()
        .delete("/admin/account/{id}", accountId)
        .then()
        .statusCode(204);
  }
}
