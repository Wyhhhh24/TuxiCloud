package com.air.yunpicturebackend;

import org.apache.shardingsphere.spring.boot.ShardingSphereAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {ShardingSphereAutoConfiguration.class}) // TODO 自动配置把它移除掉，项目启动的时候就不会出现 shardingsphere 找不到，报错找不到算法类的情况了
@MapperScan("com.air.yunpicturebackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true) // 开启可以引入代理
@EnableAsync // 开启异步
public class YunPictureBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(YunPictureBackendApplication.class, args);
    }
}
