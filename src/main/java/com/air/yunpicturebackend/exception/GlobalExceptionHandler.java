package com.air.yunpicturebackend.exception;

import com.air.yunpicturebackend.common.BaseResponse;
import com.air.yunpicturebackend.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

//全局异常处理器
@RestControllerAdvice //环绕切面，就可以在这个类中写一些切点
@Slf4j
public class GlobalExceptionHandler {

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

