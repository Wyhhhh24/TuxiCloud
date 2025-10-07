package com.air.yunpicturebackend.exception;

/**
 * @author WyH524
 * @since 2025/8/26 下午3:43
 * 抛异常的工具类
 */
public class ThrowUtils {

    /**
     * 条件成立则抛异常
     */
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }

    public static void throwIf(boolean condition, ErrorCode errorCode) {
        throwIf(condition, new BusinessException(errorCode));
                        // BusinessException 继承了 RuntimeException
    }

    public static void throwIf(boolean condition, ErrorCode errorCode,String message) {
        throwIf(condition, new BusinessException(errorCode,message));
    }
}
