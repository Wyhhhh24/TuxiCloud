package com.air.yunpicturebackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Spring MVC Json 配置
 */
@JsonComponent
public class JsonConfig {

    /**
     * 但是，在测试؜中，如果你打开 F12 控制台，利用预览来查‌看响应数据，就会发现另一个问题：id 的最后‍两位好像都变成 0 了！
     * 但是在响应中、以及 Swagger 中查看，却是正常的
     *
     * 添加 Long 转 json 精度丢失的配置
     * 这是由于前؜端 JS 的精度范围有限，我们后端返回的‌ id 范围过大，导致前端精度丢失，会影‍响前端页面获取到的数据结果。
     * 为了解决这个问题，可以在后端 config 包下新建一个全局 JSON 配置，将整个后端 Spring MVC 接口返回值的长整型数字转换为字符串进行返回，从而集中解决问题。
     */
    @Bean
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);
        return objectMapper;
    }
}
