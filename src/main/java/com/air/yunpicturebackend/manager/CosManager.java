package com.air.yunpicturebackend.manager;

import com.air.yunpicturebackend.config.CosClientConfig;
import com.air.yunpicturebackend.model.entity.Picture;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;

@Component
public class CosManager {  
  
    @Resource
    private CosClientConfig cosClientConfig;
  
    @Resource  
    private COSClient cosClient;

    /**
     * 上传对象
     * 官方文档中上传文件的案例（这里上传本地文件）
     * @param key  唯一键
     * @param file 文件
     */                   // 对象键(Key)是对象在存储桶中的唯一标识。
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }


    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {        //获取哪个存储桶中的哪个文件，这里桶是固定的
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
        //COSObject得到对象存储的对象，我们可以从这个对象中拿到文件的流
    }


    /**
     * 上传对象（附带图片信息，上传的基础上多加了一个解析）
     * @param key  唯一键
     * @param file 文件
     */                   // 对象键(Key)是对象在存储桶中的唯一标识。
    public PutObjectResult putPictureObject(String key, File file) {
        //上传文件
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        //对图片进行处理（获取图片的基本信息也被视做为一种图片的处理）
        // 1 表示返回原图信息
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1);
        //构造处理参数
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }
}
