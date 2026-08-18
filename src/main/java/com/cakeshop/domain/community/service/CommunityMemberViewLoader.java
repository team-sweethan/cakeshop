package com.cakeshop.domain.community.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.member.dto.view.MemberCommunityView;
import com.cakeshop.domain.member.service.MemberCommunityQueryService;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CommunityMemberViewLoader {

    private final MemberCommunityQueryService memberCommunityQueryService;

    Map<Long, MemberCommunityView> findByIds(Stream<Long> memberIds) {
        List<Long> distinctIds = memberIds
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return memberCommunityQueryService.getMembersByIds(distinctIds).stream()
                .collect(Collectors.toMap(MemberCommunityView::id, Function.identity()));
    }
}
