package com.air.yunpicturebackend.controller;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.air.yunpicturebackend.annotation.AuthCheck;
import com.air.yunpicturebackend.api.aliyunai.AliYunAiApi;
import com.air.yunpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.air.yunpicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.air.yunpicturebackend.api.imageSearch.ImageSearchApiFacade;
import com.air.yunpicturebackend.api.imageSearch.model.ImageSearchResult;
import com.air.yunpicturebackend.common.BaseResponse;
import com.air.yunpicturebackend.common.DeleteRequest;
import com.air.yunpicturebackend.common.ResultUtils;
import com.air.yunpicturebackend.constant.UserConstant;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import com.air.yunpicturebackend.exception.ThrowUtils;
import com.air.yunpicturebackend.model.dto.picture.*;
import com.air.yunpicturebackend.model.entity.Picture;
import com.air.yunpicturebackend.model.entity.Space;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.enums.PictureReviewStatusEnum;
import com.air.yunpicturebackend.model.vo.PictureVO;
import com.air.yunpicturebackend.service.PictureService;
import com.air.yunpicturebackend.service.SpaceService;
import com.air.yunpicturebackend.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author WyH524
 * @since 2025/9/1 上午11:05
 */
@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SpaceService spaceService;

    @Resource
    private AliYunAiApi aliYunAiApi;


    /**
     * 本地缓存
     * 官方文档中是这样写的，根据官方文档进行改造的
     * LoadingCache<Key, Graph> graphs = Caffeine.newBuilder()
     * .maximumSize(10_000)
     * .expireAfterWrite(Duration.ofMinutes(5))
     * .refreshAfterWrite(Duration.ofMinutes(1))
     * .build(key -> createExpensiveGraph(key));
     * 可以将这个封装为单独的类，方便调用
     */
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder()
                    .initialCapacity(1024)  //设置内存的初始容量，一开始分配一些内存，提高缓存的启动效率
                    .maximumSize(10000L)  //最大1w条数据
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();


    /**
     * 上传本地图片 （新图上传，重新上传）
     */
    @PostMapping("/upload")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)  不用进行权限控制了，用户也可以使用
    //权限控制，就是我们要来把这个审核状态，假如说有人更新或者编辑了这个图片，审核状态都给它变成 待审核 状态
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,  // 接收名为 "file" 的上传文件
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }


    /**
     * 通过图片 URL 上传图片 （新图上传，重新上传）
     */
    @PostMapping("/upload/url")
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(pictureUploadRequest.getFileUrl(),
                pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }


    /**
     * 批量抓取，并创建图片
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }


    /**
     * 删除图片，传过来图片id，仅本人或管理员可删除图片，进行逻辑删除
     * 现在有了公共图库和私有空间了，私有空间的图片管理员不可以删除，所以这段逻辑得要添加一点
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.deletePicture(deleteRequest.getId(), loginUser);
        return ResultUtils.success(true);
    }


    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest
            , HttpServletRequest httpServletRequest) {
        // 1. 校验参数
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2.判断要修改的图片是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "该图片不存在，修改失败");

        // 3.将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));

        // 数据校验
        pictureService.validPicture(picture);

        //填充审核的信息
        User loginUser = userService.getLoginUser(httpServletRequest);
        pictureService.fillReviewParams(picture, loginUser);

        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "出现异常，修改图片失败");
        return ResultUtils.success(true);
    }


    /**
     * 根据 id 获取图片（仅管理员可用）
     * 管理员可以看到 Picture 的全部信息，未包含用户的信息
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "图片 id 不存在");
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "未查询到该图片");
        // 获取封装类
        return ResultUtils.success(picture);
    }


    /**
     * 根据 id 获取图片封装类（包含用户信息）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        // 1.校验参数
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "图片 id 不存在");
        User loginUser = userService.getLoginUser(request);
        // 2.查询数据库，只从审核通过的图片中进行查询
        Picture picture = pictureService.lambdaQuery().eq(Picture::getReviewStatus, PictureReviewStatusEnum.PASS.getValue())
                .eq(Picture::getId, id).one();
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "该图片不存在");
        // 3.判断是不是公共图库中的图片，如果是私有图片得要判断当前用户有没有权限操作该图片
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            // 私有图片需要进行权限校验
            pictureService.checkPictureAuth(loginUser, picture);
        }
        // 4.返回封装类
        return ResultUtils.success(pictureService.getPictureVO(picture, loginUser));
    }


    /**
     * 分页获取图片列表（仅管理员可用） 可以查看所有的图片
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        //返回的就是 Page<Picture> 对象
        return ResultUtils.success(picturePage);
    }


    /**
     * 主页展示的图片，就是请求这个接口的
     * 分页获取图片列表，给普通用户用的（封装类）
     * 未添加缓存
     * 假如说用户传过来的参数是查询某一个 spaceId 的图片，我们是不是得要校验它有没有权限
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫 一页显示的条数大于 20 的话，就报错
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 查询的是哪里的图片，私有空间还是公共图库，如果是公开图片 spaceId 为 null
        Long spaceId = pictureQueryRequest.getSpaceId();
        // 公开图库
        if (spaceId == null) {
            //普通用户默认只能查看审核通过的图片，我们自行设置查询条件为只查询审核状态为通过的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            //查 spaceId 值为 null 的数据
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }

        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));

        // 获取封装类，获取 Page<PictureVO> 对象
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 以图搜图
     * 经过测试发؜现，百度搜索对于 webp 格式图片的支‌持度并不好（改文件的后缀也没有用），估计‍是平台不支持该格式的算法
     * 但是使用 png 图片去测试，就能正常看到结果了
     * 解决 webp 格式图片无法搜索的问题
     * 如果想解决上述问题，有几种方案：
     *
     * 直接在前端拿到识图结果 URL 后，直接新页面打开，而不是把识图结果放到自己的网站页面中
     * 切换为其他识图接口，比如 Bing 以图搜图 API
     * 将本项目的图片以 PNG 格式进行压缩
     */
    // todo 需要完善一下为什么私人空间的有一些图片展示不出来
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        // 1.判断参数是否为空
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        // 2.通过 pictureId 获取图片 url
        Picture oldPicture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(oldPicture.getUrl());
        return ResultUtils.success(resultList);
    }

    /**
     * 颜色搜图
     */
    @PostMapping("/search/color")
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest ,
                                                              HttpServletRequest  request) {
        // 1. 校验参数
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);
        // 2. 调用方法
        return ResultUtils.success(pictureService.searchPictureByColor(spaceId, picColor, loginUser));
    }


    /**
     * 多级缓存
     * 主页展示的图片，就是请求这个接口的
     * 分页获取图片列表，给普通用户用的（封装类）
     * 优先从本地缓存中读取数据。如果命中，则直接返回。如果本地缓存未命中，则查询 Redis 分布式缓存。如果 Redis 命中，则返回数据并更新本地缓存。
     * 如果 Redis 也未命中，则查询数据库，并将结果写入 Redis 和本地缓存。
     *
     *
     * 这里这个分页缓存先不用了，或者加一些权限校验，然后分开缓存
     */
    @Deprecated
    @PostMapping("/list/page/vo/cache")  // todo 前端接口还没有引用
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                      HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能查看已过审的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 构建缓存 key
        // 通过 MD5 摘要算法 将较长的查询条件 JSON 字符串转换为固定长度的哈希值（32位十六进制字符串）
        // 可以显著缩短 Redis 的 Key 长度。这是一种常见的优化手段，
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        //Key的设置都是一样的，但是一般本地缓存不加 项目的前缀
        String cacheKey = "yupicture:listPictureVOByPage:" + hashKey;

        // 1.先从本地缓存中查询
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue != null) {
            // 如果缓存命中，直接返回结果
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 2.本地缓存未命中，查询分布式缓存
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        cachedValue = valueOps.get(cacheKey);
        //如果缓存命中，更新本地缓存，返回结果
        if (cachedValue != null) {
            LOCAL_CACHE.put(cacheKey, cachedValue);
            Page<PictureVO> cachepage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachepage);
        }

        // 3.如果都没有命中，直接查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);

        // 4.更新本地缓存和分布式缓存
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        LOCAL_CACHE.put(cacheKey, cacheValue);
        // 5 - 10 分钟随机过期，防止雪崩，防止同一个时间很多的缓存都失效了，所以过期时间设置不同
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        valueOps.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);

        // 返回结果
        return ResultUtils.success(pictureVOPage);
    }


    /**
     * 基于caffeine本地缓存
     * 主页展示的图片，就是请求这个接口的
     * 分页获取图片列表，给普通用户用的（封装类）
     * 有缓存的
     * 在查询数据库前先查询缓存‌，如果已有数据则直接返回缓存，如果没有数据则查询数据库，‍并且将结果设置到缓存中。
     */
//    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCaffeineCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                              HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能查看已过审的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 构建缓存 key
        // 通过 MD5 摘要算法 将较长的查询条件 JSON 字符串转换为固定长度的哈希值（32位十六进制字符串）
        // 可以显著缩短 Redis 的 Key 长度。这是一种常见的优化手段，
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        //Key的设置都是一样的，但是一般本地缓存不加 项目的前缀
        String cacheKey = "listPictureVOByPage:" + hashKey;

        // 从本地缓存中查询
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);

        if (cachedValue != null) {
            // 如果缓存命中，返回结果
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 如果没有命中缓存，查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);

        // 将数据写入本地缓存，不用设置过期时间，在构造缓存的时候已经指定好了
        // 而且不用担心雪崩，因为本地缓存本来就不打算高可用的时候才用的本地缓存
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        LOCAL_CACHE.put(cacheKey, cacheValue);

        // 返回结果
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 基于Redis分布式缓存
     * 主页展示的图片，就是请求这个接口的
     * 分页获取图片列表，给普通用户用的（封装类）
     * 有缓存的
     * 在查询数据库前先查询缓存‌，如果已有数据则直接返回缓存，如果没有数据则查询数据库，‍并且将结果设置到缓存中。
     */
//    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithRedisCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                           HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能查看已过审的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 构建缓存 key
        // 通过 MD5 摘要算法 将较长的查询条件 JSON 字符串转换为固定长度的哈希值（32位十六进制字符串）
        // 可以显著缩短 Redis 的 Key 长度。这是一种常见的优化手段，
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = "yupicture:listPictureVOByPage:" + hashKey;

        // 从 Redis 缓存中查询
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String cachedValue = valueOps.get(cacheKey);

        if (cachedValue != null) {
            // 如果缓存命中，返回结果
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 如果没有命中缓存，查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);

        // 存入 Redis 缓存
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 5 - 10 分钟随机过期，防止雪崩，防止同一个时间很多的缓存都失效了，所以过期时间设置不同
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);

        // 将数据写入 redis 缓存，并设置过期时间
        valueOps.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);

        // 返回结果
        return ResultUtils.success(pictureVOPage);
    }


    /**
     * 编辑图片（给用户使用）
     * 这里也需要进行修改，公共图库中的图片，本人以及管理员可以进行编辑
     * 私有空间中的图片，只有本人可以进行编辑
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        // 1.参数校验
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.editPicture(pictureEditRequest, loginUser);
        return ResultUtils.success(true);
    }


    /**
     * 获取标签和分类
     * 要支持用户根据标签和分类搜索图片，我‌们可以给用户列举一些常用的标签和分类，‍便于筛选。
     * 在项目前期规模؜不大的时候，我们没必要将标签和分类单独用数据表来维护了，‌直接在 PictureController 中写一个接口‍
     * 返回预设的固定数据即可
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }


    /**
     * 图片审核（管理员使用）
     * 我们还要对用户检索图片的范围进行限制，只能让它看到审核通过的数据
     * 管理员上传或者修改图片，自动过审；用户编辑图片之后，图片状态得修改为 待审核
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        // 1.校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }


    /**
     * 批量编辑图片
     */
    @PostMapping("/edit/batch")
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        //1.校验参数
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }


    /**
     * 创建 AI 扩图任务
     * ai扩图图片的大小也是有限制的
     * 官方文档中：
     * 图像大小：不超过10MB。
     * 图像分辨率：不低于512×512像素且不超过4096×4096像素。
     * todo 异步任务优化
     */
    @PostMapping("/out_painting/create_task")
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(
            @RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
            HttpServletRequest request) {
        // 1.校验参数
        if (createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse response = pictureService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }

    /**
     * 查询 AI 扩图任务
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        GetOutPaintingTaskResponse task = aliYunAiApi.getOutPaintingTask(taskId);
        return ResultUtils.success(task);
    }
}
