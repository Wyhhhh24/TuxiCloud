package com.air.yunpicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * @author WyH524
 * @since 2025/9/19 15:20
 * 空间类型枚举类
 */
@Getter
public enum SpaceTypeEnum {

    PRIVATE(0,"私有空间" ),
    TEAM(1,"团队空间");

    private final int value;

    private final String text;

    SpaceTypeEnum(int value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据 value 获取 枚举
     */
    public static SpaceTypeEnum getEnumByValue(int value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (SpaceTypeEnum valueEnum : values()) {
            if (valueEnum.value == (value)) {
                return valueEnum;
            }
        }
        return null;
    }
}
