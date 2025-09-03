package com.air.yunpicturebackend.service;

import com.air.yunpicturebackend.model.dto.picture.PictureQueryRequest;
import com.air.yunpicturebackend.model.dto.picture.PictureReviewRequest;
import com.air.yunpicturebackend.model.dto.picture.PictureUploadByBatchRequest;
import com.air.yunpicturebackend.model.dto.picture.PictureUploadRequest;
import com.air.yunpicturebackend.model.entity.Picture;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.vo.PictureVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
* @author 30280
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-08-31 21:44:02
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     *
     * @param inputSource 文件输入源
     * @param pictureUploadRequest  图片的 id ，用于修改，这就是请求体
     * @param loginUser 需要指定当前用户，因为我们要判断用户有没有权限上传
     * @return
     */
    PictureVO uploadPicture(Object inputSource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

    /**
     * 获取查询条件
     * 通过 PictureQueryRequest ，构造 QueryWrapper 条件
     * @param pictureQueryRequest
     * @return
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 获取图片封装
     * 编写获؜取图片封装的方法，可以为原有的图片关‌联创建用户的信息
     * @param picture
     * @param request
     * @return
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);


    /**
     * 获取图片分页封装
     * @param picturePage
     * @param request
     * @return
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 校验图片信息
     * @param picture
     */
    void validPicture(Picture picture);

    /**
     * 图片审核
     * 两种状态，1.审核通过，2.审核不通过 ，审核不通过的话直接抛异常，所以这个方法是不需要返回值的
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 填充审核参数
     * @param picture
     * @param loginUser
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );
}
