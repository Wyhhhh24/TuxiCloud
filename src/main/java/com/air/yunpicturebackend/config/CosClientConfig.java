package com.air.yunpicturebackend.config;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通过 @ConfigurationProperties 注解
 * 将配置文件中的值自动填充到对应字段上
 * secret-id -> secretId
 * secret_key -> secretKey
 */
@Configuration
@ConfigurationProperties(prefix = "cos.client")
@Data
public class CosClientConfig {
    /**
     * 域名
     */
    private String host;

    /**
     * secretId
     */
    private String secretId;

    /**
     * 密钥（注意不要泄露）
     */
    private String secretKey;

    /**
     * 域名 https://xxx.cos.ap-guangzhou.myqcloud.com
     */
    private String region;

    /**
     * 桶名
     */
    private String bucket;

    /**
     * 在配置类中，同时也可以注册一个 Bean ，初始化 COS 客户端
     */
    @Bean
    public COSClient cosClient() {
        // 初始化用户身份信息(secretId, secretKey)
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        // 设置bucket的区域, COS地域的简称请参照 https://www.qcloud.com/document/product/436/6224
        ClientConfig clientConfig = new ClientConfig(new Region(region));

        // 官方文档，中有这一步，但是这里我们不设置这个配置的话，也是可以运行的
        // 官方文档写着，这里建议设置使用 https 协议
        // 从 5.6.54 版本开始，默认使用了 https
        // clientConfig.setHttpProtocol(HttpProtocol.https);

        // 直接生成cos客户端，并返回为一个 Bean ，之后各种操作都可以通过注入这个 Bean 实现
        return new COSClient(cred, clientConfig);
    }
}
