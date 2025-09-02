package com.air.yunpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import com.air.yunpicturebackend.exception.ThrowUtils;
import com.air.yunpicturebackend.manager.FileService;
import com.air.yunpicturebackend.model.dto.file.UploadPictureResult;
import com.air.yunpicturebackend.model.dto.picture.PictureQueryRequest;
import com.air.yunpicturebackend.model.dto.picture.PictureReviewRequest;
import com.air.yunpicturebackend.model.dto.picture.PictureUploadRequest;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.enums.PictureReviewStatusEnum;
import com.air.yunpicturebackend.model.vo.PictureVO;
import com.air.yunpicturebackend.model.vo.UserVO;
import com.air.yunpicturebackend.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.air.yunpicturebackend.model.entity.Picture;
import com.air.yunpicturebackend.service.PictureService;
import com.air.yunpicturebackend.mapper.PictureMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author 30280
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-08-31 21:44:02
*/
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureService{

    @Resource
    private FileService fileService;

    @Resource
    private UserService userService;

    /**
     * 上传图片（包含新上传的图片，以及修改图片）
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        //1.校验参数(如果用户没登录，就不能上传文件)
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        //2.判断是新增图片还是更新图片，从 PictureUploadRequest 中看是否能获取得到pictureId, 无就是新增，有就是更新
        Long pictureId = null;
        if(pictureUploadRequest != null){
            pictureId = pictureUploadRequest.getId();
        }
        //如果是更新，查询图片在数据库中是否存在
        if(pictureId != null){
            //查找老的图片
            Picture oldPicture = getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR,"图片不存在");

            //添加判断的逻辑，因为现在用户和管理员都可以更新图片，所以如果该图片既不是自己的，同时也不是管理员的话，他就不能更新图片
            if(!oldPicture.getUserId().equals(loginUser.getId()) && userService.isAdmin(loginUser)){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"仅本人或管理员可更新图片");
            }
        }

        //3.上传图片，得到图片信息（如果图片存在，无论是更新还是第一次上传，都是要上传图片）
        //前缀，我们可以按照userId来划分目录，由于我们这个项目是公共图库，所以每一个用户就有自己的一个目录
        //之后我们去开发私有空间，什么企业空间，专属空间，所以我们现在给前缀进行划分
        //把所有公开的图片上传到 public 目录下，之后如果有私有就上传到 private 目录下，按照 userId 划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
                                             //这里是最重要的，设置图片的路径

        //上传，并得到我们解析之后的信息
        UploadPictureResult uploadPictureResult = fileService.uploadPicture(multipartFile, uploadPathPrefix);

        //构造存到数据库中的信息
        Picture picture = BeanUtil.copyProperties(uploadPictureResult, Picture.class);
        //填充未能拷贝的信息
        picture.setName(uploadPictureResult.getPicName());
        picture.setUserId(loginUser.getId());

        //填充审核的信息
        //如果是管理员上传或者更新图片，则审核自动通过 填充全部的 review 信息
        //如果是用户上传或者更新图片，则审核未通过，只填充 reviewStatus
        fillReviewParams(picture, loginUser);

        //4.操作数据库
        //如果 pictureId 不为空，说明是更新图片，需要补充一些信息
        if(pictureId != null){
            //需要补充更新时间和 pictureId
            picture.setEditTime(new Date());
            picture.setId(pictureId);
        }
        //更新或者新增都要保存到数据库
        boolean result = saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"图片上传失败，数据库操作失败");

        //返回VO对象
        return PictureVO.objToVo(picture);
    }

    /**
     * 获取图片封装（PictureVO）包含了用户信息 UserVO
     * 编写获؜取图片封装的方法，可以为原有的图片关‌联创建用户的信息
     * @param picture
     * @param request
     * @return
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            //根据该图片所属的用户id，查询用用户，将信息并封装到 VO 对象中
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 分页获取图片封装 （Page<PictureVO>）每一个 PictureVO 都包含 UserVO
     * 注意，这里我们؜做了个小优化，不是针对每条数据都查询一次用户
     * 而是先获取‌到要查询的用户 id 列表，只发送一次查询用户表的请求，‍再将查到的值设置到图片对象中
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();

        //构造 Page<PictureVO> 对象，后续进行补充
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());

        //如果查到的数据为空，也就不需要再进行封装了
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }

        // 对象列表 => 封装对象列表 ，将全部的 Picture 对象，转换为 PictureVO对象
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());

        // 1. 关联查询用户信息
        // 获取所有的用户Id，得到一个集合
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .collect(Collectors.toSet());

        //根据用户ID列表查询用户数据，并将结果按用户ID分组转换为一个Map
        //这里其实就是一个优化算法，我们可以根据用户ID列表查找出 List<User> 得到用户列表
        //但是我们如果想要每一个pictureVO绑定一个User的话，还得要从List里面一个一个遍历
        //我们可以将List转换成Map，然后通过Map的get方法，从Map中获取对应的值，这样查找效率更高
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet)
                .stream()
                .collect(Collectors.groupingBy(User::getId));

        // 2. 填充信息，根据的是 PictureVO 中的 userID 和 User 中的 ID 进行匹配，再进行绑定
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }


    /**
     * 图片审核（管理员）
     * @param pictureReviewRequest
     * @param loginUser
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1.校验参数
        Long id = pictureReviewRequest.getId(); //图片id
        Integer reviewStatus = pictureReviewRequest.getReviewStatus(); //审核状态

        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);

        //图片id不能为空，图片审核的状态也不能为空，不允许把 通过 或 拒绝 的审核状态再改为 待审核，
        //那么当前用户要修改的状态也不能为待审核，也就是传过来的审核状态参数不能是 待审核
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2.判断图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR,"图片不存在");

        // 3.校验该图片的审核状态是否重复，假如说它已经是通过状态了，你就不能再把它改为通过状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }

        // 4.数据库操作，更新审核状态
        Picture updatePicture = new Picture();
        //这里新建一个 Picture 对象，不用上面那个是为了更高效的执行 update 操作，不是全部数据都修改一遍
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());

        boolean result = updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }


    /**
     * 填充审核参数
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser){
        if(userService.isAdmin(loginUser)){
            //如果是管理员，自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
            picture.setReviewMessage("管理员自动过审");
        }else{
            //非管理员，无论是编辑还是创建，默认都是 待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }


    /**
     * 图片数据校验方法，用于更新和修改图片时进行判断
     * 可以根据自己的需要，补充更多校验规则，这里只对 图片id，url，introduction进行校验
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }


    /**
     * 获取查询条件
     * searchText 支持同时从 name 和 introduction 中检索，可以用 queryWrapper 的 or 语法构造查询条件。
     * 由于 tags 在数据库中存储的是 JSON 格式的字符串，如果前端要传多个 tag（必须同时符合才算命中）
     * 需要遍历 tags 数组，每个标签都使用 like 模糊查询，将这些条件组合在一起
     * @param pictureQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            //如果传过来的参数为空，也就不用进行构造了
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        // 从多字段中搜索
        // 等效于这个 sql 语句
        // SELECT * FROM your_table WHERE ...其他条件... AND (name LIKE '%风景%' OR introduction LIKE '%风景%')
        // 如下的操作，作用：动态拼接 SQL 查询条件，用于实现按关键词模糊搜索的功能
        if (StrUtil.isNotBlank(searchText)) {
            // 动态拼接 sql 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }

        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);


        // JSON 数组查询 ，如果前端要传多个 tag，必须同时符合才算命中
        //SELECT * FROM your_table WHERE tags LIKE '%"风景"%' AND tags LIKE '%"动物"%'
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 得传三个参数来进行控制
        // 排序               条件：排序字段是否非空     是否升序（true=升序，false=降序）         排序字段名
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }
}




