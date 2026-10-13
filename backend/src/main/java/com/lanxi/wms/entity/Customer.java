package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户
 */
@Data
@TableName("base_customer")
public class Customer {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String contactName;
    private String contactPhone;
    private String address;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
