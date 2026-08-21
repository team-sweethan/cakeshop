package com.cakeshop.domain.product.mapper;

import com.cakeshop.domain.product.dto.view.ProductCartThumbnail;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductCartMapper {

    List<ProductCartThumbnail> findThumbnailsByProductIds(
            @Param("productIds") List<Long> productIds
    );
}
