package com.air.yunpicturebackend.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.air.yunpicturebackend.common.BaseResponse;
import com.air.yunpicturebackend.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

//全局异常处理器
@RestControllerAdvice //环绕切面，就可以在这个类中写一些切点
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 如果 Sa-Token 校验用户没有符合要求的权限、或者用户未登录，就会抛出它定义的异常，参考文档。
     * 需要将框架؜的异常全局处理为我们自己定义的业务异‌常
     */
    // 捕获 sa-token 的未登录异常，把它改成我们自己的未登录异常，这样我们就统一了异常，前端不会看到 sa-token 抛出我们未处理过的异常
    @ExceptionHandler(NotLoginException.class)
    public BaseResponse<?> notLoginException(NotLoginException e) {
        log.error("NotLoginException", e);
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, e.getMessage());
    }

    //捕获 sa-token 的无权限异常，把它改成我们自己的无权限异常，这样我们就统一了异常，前端不会看到 sa-token 抛出我们未处理过的异常
    @ExceptionHandler(NotPermissionException.class)
    public BaseResponse<?> notPermissionExceptionHandler(NotPermissionException e) {
        log.error("NotPermissionException", e);
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, e.getMessage());
    }


    // BusinessException 继承自 RuntimeException，因此它属于 "运行时异常" 的一种。
    // 但 @ExceptionHandler 会优先匹配 最具体的异常类型（即 BusinessException），而不是父类 RuntimeException。
    // 虽然 BusinessException 是 RuntimeException 的子类，但异常处理机制会优先匹配具体子类


    //对任何的业务异常进行处理
    //只要在项目中任何一个方法中抛出BusinessException异常，都会被我们的环绕切面给捕获到
    //然后我们就可以在这个方法里去写怎么处理这个异常
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e); //打印异常的信息
        return ResultUtils.error(e.getCode(), e.getMessage());
        //给前端返回一个更友好的封装之后的错误信息，封装为我们自己的返回
    }

    //封装一个更广泛的异常处理
    //项目运行时，还有可能抛出很多各种各样的运行时异常，不是 BusinessException 的异常我们就在这里被捕获到
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}

