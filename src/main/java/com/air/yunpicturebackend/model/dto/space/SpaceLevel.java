package com.air.yunpicturebackend.model.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
/**
 * 用于给前端展示所有的‌空间级别信息
 */
public class SpaceLevel {

    //    COMMON("普通版", 0, 100, 100L * 1024 * 1024),  //100 MB
    //    PROFESSIONAL("专业版", 1, 1000, 1000L * 1024 * 1024),  //1000 MB
    //    FLAGSHIP("旗舰版", 2, 10000, 10000L * 1024 * 1024);  //10 GB

    /**
     * 0，1，2
     */
    private int value;

    /**
     * 普通版，专业版，旗舰版
     */
    private String text;

    /**
     * 最大存储的图片数量
     */
    private long maxCount;

    /**
     * 最大存储的空间大小
     */
    private long maxSize;
}
