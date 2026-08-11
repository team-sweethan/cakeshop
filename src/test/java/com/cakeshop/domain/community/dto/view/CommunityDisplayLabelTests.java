package com.cakeshop.domain.community.dto.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.entity.ReportStatus;

import org.junit.jupiter.api.Test;

class CommunityDisplayLabelTests {

    @Test
    void postStatus_labelsMatchAdminSpec() {
        assertThat(PostStatus.values())
                .extracting(PostStatus::getLabel)
                .containsExactly("노출 중", "삭제됨", "차단됨");
    }

    @Test
    void reportStatus_labelsMatchAdminSpec() {
        assertThat(ReportStatus.values())
                .extracting(ReportStatus::getLabel)
                .containsExactly("미처리", "처리 완료", "기각됨");
    }

    @Test
    void postSort_optionsOwnRequestParameterAndLabel() {
        assertThat(PostSort.values())
                .extracting(PostSort::getParameter, PostSort::getLabel)
                .containsExactly(
                        assertThatTuple("LATEST", "최신순"),
                        assertThatTuple("VIEWS", "조회수순"));
    }

    @Test
    void adminPostSort_optionsOwnRequestParameterAndLabel() {
        assertThat(AdminPostSort.values())
                .extracting(AdminPostSort::getParameter, AdminPostSort::getLabel)
                .containsExactly(
                        assertThatTuple("LATEST", "최신순"),
                        assertThatTuple("REPORTS", "신고 많은 순"));
    }

    private org.assertj.core.groups.Tuple assertThatTuple(String parameter, String label) {
        return org.assertj.core.groups.Tuple.tuple(parameter, label);
    }
}
