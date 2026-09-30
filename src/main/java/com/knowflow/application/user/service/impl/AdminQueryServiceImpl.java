package com.knowflow.application.user.service.impl;

import com.knowflow.application.user.api.AdminQueryService;
import com.knowflow.application.user.model.entity.AdminEntity;
import com.knowflow.application.user.model.entity.QAdminEntity;
import com.knowflow.application.user.model.entity.QAdminRoleEntity;
import com.knowflow.application.user.model.request.AdminPageRequest;
import com.knowflow.application.user.model.response.AdminItemResponse;
import com.knowflow.application.user.repository.AdminRepository;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminQueryServiceImpl implements AdminQueryService {
  private final AdminRepository repository;
  private final JPAQueryFactory queryFactory;

  @Override
  public Optional<AdminEntity> findAdminByEmail(String email) {
    return repository.findByEmail(email);
  }

  @Override
  public Page<AdminItemResponse> page(AdminPageRequest adminPageRequest) {

    QAdminEntity qAdmin = QAdminEntity.adminEntity;
    QAdminRoleEntity qAdminRole = QAdminRoleEntity.adminRoleEntity;

    BooleanExpression booleanExpression = null;
    if (StringUtils.hasText(adminPageRequest.getEmail())) {
      booleanExpression = qAdmin.email.contains(adminPageRequest.getEmail());
    }

    Pageable pageable = adminPageRequest.toPageable();

    List<AdminItemResponse> content =
        queryFactory
            .select(
                Projections.constructor(
                    AdminItemResponse.class,
                    qAdmin.id,
                    qAdmin.email,
                    Expressions.stringTemplate("string_agg({0} , ',')", qAdminRole.role),
                    qAdmin.createTime))
            .from(qAdmin)
            .leftJoin(qAdminRole)
            .on(qAdminRole.userId.eq(qAdmin.id))
            .where(booleanExpression)
            .groupBy(qAdmin.id)
            .limit(pageable.getPageSize())
            .offset(pageable.getOffset())
            .fetch();

    JPAQuery<Long> count =
        queryFactory.select(qAdmin.id.count()).from(qAdmin).where(booleanExpression);

    return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
  }
}
