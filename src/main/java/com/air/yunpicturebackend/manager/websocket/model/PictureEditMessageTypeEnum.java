package com.air.yunpicturebackend.manager.websocket.model;

import lombok.Getter;

/**
 * @author WyH524
 * @since 2025/9/25 16:42
 *
 * 我们的消息类型是可枚举的，我们的编辑动作也是可枚举的
 * 后面要多次用到这些枚举的具体的值，所以最好定义枚举类
 *
 * 图片编辑消息类型枚举
 * 服务端和客户端之间通讯可能会发送的消息类型
 */
@Getter
public enum PictureEditMessageTypeEnum {

    INFO("发送通知", "INFO"),
    ERROR("发送错误", "ERROR"),
    ENTER_EDIT("进入编辑状态", "ENTER_EDIT"),
    EXIT_EDIT("退出编辑状态", "EXIT_EDIT"),
    EDIT_ACTION("执行编辑操作", "EDIT_ACTION");

    private final String text;
    private final String value;

    PictureEditMessageTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static PictureEditMessageTypeEnum getEnumByValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (PictureEditMessageTypeEnum typeEnum : PictureEditMessageTypeEnum.values()) {
            if (typeEnum.value.equals(value)) {
                return typeEnum;
            }
        }
        return null;
    }
}

