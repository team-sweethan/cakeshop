package com.cakeshop.global.security;

import com.cakeshop.domain.member.entity.Member;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.List;

@Getter
public class MemberDetails extends User {

    private final Member member;

    public MemberDetails(Member member) {
        // 부모(User) 생성자 호출: 이메일, 비밀번호, 권한목록
        super(member.getEmail(), member.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole())));

        this.member = member; // member 필드 초기화
    }

    public Long getMemberId(){
        return member.getId();
    }
}