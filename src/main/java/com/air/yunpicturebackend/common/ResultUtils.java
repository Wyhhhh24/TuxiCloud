package com.air.yunpicturebackend.common;

import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;

import java.io.Serializable;

/**
 * @author WyH524
 * @since 2025/8/26 下午3:43
 * 响应工具类
 */
public class ResultUtils implements Serializable {

    /**
     * 成功
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 响应
     */
    public static <T> BaseResponse<T> success(T data) {
        // 调用全参构造
        return new BaseResponse<>(0, data, "ok");
    }

    /**
     *
     * 失败
     * 反正失败，data 为空，填充状态码以及错误信息
     *
     * @param errorCode 错误码
     * @return 响应
     */
    public static BaseResponse<?> error(ErrorCode errorCode) {
        // 传了code 、message ，data 为 null
        return new BaseResponse<>(errorCode);
    }


    /**
     * 失败
     *
     * @param code    错误码
     * @param message 错误信息
     * @return 响应
     */
    public static BaseResponse<?> error(int code, String message) {
        // 这里是给捕捉到的不是自定义异常返回统一结果用的，错误码以及错误信息根据异常进行返回
        return new BaseResponse<>(code, null, message);
    }


    /**
     * 失败
     *
     * @param errorCode 错误码
     * @return 响应
     */
    public static BaseResponse<?> error(ErrorCode errorCode, String message) {
        // 这里自定义错误信息，错误码用提前定义好的
        return new BaseResponse<>(errorCode.getCode(), null, message);
    }
}
