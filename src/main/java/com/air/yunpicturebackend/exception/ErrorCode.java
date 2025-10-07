package com.air.yunpicturebackend.exception;

import lombok.Getter;
/**
 * 自定义状态码的用途
 * 前后端统一规范：定义一套业务状态码，让前端能根据不同的 code 执行不同的逻辑（如跳转登录页、显示错误提示等）
 * 标准化错误处理：避免直接返回 HTTP 状态码（如 404、500）导致前端难以区分业务错误和系统错误
 * 自定义状态码能帮助定位业务逻辑问题
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),

    PARAMS_ERROR(40000, "请求参数错误"),

    NOT_LOGIN_ERROR(40100, "未登录"),

    NO_AUTH_ERROR(40101, "无权限"),

    NOT_FOUND_ERROR(40400, "请求数据不存在"),

    FORBIDDEN_ERROR(40300, "禁止访问"),

    SYSTEM_ERROR(50000, "系统内部异常"),

    OPERATION_ERROR(50001, "操作失败");


    /**
     * 状态码
     */
    private final int code;


    /**
     * 信息
     */
    private final String message;


    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
