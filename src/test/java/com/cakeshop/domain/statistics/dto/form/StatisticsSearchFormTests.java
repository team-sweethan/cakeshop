package com.cakeshop.domain.statistics.dto.form;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatisticsSearchFormTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validate_noDateRange_acceptsDefaultSearch() {
        StatisticsSearchForm form = new StatisticsSearchForm();

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void validate_onlyStartDate_rejectsIncompleteRange() {
        StatisticsSearchForm form = form(LocalDate.of(2026, 8, 1), null);

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("dateRangeComplete");
    }

    @Test
    void validate_startDateAfterEndDate_rejectsReversedRange() {
        StatisticsSearchForm form = form(
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 1)
        );

        assertThat(validator.validate(form))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("dateRangeOrdered");
    }

    @ParameterizedTest
    @CsvSource({
            "365, true",
            "366, false"
    })
    void validate_dateRangeBoundary_enforcesInclusiveMaximum(int endOffsetDays, boolean expectedValid) {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        StatisticsSearchForm form = form(startDate, startDate.plusDays(endOffsetDays));

        boolean valid = validator.validate(form).isEmpty();

        assertThat(valid).isEqualTo(expectedValid);
    }

    private StatisticsSearchForm form(LocalDate startDate, LocalDate endDate) {
        StatisticsSearchForm form = new StatisticsSearchForm();
        form.setStartDate(startDate);
        form.setEndDate(endDate);
        return form;
    }
}
