package com.air.yunpicturebackend.manager;
import cn.hutool.core.io.FileUtil;
import com.air.yunpicturebackend.config.CosClientConfig;
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
        // COSObject 对象存储对象，我们可以从这个对象中拿到文件的流
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
                      // / 会被视为目录分隔符，COS 控制台会按目录结构展示文件（如 public/1960965073795543041/ 是两级目录）

        //对图片进行处理（获取图片的基本信息也被视做为一种图片的处理）
        //图片上传完成后，对象存储（Cloud Object Storage，COS）会存储原始图片和已处理过的图片。
        //官方文档：https://cloud.tencent.com/document/product/436/55377
        //设置参数为 1 表示返回原图信息
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1); //是否返回原图信息，0不返回原图信息，1返回原图信息，默认为0

        // 一、添加图片处理规则
        // 1.图片压缩，将原图转成 webp 格式
        String webpKey = FileUtil.mainName(key) + ".webp";
        List<PicOperations.Rule> ruleList = new LinkedList<>();
        PicOperations.Rule rule1 = new PicOperations.Rule();
        rule1.setBucket(cosClientConfig.getBucket());
        rule1.setFileId(webpKey); //存到对应目录中的文件名，也就是处理后的文件名称
        rule1.setRule("imageMogr2/format/webp"); //将原图转换为 webp 格式，达到图片压缩效果。
        //Pic-Operations:
        //{
        //"is_pic_info": 1,
        //"rules": [{
        //    "fileid": "exampleobject",
        //    "rule": "imageMogr2/format/webp"
        //}]
        //}
        //其实我们上面的 set ，都是按照上面这个规则来的

        ruleList.add(rule1);

        // 2.获取缩略图
        // 判断文件的大小是否超过 2KB ，超过才进行缩略
        if(file.length() > 2 * 1024){
            //如果上传的图片本身就比较小‌，缩略图反而比压缩图更大，还不如不缩‍略！仅对 > 20 KB 的图片生‍成缩略图
            PicOperations.Rule rule2 = new PicOperations.Rule();
            rule2.setBucket(cosClientConfig.getBucket());
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail."+FileUtil.getSuffix(key);
            rule2.setFileId(thumbnailKey);
            // 缩放规则 /thumbnail/<Width>x<Height>> 将图片缩放到宽度 ≤ 256px 且高度 ≤ 256px，并保持原图比例
            // > 表示 等比缩放，且缩放后的图片不会超过 256x256（即“限制矩形”）。
            // 如果原图是 500x300，缩放后会是 256x153.6（保持宽高比）。
            // 如果原图是 200x200（小于 256x256），则 不会放大，保持原尺寸。
            rule2.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 256, 256));
            ruleList.add(rule2);
        }

        //压缩图：强制转换为webp格式
        //缩略图：保持与原图相同的格式
        picOperations.setRules(ruleList);
        //构造处理参数
        putObjectRequest.setPicOperations(picOperations);

        // 实现文件的上传
        return cosClient.putObject(putObjectRequest);
    }


    /**
     * 释放 COS 中的图片资源
     */
    public void deleteObject(String key){
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }

}
