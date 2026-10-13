package com.lanxi.wms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 物流订单创建表单
 */
@Data
public class LogisticsForm {

    /** 关联出库单ID（可选） */
    private Long outboundOrderId;

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "收货电话不能为空")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;

    private String remark;
}
