package com.knowflow.application.user.mapper;

import com.knowflow.application.user.api.dto.WorkSpaceDTO;
import com.knowflow.application.user.model.entity.WorkSpaceEntity;
import com.knowflow.application.user.model.response.WorkSpaceResponse;
import org.mapstruct.Mapper;

@Mapper
public interface WorkSpaceMapper {

  WorkSpaceDTO toDTO(WorkSpaceEntity entity);

  WorkSpaceResponse toResponse(WorkSpaceEntity entity);

  WorkSpaceResponse toResponse(WorkSpaceDTO dto);
}
