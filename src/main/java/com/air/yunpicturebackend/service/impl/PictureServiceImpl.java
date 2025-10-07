package com.air.yunpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.air.yunpicturebackend.api.aliyunai.AliYunAiApi;
import com.air.yunpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.air.yunpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
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
import com.air.yunpicturebackend.utils.ColorSimilarUtils;
import com.air.yunpicturebackend.utils.ColorTransformUtils;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
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

    @Resource
    private AliYunAiApi aliYunAiApi;


    /**
     * 上传图片（新上传的图片，修改图片，两个接口）
     * @param inputSource 文件输入源
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     * 上传图片，现在有了空间概念，如果有指定 spaceId 的话，就得校验空间权限
     * 如果更新图片的话，就得判断原图片是否是自己的，不能修改别人的图片，管理员才有权限
     */
    @Override
    public PictureVO uploadPicture(Object inputSource , PictureUploadRequest pictureUploadRequest, User loginUser) {
        //1.校验参数(如果用户没登录，就不能上传文件)
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        //2.如果 spaceId 不为 null ，那么就是将图片上传到私有空间或者团队空间，所以需要进行权限的校验，空间大小的校验
        Long spaceId = null;
        if(pictureUploadRequest != null){
            spaceId = pictureUploadRequest.getSpaceId();
        }
        if(spaceId != null){
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");

            // 统一使用了 sa-token 进行权限校验，没有权限是进不到这个方法里面的
            // 校验是否有该用户是否有权限，只能上传到自己的空间以及公共图库
            // 改为统一的权限校验逻辑，以前是只有私有空间，只有空间的创建人才能去上传，但是现在我们不是只有私有空间了
            // 有团队空间了，即使不是空间的创建人，但是如果它是空间的编辑者，它有权限，我们现在
            // if(!loginUser.getId().equals(space.getUserId()))
            // throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有空间权限");

            // 校验额度，上传图片到自己的私有空间中，第一步都是先校验额度是否已满
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }

        // 3.判断是新增图片还是更新图片，从 PictureUploadRequest 中看是能否获取得到 pictureId , null 就新增，否则就更新
        Long pictureId = null;
        if(pictureUploadRequest != null){
            pictureId = pictureUploadRequest.getId();
        }

        // 4.如果是更新图片，需要进行逻辑判断，现在又添加了 SpaceId ，下面需要添加一点逻辑
        if(pictureId != null){
            // 4.1.查找旧图片记录
            Picture oldPicture = getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR,"图片不存在");

            // 改为统一的权限校验逻辑，若没有权限进入不到这个方法中来
            // 添加判断的逻辑，现在用户和管理员都可以更新图片，如果该图片既不是自己的，同时他又不是管理员的，就不能更新图片
            // if(!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)){
            //     throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"仅本人或管理员可更新图片");
            // }

            // 搞不好还有一种情况，它更新图片的时候传入的 spaceId 和它创建图片时传入的 spaceId 不同，这样就会出现漏洞了
            // 我们老图片传入了空间 A ，但是现在更新传入空间的时候，指定 spaceId 是 B ，这不就有问题了吗，为了严谨
            // 也就是旧记录中的 spaceId 要与传过来的 spaceId 一致
            // 还要校验当前传的这个空间 id 是否和原本图片的 spaceId 一致，如果这一次它没有传 spaceId ，那么我们就直接复用原本的 spaceId ，更新原来那个空间的图片
            // 这样也兼顾了公共图库，如果空间id为 null ，就是传入公共图库

            // 没传 spaceId ，则复用原有图片的 spaceId （这样也复用了公共图库）
            if(spaceId == null){
                spaceId = oldPicture.getSpaceId();
            }else{
                // 传入了 spaceId ，必须和旧图记录的 spaceId 一致
                if(!oldPicture.getSpaceId().equals(spaceId)){
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,"上传的空间与原记录的空间不一致");
                }
            }
        }

        // 5.上传图片，得到图片信息（无论是更新还是创建图片，都需要上传）
        // 前缀，我们可以按照 userId 来划分目录，由于我们这个项目是公共图库，所以每一个用户就有自己的一个目录
        // 之后我们去开发私有空间，什么企业空间，专属空间，所以我们现在给前缀进行划分
        // 把所有公开的图片上传到 public 目录下，之后如果有私有就上传到 private 目录下，按照 userId 划分目录
        // 现在按照空间划分目录
        // 这里通过 spaceId 判断图片上传到哪个空间，然后设置上传路径
        String uploadPathPrefix;
        if(spaceId == null){
            // 公共图库
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        }else {
            // 私有空间或者团队空间
            uploadPathPrefix = String.format("space/%s", spaceId);
        }


        // 上传，并得到我们解析之后的信息
        // 根据 inputSource 类型区分上传方式，本地图片上传还是 URL 上传
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if(inputSource instanceof String){
            pictureUploadTemplate = urlPictureUpload;
        }
        // 上传图片成功之后，得到返回结果
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        //构造存到数据库中的信息
        Picture picture = BeanUtil.copyProperties(uploadPictureResult, Picture.class);

        // 填充未能拷贝的信息
        // 之前我们批量导؜入系统的图片名称都是由对方的 URL 决‌定的，名称可能乱七八糟，而且不利于我们得‍知数据是在那一批被导入的
        // 因此我们可以让管理员在执行任务前指定 名称前缀，即导入到系统中的图片名称。比如前缀为 “鱼皮”，得到的图片名称就是 “鱼皮1”、“鱼皮2”。。。
        // 相当于支持؜抓取和创建图片时批量对某批图片命名，‌名称前缀默认等于搜索关键词。
        // 可以通过 pictureUploadRequest 对象获取到要手动设置的图片名称，‍而不是完全依赖于解析的结果

        String picName = uploadPictureResult.getPicName(); //默认就是解析的图片名称，不包含扩展名
        //如果用户指定图片名称，就用用户指定的图片名称
        if(pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())){
            picName = pictureUploadRequest.getPicName();
        }

        // 数据万象返回的主色调的RGB值，有时候会少一位，存在 bug ，这里添加一步转换     位标准颜色
        picture.setPicColor(ColorTransformUtils.getStandardColor(uploadPictureResult.getPicColor()));

        picture.setName(picName);
        picture.setUserId(loginUser.getId());
        picture.setSpaceId(spaceId); //空间id

        // TODO 这里需要改一下吧，例如如果是上传到公共图库需要审核，但是上传到私人空间以及私有空间可以直接审核通过
        // 上传图片成功之后，填充审核的信息
        // 如果是管理员上传或者更新图片，则审核自动通过 填充全部的 review 信息
        // 如果是用户上传或者更新图片，则审核未通过，只填充 reviewStatus
        fillReviewParams(picture, loginUser);

        //4.操作数据库
        // 如果 pictureId 不为空，说明是更新图片，需要补充一些信息
        if(pictureId != null){
            //需要补充更新时间和 pictureId
            picture.setEditTime(new Date());
            picture.setId(pictureId);
        }

        // 将文件上传到了对象存储了之后，我们才可以知道文件的大小，先上传到了对象存储，才校验大小
        // 如果做精确的校验就是要先上传获取到文件的大小，再上传到对象存储中，这么做是可以但是得要改代码
        // 单张图片最大才 2M，那么即使空间满了再允许上传一张图片，影响也不大
        // 即使有用户在超额前的瞬间大量上传图片，对系统的影响也并不大。后续可以通过限流 + 定时任务检测空间等策略，尽早发现这些特殊情况再进行定制处理。

        // 现在操作两张表，所以我们开启编程式事务，确保数据一致性
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            // 插入图片都没有成功就不用更新额度了
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");

            // 上传图片成功之后，判断上传的公共图库还是私有图库，公共图库的话就不用更新剩余额度，就不用操作空间表
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

        // todo 如果是替换了图片，我们需要把 COS 中的图片也删除掉，同时还需要更新空间的额度，也就是如果是更新操作的话，在上面更新额度的时候，得要加上原本图片大小，但是没什么必要

        // 返回VO对象
        return PictureVO.objToVo(picture);
    }


    /**
     * AI 扩图
     * 在图片服务中编写创؜建扩图任务方法，从数据库中获取图片信息和 url 地址，构造请求参数‌后调用 api 创建扩图任务。
     * 注意，如果图片有空间 id，则需要校验‍权限，直接复用以前的权限校验方法。
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 1.获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();

        // 2.判断该图片是否存在
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR));

        // 3.判断该图片用户是否具有操作权限，如果是公共图片，用户身份必须是本人或者管理员；如果是私有图片，该图片必须是自己空间中的图片才有权限操作该图片
        // 校验权限，已经改为使用注解鉴权
        //checkPictureAuth(loginUser, picture);

        // 4.构造请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        taskRequest.setInput(input);

        // 4.1.将请求类中的 CreateOutPaintingTaskRequest.Parameters 直接复制到这个请求任务对象中
        BeanUtil.copyProperties(createPictureOutPaintingTaskRequest, taskRequest);

        // 5.调用接口，创建任务
        return aliYunAiApi.createOutPaintingTask(taskRequest);
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
        // 1.搜索词不能为空
        String searchText = pictureUploadByBatchRequest.getSearchText();
        ThrowUtils.throwIf(StrUtil.isBlank(searchText), ErrorCode.PARAMS_ERROR, "搜索词不能为空");

        // 2.抓取数量
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "抓取数量，最多 30 条");

        // 3.要抓取的地址，将搜索词，拼接到搜索地址中
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);

        // 4.使用 jsoup 抓取页面
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }

        // 5.解析页面
        // 这个 Document 对象就把它当作是这个页面中最全的 HTML 文档，我们要根据 class 类名，根据一些 div 的 id 来获取对应的内容
        Element div = document.getElementsByClass("dgControl").first();
        // 只有一个，其实就是HTML元素，就是最外层的那个 div
        if (ObjUtil.isNull(div)) {
            //如果最外层的 div 都没有，我们也就获取不到里面的元素了，直接报错
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        // 6.获取元素 从 div 元素（一个父容器）中，选择所有同时满足以下条件的 <img> 标签：标签名是 img 。拥有CSS类名为 mimg（通过 .mimg 指定）。
        Elements imgElementList = div.select("img.mimg");

        // 定义常量，用于记录抓取的图片数
        int uploadCount = 0;

        // 管理员设置的图片名称前缀
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            // 如果管理员没有指定图片名称前缀，则使用搜索关键词作为图片名称前缀
            namePrefix = searchText;
        }

        //遍历图片元素，依次上传
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过: {}", fileUrl);
                continue;
            }

            // 处理图片上传地址，防止出现转义问题，将 ? 后面的参数截取掉，这些参数会导致 URL 出现转义问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }

            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            if (StrUtil.isNotBlank(namePrefix)) {
                // 设置图片名称，拼接当前图片序号
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
     * 批量编辑图片
     * todo 用户同时编辑图片不可能说每次都编辑得特别多，用户还需要进行选择，正常来说他也不会选择成千上万的图片来进行编辑，所以这里就没有必要进行多线程，分批
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1.获取，校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        String nameRule = pictureEditByBatchRequest.getNameRule();
        //        这里 spaceId 其实也可以为空，大家如果想让管理员也可以批量编辑，那这里就不限制 spaceId 因为 spaceId 主要就是为了权限校验
        ThrowUtils.throwIf(spaceId == null || CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 2. 校验空间权限，只要涉及到图片操作都要进行权限校验
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        // 只能是空间的创建人才有权限操作这些图片，管理员在这个接口这里是没有权限进行批量编辑图片的
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }

        // 3. 查询指定图片，仅选择需要的字段（优化点）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)  //查出来的图片只需要返回主键 id 和 空间 id 就行，因为需要这两个字段进行校验
                .eq(Picture::getSpaceId, spaceId)   //这个空间 id 下的图片
                .in(Picture::getId, pictureIdList)  //根据 id 列表查询对应图片
                .list();
        if (pictureList.isEmpty()) {
            // 如果没有查询到图片，就直接退出
            return;
        }

        // 4. 将所查出的图片，更新其分类和标签以及重命名
        pictureList.forEach(picture -> {
            // 如果不为空才修改，用户不是每一次都修改的分类和标签的
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        //批量重命名
        fillPictureWithNameRule(pictureList, nameRule);

        // 5. 操作数据库进行批量更新，使用updateBatchById方法效率更高一点
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"批量编辑图片失败");
    }


    /**
     * 根据命名规则，进行批量重命名
     * nameRule 格式：图片{序号}
     * 现在就实现一个简单的功能，就只允许用户对图片加序号
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if(CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)){
            return ;
        }
        //定义一个计数器
        long count = 1 ;

        //因为涉及到正则表达式的匹配，所以为了稳定，我们try catch
        try {
            //遍历图片
            for(Picture picture : pictureList){
                //每个图片中，我们要给图片名称做正则表达式的替换，匹配所有的{序号}然后替换成我们的 count ，nameRule是这样的 图片_{序号}
                picture.setName(nameRule.replaceAll("\\{序号}", String.valueOf(count++)));
            }
        } catch (Exception e) {
            log.error("图片名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

//    //对于我们的؜项目来说，由于用户要处理的数据量不大，上述代码已经能够满足‌需求。但如果要处理大量数据，可以使用线程池 + 分批 + 并发‍进行优化，参考代码如下：
//    @Resource
//    private ThreadPoolExecutor customExecutor;
//
//    /**
//     * 批量编辑图片分类和标签
//     */
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public void batchEditPictureMetadata(PictureBatchEditRequest request, Long spaceId, Long loginUserId) {
//        // 参数校验
//        validateBatchEditRequest(request, spaceId, loginUserId);
//
//        // 查询空间下的图片
//        List<Picture> pictureList = this.lambdaQuery()
//                .eq(Picture::getSpaceId, spaceId)
//                .in(Picture::getId, request.getPictureIds())
//                .list();
//
//        if (pictureList.isEmpty()) {
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "指定的图片不存在或不属于该空间");
//        }
//
//        // 分批处理避免长事务
//        int batchSize = 100;
//        List<CompletableFuture<Void>> futures = new ArrayList<>();
//        for (int i = 0; i < pictureList.size(); i += batchSize) {
//            List<Picture> batch = pictureList.subList(i, Math.min(i + batchSize, pictureList.size()));
//
//            // 异步处理每批数据
//            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//                batch.forEach(picture -> {
//                    // 编辑分类和标签
//                    if (request.getCategory() != null) {
//                        picture.setCategory(request.getCategory());
//                    }
//                    if (request.getTags() != null) {
//                        picture.setTags(String.join(",", request.getTags()));
//                    }
//                });
//                boolean result = this.updateBatchById(batch);
//                if (!result) {
//                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量更新图片失败");
//                }
//            }, customExecutor);
//
//            futures.add(future);
//        }
//
//        // 等待所有任务完成
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//    }


    /**
     * 删除图片
     *
     * @param pictureId
     * @param loginUser
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        // 1.判断图片是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 已经改为使用统一的注解鉴权
        // 2.判断该图片，用户是否具有操作权限，如果是公共图片，用户身份必须是本人或者管理员；如果是私有图片，该图片必须是自己空间中的图片才有权限操作该图片
        // checkPictureAuth(loginUser, oldPicture);

        // 3.删除图片释放额度，操作两张表，所以要开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库，删除图片
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 释放空间额度
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

        // 4.异步 COS 中的文件
        this.clearPictureFile(oldPicture);
    }


    /**
     * 异步清理图片文件
     * todo 待使用，了解Async注解
     */
    @Async
    @Override
    public void clearPictureFile(Picture picture) {
        // 1.获取图片的 url
        String pictureUrl = picture.getUrl();

        // 2.清理 压缩图 和 缩略图 以及原图
        // 从 url 中解析出各图在 bucket 中的存储路径 ： /public/picture/2023/07/01/xxx.png
        cosManager.deleteObject(pictureUrl.substring(pictureUrl.indexOf(".com") + 4));
        String thumbnailUrl = picture.getThumbnailUrl();
        if(StrUtil.isNotBlank(thumbnailUrl)){
            cosManager.deleteObject(thumbnailUrl.substring(thumbnailUrl.indexOf(".com") + 4));
            // 构造出原图的路径
            cosManager.deleteObject(thumbnailUrl.substring(thumbnailUrl.indexOf(".com") + 4)
                    .replace("_thumbnail", ""));
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
        // 1.在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);

        // 2.注意将 list 转为 string
        if(CollUtil.isNotEmpty(pictureEditRequest.getTags())){
            picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        }

        // 3.设置编辑时间
        picture.setEditTime(new Date());

        // 4.数据校验，判断图片的基本信息是否符合格式
        this.validPicture(picture);

        // 5.判断该图片是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 6.校验当前用户是否有操作该图片的权限
        // 已经改为 sa-token 的统一鉴权
        // checkPictureAuth(loginUser, oldPicture);

        // 7.补充审核参数
        this.fillReviewParams(picture, loginUser);

        // 8.操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }


    /**
     * 手动校验权限
     * 判断该图片，用户是否具有操作权限，如果是公共图片，用户身份必须是本人或者管理员；如果是私有图片，该图片必须是自己空间中的图片才有权限操作该图片
     * 公共图库的话是系统管理员也能改
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
     * 以颜色搜图
     * 对哪个空间进行搜索，空间id，以颜色搜图是在私人空间进行搜索的；公共图库的话，效率较慢
     * 用户要搜索图片的颜色色值，十六进制
     * 判断当前操作的用户，校验空间权限
     * 返回值是搜索图片的列表
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1.参数校验
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"该空间不存在");

        // 使用注解鉴权了
        // 2.如果用户不是该空间的创建者，则没有权限
        // if (!loginUser.getId().equals(space.getUserId())) {
        //      throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        //  }

        // 3.查询该空间下所有的图片，图片信息中的主色调不能为空
        List<Picture> pictureList = lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();

        // 4.如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }

        // 5.将传过来的十六进制颜色码（如 "#FF5733"）转换成 java.awt.Color 对象
        Color targetColor = Color.decode(picColor);

        // 6.计算相似度并排序
        List<Picture> sortedPictures = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    // 提取图片主色调，肯定是有的，因为只能查出来的都是包含主色调的
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片放到最后
                    if (StrUtil.isBlank(hexColor)) {
                        // 返回最大值，直接排在集合的后面
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 采用 欧几里得距离算法 计算相似度
                    // 原本方法返回值，越大越相似，那越大的值，在sorted排序中是往后排的
                    // 但是我们希望越相似的值越往前排，所以这里取负值，越相似排在越前面
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                // 只取前 12 个
                .limit(12)
                .collect(Collectors.toList());

        // 7.转换为 PictureVO
        return sortedPictures.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }


    /**
     * 获取图片封装（PictureVO）包含了用户信息 UserVO
     * 编写获؜取图片封装的方法，可以为原有的图片关‌联创建用户的信息
     * @param picture
     * @param loginUser
     * @return
     */
    @Override
    public PictureVO getPictureVO(Picture picture, User loginUser) {
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
        // 1.获取所有记录
        List<Picture> pictureList = picturePage.getRecords();

        // 2.构造 Page<PictureVO> 对象，接下来的所有操作都是对其进行补充
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());

        // 3.如果查到的数据为空，也就不需要再进行封装了
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }

        // 4.对象列表 => 封装对象列表 ，将全部的 Picture 对象，转换为 PictureVO 对象
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());

        // 5. 每一张图片关联用户信息
        // 5.1.获取用户Id集合
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .collect(Collectors.toSet());

        // 根据 用户ID 列表查询用户数据，并将结果按 用户ID 分组转换到一个 Map
        // 这里其实就是一个优化算法，我们可以根据 用户ID 列表查找出所有的用户列表
        // 但是我们如果想要每一个 pictureVO 绑定一个 User 的话，还得要从List里面一个一个遍历
        // 5.2.我们可以将 List 转换成 Map ，Key 为 用户ID ，value 为用户对象，然后通过 Map 的 get 方法直接拿到对应对象，这样效率更高
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet)
                .stream()
                .collect(Collectors.groupingBy(User::getId));

        // 5.3. 填充信息，根据的是 PictureVO 中的 userID 和 User 中的 ID 进行匹配，再进行绑定
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });

        // 5.4.封装返回 Page 对象
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
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        // 根据枚举的 value 值获取对应的枚举
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);

        // 图片id不能为空，图片审核的状态也不能为空，不允许把 通过 或 拒绝 的审核状态再改为 待审核，
        // 那么当前用户要修改的状态也不能为待审核，也就是传过来的审核状态参数不能是 待审核
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
        // 这里新建一个 Picture 对象，不用上面那个是为了更高效的执行 update 操作，不是全部数据都修改一遍
        // 填充审核参数
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
     * 可以根据自己的需要，补充更多校验规则
     * 这里只对 图片 id，url，introduction进行校验
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 图片 id 不能为空
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
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
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
        // >= startTime
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime),"editTime",startEditTime);
        //< endTime
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime),"editTime",endEditTime);


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




