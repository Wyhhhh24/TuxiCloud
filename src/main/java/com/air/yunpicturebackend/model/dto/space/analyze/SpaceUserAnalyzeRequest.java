package com.air.yunpicturebackend.model.dto.space.analyze;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 空间用户上传行为分析请求封装类
 * 用户上传行为分析需要增加时间维度（日‌、周、月）和用户 ID 参数，支持只分析某个‍用户上传图片的情况
 * 除了继承查询范围字段之外，还要指定一个维度，比如说按照每日统计还是每周统计还是每月统计，就是这样的一个字符串
 * 还要记录一下是否要分析某一个用户，这样我们这个接口查询范围就更灵活了，可以分析当天所有用户的上传习惯，也可以分析当个时间段某一个用户的上传习惯
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceUserAnalyzeRequest extends SpaceAnalyzeRequest {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 时间维度：day / week / month
     */
    private String timeDimension;
}
