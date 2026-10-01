package com.knowflow.application.document.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowflow.application.document.api.dto.DocCommentDTO;
import com.knowflow.application.document.api.dto.DocCommentNodeDTO;
import com.knowflow.application.document.mapper.DocCommentMapper;
import com.knowflow.application.document.model.entity.DocCommentEntity;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.repository.DocCommentRepository;
import com.knowflow.application.document.repository.DocumentRepository;
import com.knowflow.application.enums.WorkSpaceAclEnum;
import com.knowflow.application.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocCommentServiceTest {

  @Mock private DocumentRepository documentRepository;
  @Mock private DocCommentRepository docCommentRepository;
  @Mock private DocCommentMapper docCommentMapper;

  private DocCommentService commentService;

  @BeforeEach
  void setUp() {
    commentService =
        new DocCommentService(documentRepository, docCommentRepository, docCommentMapper);
  }

  @Test
  @DisplayName("发表根评论：成功创建并返回 DTO")
  void testAddRootCommentSuccess() {
    DocumentEntity doc =
        DocumentEntity.createManual(1L, 0L, "文档", "摘要", "正文", WorkSpaceAclEnum.INTERNAL);
    ReflectionTestUtils.setField(doc, "id", 100L);
    when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));

    when(docCommentRepository.save(any(DocCommentEntity.class)))
        .thenAnswer(
            inv -> {
              DocCommentEntity entity = inv.getArgument(0);
              ReflectionTestUtils.setField(entity, "id", 50L);
              return entity;
            });

    DocCommentDTO mockDto =
        new DocCommentDTO(50L, 1L, 100L, 10L, 0L, null, "写得很清晰！", 0, LocalDateTime.now());
    when(docCommentMapper.toDTO(any(DocCommentEntity.class))).thenReturn(mockDto);

    DocCommentDTO result = commentService.addRootComment(1L, 100L, 10L, "写得很清晰！");

    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(50L);
    assertThat(result.parentId()).isZero();
    verify(docCommentRepository).save(any(DocCommentEntity.class));
  }

  @Test
  @DisplayName("删除评论权限校验：非发表者本人无法删除")
  void testDeleteCommentAccessDenied() {
    DocCommentEntity comment = DocCommentEntity.createRoot(1L, 100L, 10L, "评论内容");
    ReflectionTestUtils.setField(comment, "id", 50L);
    when(docCommentRepository.findById(50L)).thenReturn(Optional.of(comment));

    // 当前操作人 99L 非评论发表人 10L
    assertThatThrownBy(() -> commentService.deleteComment(50L, 99L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("仅评论发表者可删除");
  }

  @Test
  @DisplayName("组装评论树：递归组装根评论与楼中楼嵌套回复")
  void testGetCommentTreeSuccess() {
    DocCommentEntity root = DocCommentEntity.createRoot(1L, 100L, 10L, "主楼评论");
    ReflectionTestUtils.setField(root, "id", 1L);

    DocCommentEntity reply = DocCommentEntity.createReply(1L, 100L, 20L, 1L, 10L, "楼中楼回复");
    ReflectionTestUtils.setField(reply, "id", 2L);

    when(docCommentRepository.findByDocumentIdOrderByCreateTimeAsc(100L))
        .thenReturn(List.of(root, reply));

    List<DocCommentNodeDTO> tree = commentService.getCommentTree(100L);

    assertThat(tree).hasSize(1);
    DocCommentNodeDTO rootNode = tree.get(0);
    assertThat(rootNode.id()).isEqualTo(1L);
    assertThat(rootNode.replies()).hasSize(1);

    DocCommentNodeDTO replyNode = rootNode.replies().get(0);
    assertThat(replyNode.id()).isEqualTo(2L);
    assertThat(replyNode.parentId()).isEqualTo(1L);
  }
}
