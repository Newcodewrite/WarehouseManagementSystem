package com.lanxi.wms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatForm {

    @NotBlank(message = "消息不能为空")
    private String message;

    /** 历史对话 [{role: user|assistant, content: ...}] */
    private List<Map<String, String>> history;
}
