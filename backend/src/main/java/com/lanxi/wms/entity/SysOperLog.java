package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String module;
    private String description;
    private String method;
    private String params;
    private String ip;
    private Long costMs;
    /** 0失败 1成功 */
    private Integer status;
    private String errorMsg;
    private LocalDateTime createTime;
}
