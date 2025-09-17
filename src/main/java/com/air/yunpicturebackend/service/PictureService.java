package com.air.yunpicturebackend.service;

import com.air.yunpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.air.yunpicturebackend.model.dto.picture.*;
import com.air.yunpicturebackend.model.entity.Picture;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.vo.PictureVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author 30280
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-08-31 21:44:02
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片（url，本地图片上传）
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
     * @param loginUser
     * @return
     */
    PictureVO getPictureVO(Picture picture, User loginUser);


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
     * AI 扩图
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     * @return
     */
    CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
                                                               User loginUser);

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


    /**
     * 批量编辑图片
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    @Transactional(rollbackFor = Exception.class)
    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    /**
     * 删除图片
     * @param pictureId
     * @param loginUser
     */
    void deletePicture(long pictureId, User loginUser);

    /**
     * 清理 cos 中的图片文件
     * @param picture
     */
    void clearPictureFile(Picture picture);


    /**
     * 编辑图片
     * @param pictureEditRequest
     * @param loginUser
     */
    void editPicture(PictureEditRequest pictureEditRequest, User loginUser);

    /**
     * 公共的权限校验方法
     * 校验空间图片的权限
     * 删除图片和修改空间图片的权限是一样的，因为我们都是仅本人仅空间创建人才能修改，我们还要区分是公共图库还是私有空间
     * 公共图库的话是系统管理员也能改
     * 校验当前登录用户能不能看到这张图片
     */
    void checkPictureAuth(User loginUser, Picture picture);


    /**
     * 对哪个空间进行搜索，空间id
     * 用户要搜索图片的颜色色值，十六进制
     * 记录谁来查询，校验空间权限
     * 返回值就是查到的图片列表
     */
    List<PictureVO> searchPictureByColor(Long spaceId , String picColor,User loginUser);

}
