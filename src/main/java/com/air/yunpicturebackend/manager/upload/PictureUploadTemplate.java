package com.air.yunpicturebackend.manager.upload;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.air.yunpicturebackend.config.CosClientConfig;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import com.air.yunpicturebackend.manager.CosManager;
import com.air.yunpicturebackend.model.dto.file.UploadPictureResult;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * @author WyH524
 * @since 2025/8/31 下午10:06
 * 图片上传模板
 */
@Service
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;


    /**
     * 上传图片（附带图片信息的）
     *
     * @param inputSource    文件
     * @param uploadPathPrefix 上传路径前缀
     * @return
     */                           //上传文件的路径，由于这里我们是一个通用的文件上传的方法，我们指定文件上传的前缀，而不是具体的路径名
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1.校验图片
        validPicture(inputSource);

        // 2.构建图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originFilename = getOriginFilename(inputSource);
        // 拼接文件名，得到最后存储到对象存储中的文件名
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originFilename)); //FileUtil.getSuffix(originFilename) 提取文件的扩展名
        //拼接上传的具体路径，这里如果多个项目用一个存储桶的话，存储的路径还可以添加一个 /projectName 以项目进行区分
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);
                    //得到：  /public/1960965073795543041/2025-09-02_tfTDQvUcxt8q5kAn.jpg

        File file = null;
        try {
            // 3.创建临时文件，获取文件到服务器
            // 文件名其实可以随便设置，只要不重复，这里直接用了对象存储中的具体路径名
            file = File.createTempFile(uploadPath, null);
            // 上传的文件 转化到 临时文件
            // 处理文件来源
            processFile(inputSource,file);

            // 4.上传图片到对象存储，返回结果中包含图片的基本信息
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            // 从返回参数中，可以得到原图的基本信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            // 5.获取图片处理后的图片信息，封装返回结果
            // 取出来处理图片的结果，这是一个列表
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            // 得到处理转换后的图片列表，其实列表中只有一个元素，因为我们只写了一个图片处理规则
            List<CIObject> objectList = processResults.getObjectList();
            if(CollUtil.isNotEmpty(objectList)){
                //如果处理后的图片为空，是不是就表示处理失败了，或者压根没有写处理规则
                //获取压缩之后，得到的文件信息
                CIObject ciCompressObject = objectList.get(0);
                //封装压缩图的返回方法
                return buildResult(originFilename, ciCompressObject);
                //将压缩后的图片信息存到数据库中
                //todo 后端数据库中保存的图片url是压缩后的 webp 文件，原图的url还没有保存，压缩后的图片基本上一点不模糊，扩
                // 展：1.增加对原图的处理，目前每次上传图片؜实际上会保存原图和压缩图2个图片，原图占用的空间还是比较‌大的。如果想进一步优化，可以删除原图，只保留缩略图
                // ；或者在数‍据库中保存原图的地址，用作备份。2）尝试更大比例的压缩，比如使用 质量变换 来处理图片。

            }

            // 6.如果处理失败封装原图的信息，返回结果
            return buildResult(imageInfo, originFilename, file, uploadPath);
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        } finally {
            // 6.最后清理临时文件
            this.deleteTempFile(file);
        }
    }

    /**
     * 封装返回结果
     * 压缩后的图片信息封装
     */
    private UploadPictureResult buildResult(String originFilename, CIObject ciCompressObject) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = ciCompressObject.getWidth();
        int picHeight = ciCompressObject.getHeight();
        //计算图片的 宽高比（Aspect Ratio），并保留两位小数
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        //设置文件原始名称
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(ciCompressObject.getFormat());
        //设置文件大小
        uploadPictureResult.setPicSize(ciCompressObject.getSize().longValue());
        //拼接可直接访问的 url
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + ciCompressObject.getKey());
        return uploadPictureResult;
    }


    /**
     * 封装返回结果
     * 原图的信息封装
     */
    private UploadPictureResult buildResult(ImageInfo imageInfo, String originFilename, File file, String uploadPath) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        //计算图片的 宽高比（Aspect Ratio），并保留两位小数
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        //设置文件原始名称
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        //设置文件大小
        uploadPictureResult.setPicSize(FileUtil.size(file));
        //拼接可直接访问的 url
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        return uploadPictureResult;
    }



    /**
     * 校验输入源（本地文件或 URL）
     */
    protected abstract void validPicture(Object inputSource);

    /**
     * 获取输入源的原始文件名
     */
    protected abstract String getOriginFilename(Object inputSource);

    /**
     * 处理输入源并生成本地临时文件
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;


    /**
     * 删除临时文件
     */
    public void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        // 删除临时文件
        boolean deleteResult = file.delete();
        if (!deleteResult) {                                //获取未删除的临时文件的绝对路径
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }
    }

}
