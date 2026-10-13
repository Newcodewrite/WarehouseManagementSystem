package com.lanxi.wms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdjustForm {

    @NotNull(message = "仓库不能为空")
    private Long warehouseId;

    @NotNull(message = "商品不能为空")
    private Long productId;

    @NotNull(message = "调整数量不能为空")
    private Integer quantity;

    private String remark;
}
