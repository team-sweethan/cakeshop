package com.cakeshop.domain.member.mapper;

import com.cakeshop.domain.member.entity.EmailVerification;
import com.cakeshop.domain.member.entity.EmailVerificationPurpose;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmailVerificationMapper {

    /** 이메일과 인증 목적 단위의 DB 잠금을 획득한다. */
    int acquireRequestLock(
            @Param("email") String email,
            @Param("purpose") EmailVerificationPurpose purpose,
            @Param("timeoutSeconds") int timeoutSeconds);

    /** 이메일과 인증 목적 단위의 DB 잠금을 해제한다. */
    int releaseRequestLock(
            @Param("email") String email,
            @Param("purpose") EmailVerificationPurpose purpose);

    /** 새 인증 요청을 저장한다. */
    int insert(EmailVerification verification);

    /** 이메일과 목적에 해당하는 가장 최근 인증 요청을 조회한다. */
    Optional<EmailVerification> findLatest(
            @Param("email") String email,
            @Param("purpose") EmailVerificationPurpose purpose);

    /** 발송 제한을 확인하기 위해 기준 시각 이후 요청 수를 조회한다. */
    int countRequestsSince(
            @Param("email") String email,
            @Param("purpose") EmailVerificationPurpose purpose,
            @Param("since") LocalDateTime since);

    /** 아직 검증 가능한 요청의 실패 횟수를 한 번 증가시킨다. */
    int incrementAttemptCount(
            @Param("id") Long id,
            @Param("now") LocalDateTime now);

    /** 만료되지 않은 최신 요청을 인증 완료 상태로 변경한다. */
    int markVerified(
            @Param("id") Long id,
            @Param("email") String email,
            @Param("purpose") EmailVerificationPurpose purpose,
            @Param("verifiedAt") LocalDateTime verifiedAt);

    /** 회원가입에서 사용할 인증 완료 요청을 잠그고 조회한다. */
    Optional<EmailVerification> findVerifiedForUpdate(
            @Param("email") String email,
            @Param("purpose") EmailVerificationPurpose purpose,
            @Param("verifiedSince") LocalDateTime verifiedSince);

    /** 인증 완료 요청을 한 번만 사용 처리한다. */
    int markConsumed(
            @Param("id") Long id,
            @Param("consumedAt") LocalDateTime consumedAt);

    /** 보관 기간이 지난 인증 요청을 삭제한다. */
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
