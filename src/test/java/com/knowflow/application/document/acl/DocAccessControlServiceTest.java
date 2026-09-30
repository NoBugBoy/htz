package com.knowflow.application.document.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.statemachine.DocumentStateEnum;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import com.knowflow.application.enums.WorkSpaceRoleEnum;
import com.knowflow.application.exception.BusinessException;
import com.knowflow.application.user.model.entity.WorkSpaceMemberEntity;
import com.knowflow.application.user.repository.WorkSpaceMemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocAccessControlServiceTest {

  @Mock private WorkSpaceMemberRepository workSpaceMemberRepository;

  private DocAccessControlService aclService;

  @BeforeEach
  void setUp() {
    aclService = new DocAccessControlService(workSpaceMemberRepository);
  }

  @Test
  @DisplayName("PUBLIC 级别：任何已登录用户均可查阅")
  void testPublicDocReadAllowed() {
    DocumentEntity doc = DocumentEntity.createManual(1L, 0L, "公开文档", "摘要", "内容", WorkSpaceAclEnum.PUBLIC);
    ReflectionTestUtils.setField(doc, "id", 10L);

    assertThat(aclService.canRead(doc, 999L)).isTrue();
  }

  @Test
  @DisplayName("INTERNAL 级别：工作区成员可读写，非工作区成员拦截拒绝")
  void testInternalDocAccess() {
    DocumentEntity doc = DocumentEntity.createManual(1L, 0L, "内部文档", "摘要", "内容", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 11L);

    WorkSpaceMemberEntity member = WorkSpaceMemberEntity.create(1L, 100L, WorkSpaceRoleEnum.MEMBER);
    when(workSpaceMemberRepository.findByWorkSpaceIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));
    when(workSpaceMemberRepository.findByWorkSpaceIdAndUserId(1L, 999L)).thenReturn(Optional.empty());

    assertThat(aclService.canRead(doc, 100L)).isTrue();
    assertThat(aclService.canWrite(doc, 100L)).isTrue();

    assertThat(aclService.canRead(doc, 999L)).isFalse();
    assertThat(aclService.canWrite(doc, 999L)).isFalse();
    assertThatThrownBy(() -> aclService.assertCanRead(doc, 999L))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("PRIVATE 级别：仅创建者或团队管理员/Owner有权查看与编辑")
  void testPrivateDocAccess() {
    DocumentEntity doc = DocumentEntity.createManual(1L, 0L, "私密文档", "摘要", "内容", WorkSpaceAclEnum.PRIVATE);
    ReflectionTestUtils.setField(doc, "id", 12L);
    ReflectionTestUtils.setField(doc, "createBy", 200L);

    // 1. 创建者本人 -> 允许
    assertThat(aclService.canRead(doc, 200L)).isTrue();
    WorkSpaceMemberEntity creatorMember = WorkSpaceMemberEntity.create(1L, 200L, WorkSpaceRoleEnum.MEMBER);
    when(workSpaceMemberRepository.findByWorkSpaceIdAndUserId(1L, 200L)).thenReturn(Optional.of(creatorMember));
    assertThat(aclService.canWrite(doc, 200L)).isTrue();

    // 2. 普通成员（非创建者） -> 拒绝
    WorkSpaceMemberEntity normalMember = WorkSpaceMemberEntity.create(1L, 300L, WorkSpaceRoleEnum.MEMBER);
    when(workSpaceMemberRepository.findByWorkSpaceIdAndUserId(1L, 300L)).thenReturn(Optional.of(normalMember));
    assertThat(aclService.canRead(doc, 300L)).isFalse();
    assertThat(aclService.canWrite(doc, 300L)).isFalse();

    // 3. 管理员 -> 允许
    WorkSpaceMemberEntity adminMember = WorkSpaceMemberEntity.create(1L, 400L, WorkSpaceRoleEnum.ADMIN);
    when(workSpaceMemberRepository.findByWorkSpaceIdAndUserId(1L, 400L)).thenReturn(Optional.of(adminMember));
    assertThat(aclService.canRead(doc, 400L)).isTrue();
    assertThat(aclService.canWrite(doc, 400L)).isTrue();
  }

  @Test
  @DisplayName("生命周期状态拦截：归档或审阅中状态禁止编辑")
  void testArchivedDocCannotWrite() {
    DocumentEntity doc = DocumentEntity.createManual(1L, 0L, "文档", "摘要", "内容", WorkSpaceAclEnum.INTERNAL);
    doc.transitionTo(DocumentStateEnum.ARCHIVED);

    assertThatThrownBy(() -> aclService.canWrite(doc, 400L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("已归档");
  }
}
