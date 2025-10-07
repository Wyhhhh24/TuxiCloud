package com.air.yunpicturebackend.api.aliyunai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建扩图任务响应类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOutPaintingTaskResponse {

    /**
     *  通过一个类构造响应体，将调用接口返回的 json 序列化成这个对象
     *  成功响应：
     * {
     *     "output": {
     *         "task_status": "PENDING",
     *         "task_id": "0385dc79-5ff8-4d82-bcb6-xxxxxx"
     *     },
     *     "request_id": "4909100c-7b5a-9f92-bfe5-xxxxxx"
     * }
     *
     *  失败响应：
     * {
     *     "code":"InvalidApiKey",
     *     "message":"Invalid API-key provided.",
     *     "request_id":"fb53c4ec-1c12-4fc4-a580-xxxxxx"
     * }
     */

    private Output output;

    /**
     * 表示任务的输出信息
     */
    @Data
    public static class Output {

        /**
         * 任务 ID
         */
        private String taskId;

        /**
         * 任务状态
         * <ul>
         *     <li>PENDING：排队中</li>
         *     <li>RUNNING：处理中</li>
         *     <li>SUSPENDED：挂起</li>
         *     <li>SUCCEEDED：执行成功</li>
         *     <li>FAILED：执行失败</li>
         *     <li>UNKNOWN：任务不存在或状态未知</li>
         * </ul>
         */
        private String taskStatus;
    }

    /**
     * 接口错误码。
     * <p>接口成功请求不会返回该参数。</p>
     */
    private String code;


    /**
     * 接口错误信息。
     * <p>接口成功请求不会返回该参数。</p>
     */
    private String message;


    /**
     * 请求唯一标识。
     * <p>可用于请求明细溯源和问题排查。</p>
     */
    private String requestId;
}
