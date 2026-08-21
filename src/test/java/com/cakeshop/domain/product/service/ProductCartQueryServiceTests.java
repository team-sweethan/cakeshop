package com.cakeshop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.product.dto.view.ProductCartThumbnail;
import com.cakeshop.domain.product.mapper.ProductCartMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductCartQueryServiceTests {

    @Mock
    private ProductCartMapper productCartMapper;

    @InjectMocks
    private ProductCartQueryService productCartQueryService;

    @Test
    void getThumbnails_emptyProductIds_skipsMapperQuery() {
        assertThat(productCartQueryService.getThumbnails(List.of())).isEmpty();

        verifyNoInteractions(productCartMapper);
    }

    @Test
    void getThumbnails_returnsProductOwnedRepresentativeImages() {
        List<ProductCartThumbnail> expected = List.of(
                new ProductCartThumbnail(10L, "/uploads/product/representative.jpg")
        );
        when(productCartMapper.findThumbnailsByProductIds(List.of(10L))).thenReturn(expected);

        assertThat(productCartQueryService.getThumbnails(List.of(10L))).isEqualTo(expected);
    }
}
