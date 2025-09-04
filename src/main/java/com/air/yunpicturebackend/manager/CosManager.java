package com.air.yunpicturebackend.manager;

import cn.hutool.core.io.FileUtil;
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
import java.util.LinkedList;
import java.util.List;

/**
 * COS通用方法定义
 */
@Component
public class CosManager {  
  
    @Resource
    private CosClientConfig cosClientConfig;
  
    @Resource  
    private COSClient cosClient;

    /**
     * 上传对象
     * 官方文档中上传文件的案例
     * @param key  唯一键
     *             对象键(Key)是对象在存储桶中的唯一标识
     *             指定文件上传到 COS 上的路径，即对象键。例如对象键为 folder/picture.jpg
     *             则表示将文件 picture.jpg 上传到 folder 路径下
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }


    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {        //获取哪个存储桶中的哪个文件，这个项目中桶是固定的
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
        // COSObject 对象存储的对象，我们可以从这个对象中拿到文件的流
    }


    /**
     * 上传对象（附带图片信息，上传的基础上多加了一个解析图片基本信息） + 图片处理
     * @param key  唯一键
     * @param file 文件
     */                   // 对象键(Key)是对象在存储桶中的唯一标识。形如这样 /public/1960965073795543041/2025-09-02_tfTDQvUcxt8q5kAn.jpg
    public PutObjectResult putPictureObject(String key, File file) {
        //上传文件
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
                               //如果我设置的Key是这样： /public/1960965073795543041/2025-09-02_tfTDQvUcxt8q5kAn.jpg
                           // 那么最终存到cos中的文件名是：2025-09-02_tfTDQvUcxt8q5kAn.jpg
                      // /会被视为目录分隔符，COS 控制台会按目录结构展示文件（如 public/1960965073795543041/ 是两级目录）

        //对图片进行处理（获取图片的基本信息也被视做为一种图片的处理）
        //图片上传完成后，对象存储（Cloud Object Storage，COS）会存储原始图片和已处理过的图片。
        //官方文档：https://cloud.tencent.com/document/product/436/55377
        //设置参数为 1 表示返回原图信息
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1); //是否返回原图信息，0不返回原图信息，1返回原图信息，默认为0

        // 添加图片处理规则
        String webpKey = FileUtil.mainName(key) + ".webp";
        List<PicOperations.Rule> ruleList = new LinkedList<>();
        PicOperations.Rule rule1 = new PicOperations.Rule();
        rule1.setBucket(cosClientConfig.getBucket());
        rule1.setFileId(webpKey); //存到对应目录中的文件名，也就是处理后的文件名称
        rule1.setRule("imageMogr2/format/webp"); //将原图转换为 webp 格式，达到图片压缩效果。
        ruleList.add(rule1);
        picOperations.setRules(ruleList);

        //Pic-Operations:
        //{
        //"is_pic_info": 1,
        //"rules": [{
        //    "fileid": "exampleobject",
        //    "rule": "imageMogr2/format/webp"
        //}]
        //}
        //其实我们上面的操作都是再构建这么一个规则

        //构造处理参数
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }
}
