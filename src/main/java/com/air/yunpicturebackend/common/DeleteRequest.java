package com.air.yunpicturebackend.common;

import lombok.Data;

import java.io.Serializable;

//通用的删除请求包装类，给其它类继承的
//接受要删除数据的 id 作为参数
//对于“删除某条数据” 这类通用的请求，可以封装统一的请‌求包装类
//用于接受前端传来的参数，之后相同参数的请求‍就不用专门再新建一个类了
@Data
public class DeleteRequest implements Serializable {
    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
