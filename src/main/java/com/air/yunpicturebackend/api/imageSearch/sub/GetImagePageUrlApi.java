package com.air.yunpicturebackend.api.imageSearch.sub;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class GetImagePageUrlApi {

    /**
     * 获取图片页面地址
     *
     * @param imageUrl
     * @return
     */
    public static String getImagePageUrl(String imageUrl) {
        //观察浏览器控制台
        //以下是请求：https://graph.baidu.com/upload?uptime=1757824051565  中的载荷，也这个接口所需的表单参数
        //image : https%3A%2F%2Fair-wyh-1360725635
        //tn ：pc
        //from ：pc
        //image_source ： PC_UPLOAD_URL
        //sdkParams ： {"data":"9242e43d49b387593934af   这个可以不需要

        // 1. 准备请求参数
        Map<String, Object> formData = new HashMap<>();
        formData.put("image", imageUrl);
        formData.put("tn", "pc");
        formData.put("from", "pc");
        formData.put("image_source", "PC_UPLOAD_URL");
        // 获取当前时间戳
        long uptime = System.currentTimeMillis();
        // 拼接请求地址
        String url = "https://graph.baidu.com/upload?uptime=" + uptime;

        try {
            // 2. 调用 hutool 工具类，发送 POST 请求到百度接口
            HttpResponse response = HttpRequest.post(url)
                    .header("acs-token", RandomUtil.randomString(1))
                    // 添加一个随机的 acs-token，像一个反爬虫的机制，得要加这个请求头才可以访问成功
                    .form(formData)
                    .timeout(5000)
                    .execute();
            // 判断响应状态
            if (HttpStatus.HTTP_OK != response.getStatus()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            // 解析响应
            // 响应是这样的，我们就按照这个格式进行解析
            //{"status":0,"msg":"Success",
            // "data":{"url":"https://graph.baidu.com/s?card_key=\u0026entrance=GENERAL\u0026extUiData%5BisLogoShow%5D=1
            // \u0026f=all\u0026isLogoShow=1\u0026session_id=2485431150742180449\
            // u0026sign=1265ffc27939f3eab76c201757824399\u0026tpl_from=pc",
            // "sign":"1265ffc27939f3eab76c201757824399"}}

            String responseBody = response.body(); // 这个 body() 方法中已经释放资源了
            // 拿到这个返回值，我们进行转换，可以转成一个Map结构，就不定义一个类来接收了，大部分数据都是没有用的
            Map<String, Object> result = JSONUtil.toBean(responseBody, Map.class);

            // 3. 处理响应结果
            // 判断返回结果是否有效
            if (result == null || !Integer.valueOf(0).equals(result.get("status"))) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }

            // 从结果中取得 data 转换成 Map
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            String rawUrl = (String) data.get("url");
            // 这是一个原生的 URL ，需要对 URL 进行解码，否则请求不成功
            String searchResultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);
            // 判断 URL 是否为空
            if (searchResultUrl == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未返回有效结果");
            }
            return searchResultUrl;
        } catch (Exception e) {
            //上面这个过程中，可能会报一些异常，这里 try catch 一下
            log.error("搜索失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }
    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://air-wyh-1360725635.cos.ap-guangzhou.myqcloud.com/public/1960965073795543041/2025-09-05_3Um2GZUqjnoCkpLM_thumbnail.fDDjkYkZrK-nRckT6J6s4gHaLH";
        String result = getImagePageUrl(imageUrl);
        System.out.println("搜索成功，结果 URL：" + result);
    }
}
