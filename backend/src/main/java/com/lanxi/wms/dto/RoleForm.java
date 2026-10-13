package com.lanxi.wms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoleForm {

    private Long id;

    @NotBlank(message = "角色编码不能为空")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    private String name;

    private String perms;
    private String remark;
}
