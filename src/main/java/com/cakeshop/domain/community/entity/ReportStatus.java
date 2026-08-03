package com.cakeshop.domain.community.entity;

/**
 * 게시글 신고의 처리 상태다. "관리자가 이 신고를 보고 조치했는가"를 뜻하며,
 * 게시글의 현재 노출 여부와는 별개다(docs/community/DOMAIN.md 6.6).
 */
public enum ReportStatus {
    PENDING,
    RESOLVED,
    REJECTED;

    /**
     * 전이 규칙은 docs/community/DOMAIN.md 6.6을 따른다. 처리된 신고는 조치의 기록이므로
     * 되돌리지 않는다 — 차단을 해제해도 RESOLVED는 그대로다(4.2의 blocked_* 보존과 같은 이유).
     */
    public boolean canTransitionTo(ReportStatus next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case PENDING -> next == RESOLVED || next == REJECTED;
            case RESOLVED, REJECTED -> false;
        };
    }
}
