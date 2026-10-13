package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仓库
 */
@Data
@TableName("base_warehouse")
public class Warehouse {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String address;
    /** 库容（件） */
    private Integer capacity;
    private String managerName;
    private String managerPhone;
    private Integer status;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
