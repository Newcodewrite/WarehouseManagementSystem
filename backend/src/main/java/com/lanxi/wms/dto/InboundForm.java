package com.lanxi.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class InboundForm {

    @NotNull(message = "仓库不能为空")
    private Long warehouseId;

    private Long supplierId;
    private String remark;

    @NotEmpty(message = "入库明细不能为空")
    @Valid
    private List<ItemForm> items;
}
