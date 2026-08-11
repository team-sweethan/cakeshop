package com.cakeshop.domain.member.service;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.mapper.MemberMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberAuthenticationService {

    private final MemberMapper memberMapper;

    @Transactional(readOnly = true)
    public Optional<MemberAuthenticationView> findForAuthentication(String email) {
        return memberMapper.findByEmail(email).map(this::toAuthenticationView);
    }

    private MemberAuthenticationView toAuthenticationView(Member member) {
        return new MemberAuthenticationView(
                member.getId(),
                member.getEmail(),
                member.getPassword(),
                member.getRole(),
                member.getStatus() == MemberStatus.ACTIVE,
                resolveDisplayName(member));
    }

    private String resolveDisplayName(Member member) {
        if (member.getNickname() != null && !member.getNickname().isBlank()) {
            return member.getNickname();
        }
        if (member.getName() != null && !member.getName().isBlank()) {
            return member.getName();
        }
        return member.getEmail();
    }
}
