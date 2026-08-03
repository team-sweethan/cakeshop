package com.cakeshop.domain.member.mapper;

import java.util.List;

import com.cakeshop.domain.member.dto.view.MemberStatusHistoryView;
import com.cakeshop.domain.member.entity.MemberStatusHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberStatusHistoryMapper {

    int insert(MemberStatusHistory history);

    List<MemberStatusHistoryView> findByMemberId(
            @Param("memberId") Long memberId);
}
