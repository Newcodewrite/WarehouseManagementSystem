package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入库单明细
 */
@Data
@TableName("wms_inbound_item")
public class InboundItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private BigDecimal price;
    /** 批次号 */
    private String batchNo;
    /** 生产日期 */
    private LocalDate productionDate;
    /** 到期日期 */
    private LocalDate expiryDate;
    /** 上架库位ID */
    private Long locationId;
}
