package com.air.yunpicturebackend.model.vo.space.anayze;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户空间上传行为分析响应
 * 结果需要返回时‌间区间和对应的图片数量
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpaceUserAnalyzeResponse implements Serializable {

    /**
     * 时间区间
     * 这里返回的时间区间是和前端请求所需的时间范围是对应的，有一点区别就是细节化了
     * 比如说第一周：12.1-12.7 第二周：12.8-12.14 ，是具体某一个时间区间字符串
     */
    private String period;

    /**
     * 返回这个时间区间内的上传数量
     */
    private Long count;

    private static final long serialVersionUID = 1L;
}
