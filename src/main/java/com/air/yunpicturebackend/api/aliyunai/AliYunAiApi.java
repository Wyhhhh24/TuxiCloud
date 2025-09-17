package com.air.yunpicturebackend.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.air.yunpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.air.yunpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.air.yunpicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author WyH524
 * @since 2025/9/16 下午12:14
 * 阿里云ai扩图 api
 * 文档链接：https://help.aliyun.com/zh/model-studio/image-scaling-api?spm=a2c4g.11186623.help-menu-2400256.d_2_2_9.278816daSCDT5j&scm=20140722.H_2796845._.OR_help-T_cn~zh-V_1#c7cc6032c2nr5
 */
@Slf4j
@Component
public class AliYunAiApi {

    //读取配置文件
    @Value("${aliYunAi.accessKeyId}")
    private String apiKey;

    // 创建任务地址
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    // 查询任务状态
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s"; //这里需要拼接 taskId

    /**
     * 创建任务
     *
     * @param createOutPaintingTaskRequest  请求体
     * @return
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        if (createOutPaintingTaskRequest == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "扩图参数为空");
        }
        // 1.构建HTTP请求对象
        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                /**
                 * 请求头：
                 * Content-Type string （必选） 请求内容类型。此参数必须设置为application/json。
                 * Authorization string（必选）请求身份认证。接口使用阿里云百炼API-Key进行身份认证。示例值：Bearer sk-xxxx。
                 * X-DashScope-Async string （必选）异步处理配置参数。HTTP请求只支持异步，必须设置为enable。
                 *
                 * curl --location --request POST 'https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting' \
                 * --header "Authorization: Bearer $DASHSCOPE_API_KEY" \
                 * --header 'X-DashScope-Async: enable' \
                 * --header 'Content-Type: application/json' \
                 * --data '{
                 *     "model": "image-out-painting",
                 *     "input": {
                 *         "image_url": "http://xxx/image.jpg"
                 *     },
                 *     "parameters":{
                 *         "angle": 45,
                 *         "x_scale":1.5,
                 *         "y_scale":1.5
                 *     }
                 * }'
                 * 可以把这个粘贴到这里，然后 ai 的话会根据我们的注释逐渐构造我们的请求体
                 */
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                // 必须开启异步处理，设置为enable。
                .header("X-DashScope-Async", "enable")
                .header(Header.CONTENT_TYPE, ContentType.JSON.getValue())
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));    //将Java对象自动序列化为 Json 字符串作为请求体

        // 2.发请求的时候，try catch 一下 ，这里的对象需要释放资源，这里的 try 中构建对象的话，可以自动释放资源
        try (HttpResponse httpResponse = httpRequest.execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败");
            }
            CreateOutPaintingTaskResponse response = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            String errorCode = response.getCode();
            if (StrUtil.isNotBlank(errorCode)) {
                String errorMessage = response.getMessage();
                log.error("AI 扩图失败，errorCode:{}, errorMessage:{}", errorCode, errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图接口响应异常");
            }
            return response;
        }
    }

    /**
     * 查询创建的任务
     *
     * @param taskId
     * @return
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        // 1.参数校验
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务 id 不能为空");
        }
        // 2.发请求，这里的 response 放在 try 里面可以自动释放
        try (HttpResponse httpResponse = HttpRequest.get(String.format(GET_OUT_PAINTING_TASK_URL, taskId))
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                .execute()) {
            if (!httpResponse.isOk()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务失败");
            }
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }
}
