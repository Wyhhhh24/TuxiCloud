package com.air.yunpicturebackend.manager;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.air.yunpicturebackend.config.CosClientConfig;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import com.air.yunpicturebackend.exception.ThrowUtils;
import com.air.yunpicturebackend.model.dto.file.UploadPictureResult;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author WyH524
 * @since 2025/8/31 下午10:06
 */
@Service
@Slf4j
public class FileService {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;


    /**
     * 上传图片（附带图片信息的）
     *
     * @param multipartFile    文件
     * @param uploadPathPrefix 上传路径前缀
     * @return
     */                           //上传文件的路径，由于这里我们是一个通用的文件上传的方法，我们指定文件上传的前缀，而不是具体的路径名
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 校验图片
        validPicture(multipartFile);
        // 图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originFilename = multipartFile.getOriginalFilename();
        // 拼接文件名，得到最后存储到对象存储中的文件名
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originFilename));
        //拼接上传的具体路径，这里如果多个项目用一个存储桶的话，存储的路径还可以添加一个 /projectName 以项目进行区分
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);

        File file = null;
        try {
            // 创建临时文件，文件名其实可以随便设置，只要不重复，这里直接用了对象存储中的具体路径名
            file = File.createTempFile(uploadPath, null);
            // 上传的文件 转化到这个 临时文件
            multipartFile.transferTo(file);
            // 上传图片，返回结果中包含图片的基本信息
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            // 封装返回结果
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
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        } finally {
            //最后清理临时文件
            this.deleteTempFile(file);
        }
    }

    /**
     * 校验文件（对文件的大小，类型进行校验）
     *
     * @param multipartFile multipart 文件
     */
    public void validPicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 1. 校验文件大小，文件大小超过 2M 就报错
        long fileSize = multipartFile.getSize();
        final long ONE_M = 1024 * 1024L;
        ThrowUtils.throwIf(fileSize > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");
        // 2. 校验文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        // 允许上传的文件后缀
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件类型错误");
    }

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
