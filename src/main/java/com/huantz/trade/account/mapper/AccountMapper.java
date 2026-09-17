package com.huantz.trade.account.mapper;

import com.huantz.trade.account.model.dto.SellerContactDto;
import com.huantz.trade.account.model.entity.AccountEntity;
import com.huantz.trade.account.model.entity.AccountImageEntity;
import com.huantz.trade.account.model.entity.SellerContact;
import com.huantz.trade.account.model.request.AccountCreateRequest;
import com.huantz.trade.account.model.request.AccountUpdateRequest;
import com.huantz.trade.account.model.response.AccountDetailResponse;
import com.huantz.trade.account.model.response.AccountImageResponse;
import com.huantz.trade.account.model.response.AccountPageResponse;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper
public interface AccountMapper {

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(source = "seller", target = "sellerContact")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "accountStatus", ignore = true)
  @Mapping(target = "firstPrice", ignore = true)
  void updateEntityFromRequest(AccountUpdateRequest request, @MappingTarget AccountEntity entity);

  @Mapping(source = "seller", target = "sellerContact")
  AccountEntity toEntity(AccountCreateRequest accountCreateRequest);

  SellerContact toSellerContact(SellerContactDto dto);

  SellerContactDto toSellerContactDto(SellerContact entity);

  AccountImageResponse toImageResponse(AccountImageEntity entity);

  List<AccountImageResponse> toImageResponseList(List<AccountImageEntity> entities);

  @Mapping(source = "entity.sellerContact.name", target = "sellerName")
  @Mapping(source = "entity.sellerContact.gender", target = "sellerGender")
  @Mapping(source = "entity.sellerContact.phone", target = "sellerPhone")
  @Mapping(source = "entity.sellerContact.wechat", target = "sellerWechat")
  AccountPageResponse toPageResponse(AccountEntity entity, String sectName, String serverName);

  @Mapping(source = "entity.sellerContact", target = "seller")
  AccountDetailResponse toDetailResponse(
      AccountEntity entity, String sectName, String serverName, List<AccountImageResponse> images);
}
