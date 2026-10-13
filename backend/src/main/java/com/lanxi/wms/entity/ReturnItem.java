package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 退货明细
 */
@Data
@TableName("wms_return_item")
public class ReturnItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long returnId;
    private Long productId;
    private Integer quantity;
    /** 质检分级 1可二次销售 2需翻新 3报废 */
    private Integer qcGrade;
    private String qcRemark;
}
