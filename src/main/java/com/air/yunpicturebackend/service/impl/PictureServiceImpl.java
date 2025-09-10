package com.air.yunpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import com.air.yunpicturebackend.exception.ThrowUtils;
import com.air.yunpicturebackend.manager.CosManager;
import com.air.yunpicturebackend.manager.upload.FilePictureUpload;
import com.air.yunpicturebackend.manager.upload.PictureUploadTemplate;
import com.air.yunpicturebackend.manager.upload.UrlPictureUpload;
import com.air.yunpicturebackend.model.dto.file.UploadPictureResult;
import com.air.yunpicturebackend.model.dto.picture.*;
import com.air.yunpicturebackend.model.entity.Space;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.enums.PictureReviewStatusEnum;
import com.air.yunpicturebackend.model.vo.PictureVO;
import com.air.yunpicturebackend.model.vo.UserVO;
import com.air.yunpicturebackend.service.SpaceService;
import com.air.yunpicturebackend.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.air.yunpicturebackend.model.entity.Picture;
import com.air.yunpicturebackend.service.PictureService;
import com.air.yunpicturebackend.mapper.PictureMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
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
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureService{

    @Resource
    private UserService userService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private CosManager cosManager;

    @Resource
    private SpaceService spaceService;

    @Resource
    private TransactionTemplate transactionTemplate;


    /**
     * 上传图片（包含新上传的图片，以及修改图片）
     * @param inputSource 文件输入源
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    @Override
    public PictureVO uploadPicture(Object inputSource , PictureUploadRequest pictureUploadRequest, User loginUser) {
        //1.校验参数(如果用户没登录，就不能上传文件)
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        //2.校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if(spaceId != null){
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");

            // 校验是否有空间权限，自己上传的图片只能上传到自己的空间以及公共图库
            if(!loginUser.getId().equals(space.getUserId()))
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有空间权限");

            // 校验额度，上传图片到自己的私有空间中，第一步都是先校验额度是否已满
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }

        //2.判断是新增图片还是更新图片，从 PictureUploadRequest 中看是能否获取得到 pictureId , null 新增，有就是更新
        Long pictureId = null;
        if(pictureUploadRequest != null){
            pictureId = pictureUploadRequest.getId();
        }

        //3.如果是更新，我们又传入了 SpaceId ，所以下面还需要进行判断
        if(pictureId != null){
            //查找老的图片
            Picture oldPicture = getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR,"图片不存在");

            //添加判断的逻辑，因为现在用户和管理员都可以更新图片，如果该图片既不是自己的，同时他也不是管理员的话，就不能更新图片
            if(!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"仅本人或管理员可更新图片");
            }

            // 搞不好还有一种情况，它更新图片的时候传入的 spaceId 和它创建图片时传入的 spaceId 不同，这样就会出现一种漏洞
            // 我们老图片传入了空间A，但是现在更新传入空间的时候，指定spaceId是B，这不就有问题了吗，为了严谨
            // 还要校验当前传的这个空间id是否和原本图片的空间id一致，如果这一次它没有传spaceId，那么我们就直接复用原本的spaceId，更新原来那个空间的图片
            // 这样也兼顾了公共图库，如果空间id为null，就是传入公共图库

            // 没传 spaceId ，则复用原有图片的 spaceId ,（这样也复用了公共图库）
            if(spaceId == null){
                if(oldPicture.getSpaceId() != null){
                    spaceId = oldPicture.getSpaceId();
                }
            }else{
                // 传入了 spaceId ，必须和原来图的 spaceId 一致
                if(!oldPicture.getSpaceId().equals(spaceId)){
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间id不一致");
                }
            }
        }

        // 3.上传图片，得到图片信息（如果图片存在，无论是更新还是第一次上传，都是要上传图片）
        // 前缀，我们可以按照userId来划分目录，由于我们这个项目是公共图库，所以每一个用户就有自己的一个目录
        // 之后我们去开发私有空间，什么企业空间，专属空间，所以我们现在给前缀进行划分
        // 把所有公开的图片上传到 public 目录下，之后如果有私有就上传到 private 目录下，按照 userId 划分目录
        // 现在按照空间划分目录
        String uploadPathPrefix;
        if(spaceId == null){
            // 公共图库
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        }else {
            // 空间
            uploadPathPrefix = String.format("space/%s", spaceId);
        }


        //上传，并得到我们解析之后的信息
        //根据 inputSource 类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if(inputSource instanceof String){
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        //构造存到数据库中的信息
        Picture picture = BeanUtil.copyProperties(uploadPictureResult, Picture.class);

        //填充未能拷贝的信息
        //之前我们导؜入系统的图片名称都是由对方的 URL 决‌定的，名称可能乱七八糟，而且不利于我们得‍知数据是在那一批被导入的。
        //因此我们可以让管理员在执行任务前指定 名称前缀，即导入到系统中的图片名称。比如前缀为 “鱼皮”，得到的图片名称就是 “鱼皮1”、“鱼皮2”。。。
        //相当于支持؜抓取和创建图片时批量对某批图片命名，‌名称前缀默认等于搜索关键词。
        //可以通过 pictureUploadRequest 对象获取到要手动设置的图片名称，‍而不是完全依赖于解析的结果
        String picName = uploadPictureResult.getPicName(); //默认就是解析的图片名称
        //如果用户指定图片名称，就用用户指定的图片名称
        if(pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())){
            picName = pictureUploadRequest.getPicName();
        }

        picture.setName(picName);
        picture.setUserId(loginUser.getId());
        picture.setSpaceId(spaceId); //空间id

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

        //将文件上传到了对象存储了之后，我们才可以知道文件的大小，先上传到了对象存储，才校验大小
        //如果做精确的校验就是要先上传获取到文件的大小，再上传到对象存储中，这么做是可以但是得要改代码
        //单张图片最大才 2M，那么即使空间满了再允许上传一张图片，影响也不大
        //即使有用户在超额前的瞬间大量上传图片，对系统的影响也并不大。后续可以通过限流 + 定时任务检测空间等策略，尽早发现这些特殊情况再进行定制处理。

        // 现在操作两张表，所以我们开启事务，数据一致性
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            //插入图片都没有成功就不用更新额度了
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");

            //数据库中添加图片成功之后，还要操作空间表，更新剩余的额度
            if (finalSpaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });

        //todo 如果是替换了图片，我们需要把 COS 中的图片也删除掉，同时还需要更新空间的额度，也就是如果是更新操作的话，在上面更新额度的时候，得要加上原本图片大小，但是没什么必要

        //返回VO对象
        return PictureVO.objToVo(picture);
    }


    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     * 添加了很多日志记录和异常处理逻辑，使得单‌张图片抓取或导入失败时任务还能够继续执行，最‍终返回创建成功的图片数
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText();
        // 格式化数量
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "抓取数量，最多 30 条");
        // 要抓取的地址  ，将关键词，拼接到搜索地址中
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        //解析页面
        //这个 Document 就把它当作一个最全的 HTML ，我们要根据 class 类名，根据一些元素的 id 来获取对应的内容
        Element div = document.getElementsByClass("dgControl").first();
        //只有一个，其实就是HTML元素，就是最外层的那个 div

        if (ObjUtil.isNull(div)) {
            //如果最外层的 div 都没有，我们也就获取不到里面的元素了
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        int uploadCount = 0;
        //遍历图片元素，依次上传
        for (Element imgElement : imgElementList) {

            String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
            if (StrUtil.isBlank(namePrefix)) {
                //如果管理员没有指定图片名称前缀，则使用搜索关键词作为图片名称前缀
                namePrefix = searchText;
            }

            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过: {}", fileUrl);
                continue;
            }
            // 处理图片上传地址，防止出现转义问题
            int questionMarkIndex = fileUrl.indexOf("?");
            //将 ? 后面的参数截取掉，这些参数会导致 URL 出现转义问题
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            if (StrUtil.isNotBlank(namePrefix)) {
                // 设置图片名称，序号连续递增
                pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            }

            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功, id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }


    /**
     * 删除图片
     *
     * @param pictureId
     * @param loginUser
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);

        //删除图片释放额度，操作两张表，也要开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 释放额度
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });

        // 异步清理文件
        this.clearPictureFile(oldPicture);
    }


    /**
     * 清理图片文件
     * todo 待使用，了解Async注解
     */
    @Async
    @Override
    public void clearPictureFile(Picture picture) {
        // 判断该图片的 url 在其它记录中是否有被使用，避免多条记录引用同一张图片相同的 url ，图片秒传场景就会出现这种情况
        String pictureUrl = picture.getUrl();
        Long count = lambdaQuery().eq(Picture::getUrl, pictureUrl).count();
        if(count > 1L){
            //有不止一条记录用到了该图片，不清理
            return;
        }
        cosManager.deleteObject(FileUtil.getName(pictureUrl));
        //清理缩略图
        String thumbnailUrl = picture.getThumbnailUrl();
        if(StrUtil.isNotBlank(thumbnailUrl)){
            cosManager.deleteObject(FileUtil.getName(thumbnailUrl));
        }
    }


    /**
     * 编辑图片
     * 这里也需要进行修改，公共图库中的图片，本人以及管理员可以进行编辑
     * 私有空间中的图片，只有本人可以进行编辑
     * @param pictureEditRequest
     * @param loginUser
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 校验权限
        checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }


    /**
     * 校验空间权限的图片
     * 校验空间图片的权限
     * 删除图片和修改空间图片的权限是一样的，因为我们都是仅本人仅空间创建人才能修改，我们还要区分是公共图库还是私有空间
     * 公共图库的话是系统管理员也能改
     * 校验当前登录用户能不能操作这张图片
     * @param loginUser
     * @param picture
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long userId = loginUser.getId();
        if(spaceId == null){
            //公共图库，仅本人或者管理员可操作
            if(!userService.isAdmin(loginUser) && !userId.equals(picture.getUserId())){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }else{
            //该图片属于私有空间，仅空间所属人可操作
            if(!userId.equals(picture.getUserId())){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"私有空间，仅空间所属人可操作");
            }
        }
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
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
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
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId"); //如果 nullSpaceId 为 true 的话，我们就要查询 spaceId 为 null 的字段了 TODO 这里需要理解
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




