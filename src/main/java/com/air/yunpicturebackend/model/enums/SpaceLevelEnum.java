package com.air.yunpicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 每个级别的空间对应的限额
 * 还有另外一种定؜义空间级别限额的方式，
 * 比如将空间限额配置存储在外部文件（如 JSON 文件或 properties 文件），
 * 并创建一个单独的类来接收参数。这样后期如果有变动，修‍改配置文件即可，而不必修改代码
 */
@Getter
public enum SpaceLevelEnum {

    COMMON("普通版", 0, 100, 100L * 1024 * 1024),  //100 MB
    PROFESSIONAL("专业版", 1, 1000, 1000L * 1024 * 1024),  //1000 MB
    FLAGSHIP("旗舰版", 2, 10000, 10000L * 1024 * 1024);  //10 GB

    private final String text;

    private final int value;

    /**
     * 最大存储图片总数量
     */
    private final long maxCount;

    /**
     * 最大存储图片的总大小
     */
    private final long maxSize;


    /**
     * @param text 文本
     * @param value 值
     * @param maxSize 最大图片总大小
     * @param maxCount 最大图片总数量
     */
    SpaceLevelEnum(String text, int value, long maxCount, long maxSize) {
        this.text = text;
        this.value = value;
        this.maxCount = maxCount;
        this.maxSize = maxSize;
    }

    /**
     * 根据 value 获取枚举
     */
    public static SpaceLevelEnum getEnumByValue(Integer value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (SpaceLevelEnum spaceLevelEnum : SpaceLevelEnum.values()) {
            if (spaceLevelEnum.value == value) {
                return spaceLevelEnum;
            }
        }
        return null;
    }
}
