package com.lanxi.wms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UserForm {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    private String username;

    private String password;
    private String nickname;
    private String phone;
    private String email;
    private Integer status;

    private List<Long> roleIds;
}
