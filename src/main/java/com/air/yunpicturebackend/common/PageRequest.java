package com.air.yunpicturebackend.common;

import lombok.Data;

//通用的分页请求包؜装类
//接受页号、页面大小、排序字段、‌排序顺序参数
//对于 “分页”这类通用的请求，可以封装统一的请‌求包装类
//用于接受前端传来的参数，之后相同参数的请求‍就不用专门再新建一个类了。
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小
     */
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认降序）
     */
    private String sortOrder = "descend";
}
