package com.knowflow.application.document.repository;

import com.knowflow.application.common.BaseRepository;
import com.knowflow.application.document.model.entity.DocCategoryEntity;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 分类目录树仓储 */
@Repository
public interface DocCategoryRepository extends BaseRepository<DocCategoryEntity> {

  List<DocCategoryEntity> findByWorkSpaceIdOrderBySortOrderAsc(Long workSpaceId);

  List<DocCategoryEntity> findByWorkSpaceIdAndParentIdOrderBySortOrderAsc(
      Long workSpaceId, Long parentId);

  boolean existsByWorkSpaceIdAndParentIdAndName(Long workSpaceId, Long parentId, String name);

  List<DocCategoryEntity> findByWorkSpaceIdAndPathStartingWith(Long workSpaceId, String pathPrefix);
}
