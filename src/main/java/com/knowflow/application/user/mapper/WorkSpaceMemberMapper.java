package com.knowflow.application.user.mapper;

import com.knowflow.application.user.api.dto.WorkSpaceMemberDTO;
import com.knowflow.application.user.model.entity.WorkSpaceMemberEntity;
import com.knowflow.application.user.model.response.WorkSpaceMemberResponse;
import org.mapstruct.Mapper;

@Mapper
public interface WorkSpaceMemberMapper {

  WorkSpaceMemberDTO toDTO(WorkSpaceMemberEntity entity);

  WorkSpaceMemberResponse toResponse(WorkSpaceMemberEntity entity);

  WorkSpaceMemberResponse toResponse(WorkSpaceMemberDTO dto);
}
