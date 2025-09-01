package com.air.yunpicturebackend.common;

import com.air.yunpicturebackend.exception.ErrorCode;
import lombok.Data;

/**
 * @author WyH524
 * @since 2025/8/26 下午3:58
 */
//全局响应封装类
@Data
public class BaseResponse<T> {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
