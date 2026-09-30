package com.knowflow.application.document.mapper;

import com.knowflow.application.document.api.dto.DocVersionDTO;
import com.knowflow.application.document.model.entity.DocVersionEntity;
import org.mapstruct.Mapper;

/**
 * 版本快照 MapStruct 映射器
 */
@Mapper
public interface DocVersionMapper {

  DocVersionDTO toDTO(DocVersionEntity entity);

  java.util.List<DocVersionDTO> toDTOList(java.util.List<DocVersionEntity> entities);
}
