package com.air.yunpicturebackend.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import com.air.yunpicturebackend.exception.ThrowUtils;
import com.air.yunpicturebackend.mapper.SpaceMapper;
import com.air.yunpicturebackend.model.dto.space.analyze.*;
import com.air.yunpicturebackend.model.entity.Picture;
import com.air.yunpicturebackend.model.entity.Space;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.vo.space.anayze.*;
import com.air.yunpicturebackend.service.PictureService;
import com.air.yunpicturebackend.service.SpaceAnalyzeService;
import com.air.yunpicturebackend.service.SpaceService;
import com.air.yunpicturebackend.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
* @author 30280
* @createDate 2025-09-05 15:26:40
*/
@Service
public class SpaceAnalyzeServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceAnalyzeService {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private PictureService pictureService;


    /**
     * 获取空间使用情况数据
     *
     * @param spaceUsageAnalyzeRequest SpaceUsageAnalyzeRequest 请求参数
     * @param loginUser                当前登录用户
     * @return SpaceUsageAnalyzeResponse 分析结果
     * 如果是分析全؜空间或公共图库的使用情况，需要编写 “仅管理员可访问” 的权限校验逻‌辑，并且更改查询图片表的范围；
     * 如果只是分析单个空间的使用情况，直接从‍空间表查询出单个空间的数据即可
     */
    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        // 1.根据查询的范围，校验权限
        // 我们需要区分一下，是查询所有的空间还是特定的空间，我们的空间表有一个现成的字段，每一个空间都有该空间的使用量以及图片的总数
        // 但是如果需要查询全部的空间，或者说要查询公共图库的占用，单独从空间表查是不是比较麻烦
        // 如果要查公共图片占用的体积怎么查，是不是只能查图片表，把所有 spaceId 为 null 的图片的 pictureSize 求和
        // 所以我们会发现在我们这个需求中，查询全部空间和查询特定的空间，它的查询方式是有很大区别的，所以在最开始我们就逻辑区分一下
        // 特定空间的使用情况可以直接从 space 表中查询；全空间或者公共图库的使用情况，需要从 Picture 表统计查询

        // 1.1.如果查询的是全部空间或者公共图库，仅管理员有权限
        if (spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()) {
            // 1.2.判断是否是管理员
            boolean isAdmin = userService.isAdmin(loginUser);
            ThrowUtils.throwIf(!isAdmin, ErrorCode.NO_AUTH_ERROR, "无权访问空间使用情况");

            // 1.3.判断需要查询的是全部空间还是公共图库，拼接查询条件
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("picSize");
            // 全部空间：统计所有图片大小
            // 公共图库：统计空间 id 为 null 的图片总和，拼接这个条件
            // 将这里的判断拼接条件封装到一个方法中
            fillAnalyzeQueryWrapper(spaceUsageAnalyzeRequest, queryWrapper);

            // 现在假如说，用 list 方法查询所有的图片得到 picSize 会有什么问题吗？
            // 假如说图片表有 10w 条数据，我们只需要 picSize 这一列，用 list 方法返回的是一个 Picture 对象
            // 你觉得是一个对象大，还是我们只需要的一个 Long 大，肯定是对象占用更多的空间，所以有优化方案
            // 为了优化内存空间，尤其是分析类需求，经常需要查大量的数据的，通过这种方式节约对象的尺寸
            // 当然还有一种优化的方法，就是可以直接在数据库层面去求和，不用在 java 代码层面去做这个求和
            // 数据库层面用分组统计也是可以的

            // 优化点：
            // 由于我们只需要获取图片存储大小，从数据库中查询时要指定只查询需要的列
            // 并且使用 mapper 的 selectObjs 方法直接返回 Object 对象，而不用封装为 Picture 对象，可以提高性能并节约存储空间
            // 得到每张图片的 picSize 存到一个 List 中
            List<Object> pictureObjList = pictureService.getBaseMapper().selectObjs(queryWrapper);
            // Object 就是 Long 类型，计算所有元素中Long类型值的总和
            long usedSize = pictureObjList.stream().mapToLong(result -> result instanceof Long ? (Long) result : 0).sum();
            // 有多少个 Long 就有多少张图片
            long usedCount = pictureObjList.size();

            // 封装返回结果，公共图库和全部空间，只需要返回使用量，限额以及比例是没有的
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(usedSize);
            spaceUsageAnalyzeResponse.setUsedCount(usedCount);
            // 公共图库无上限、无比例
            spaceUsageAnalyzeResponse.setMaxSize(null);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setMaxCount(null);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);
            return spaceUsageAnalyzeResponse;
        } else {
            // 2.1.查询指定空间
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            // 2.2.获取空间信息
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");

            // 2.3.权限校验：操作该空间，仅空间所有者或管理员可访问
            spaceService.checkSpaceAuth(loginUser, space);

            // 2.4.构造返回结果
            SpaceUsageAnalyzeResponse response = new SpaceUsageAnalyzeResponse();
            response.setUsedSize(space.getTotalSize());
            response.setMaxSize(space.getMaxSize());
            // 2.5.后端直接算好百分比，这样前端可以直接展示
            double sizeUsageRatio = NumberUtil.round(space.getTotalSize() * 100.0 / space.getMaxSize(), 2).doubleValue();
            response.setSizeUsageRatio(sizeUsageRatio);
            response.setUsedCount(space.getTotalCount());
            response.setMaxCount(space.getMaxCount());
            double countUsageRatio = NumberUtil.round(space.getTotalCount() * 100.0 / space.getMaxCount(), 2).doubleValue();
            response.setCountUsageRatio(countUsageRatio);
            return response;
        }
    }


    /**
     * 获取空间图片分类分析
     * 这个涉及到分组，要按照分类进行分组，统计每个分类下图片的总的大小，数量
     */
    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        // 1.检查权限，提成了一个方法，上面的空间使用情况是直接进行判断的
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);

        // 2.1.构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        // 2.2.根据分析范围补充查询条件，判断是查询哪个范围中的分类情况
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);
        // 2.3.使用 MyBatis-Plus 分组查询
        queryWrapper.select("category AS category",
                        "COUNT(*) AS count",
                        "SUM(picSize) AS totalSize")
                .groupBy("category");

        // 只需要注意这个地方，尽量少查字段，少占用内存，还有就是自己先把 sql 写出来，然后把它转换成我们的 QueryWrapper
        // 3.查询并转换结果，我们要查询的就这三个，使用 selectMaps，每一行数据是一个 Map ，把这个 Map 转换成我们需要的返回值
        return pictureService.getBaseMapper().selectMaps(queryWrapper)
                .stream()
                .map(result -> {
                    String category = result.get("category") != null ? result.get("category").toString() : "未分类";
                    Long count = ((Number) result.get("count")).longValue();
                    Long totalSize = ((Number) result.get("totalSize")).longValue();
                    return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
                })
                .collect(Collectors.toList());
    }


    /**
     * 从数据库获取标‌签数据，统计每个标签的图片数量，并按‍使用次数降序排序
     * 统计每个标签占用了多少图片
     * 我们现在的 Picture 表是不知道有哪些标签的，没有办法像分类一样，直接通过一列就查出来
     * 我们的标签是一个字符串数组，我们需要先取出来一定范围的图片，再把这些图片中有的标签提出来，先定一下有哪些标签，再统计标签的使用次数
     * 所以是有两个步骤的
     */
    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        // 1.检查当前用户有没有查询的权限
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);

        // 2.构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);

        // 3.查询所有符合条件的标签，只需要查 tags 这一个字段就够了，所以这里也是使用的是 selectObjs
        queryWrapper.select("tags");
        // 3.1.得到一个标签字符串列表，这是一个 json
        List<String> tagsJsonList = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .filter(ObjUtil::isNotNull)  //过滤掉没有标签的图片
                .map(Object::toString)
                .collect(Collectors.toList());

        // 4.把这个字符串进行拆分，合并统计一下每个标签出现的次数
        Map<String, Long> tagCountMap = tagsJsonList.stream()
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream())
                // 把数组拆开，把多个数组的值全取处理啊，变成一个数组，这就叫做扁平化
                // ["java","python"], ["java","c++"] ==> "java","java","python","c++"
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));
                //现在就可以统计了，自动根据 tag 标签进行分组求和，拿到的就是一个标签的 Map

        // 转换为响应对象，按使用次数降序排序
        return tagCountMap.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())) // 降序排列
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }


    /**
     * 空间图片大小区间，分段统计
     */
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        // 1.检查该用户是否有操作该空间的权限
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);

        // 2.按照查询范围构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);

        // 3.查询对应范围的所有的图片大小
        queryWrapper.select("picSize");
        List<Long> picSizes = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .map(size -> ((Number) size).longValue())
                .collect(Collectors.toList());

        // 4.把这个大小集合中每种图片的大小归到一个分类中，定义分段范围，注意使用有序 Map 从小到大或者从大到小
        Map<String, Long> sizeRanges = new LinkedHashMap<>();
        sizeRanges.put("<100KB", picSizes.stream().filter(size -> size < 100 * 1024).count());
        sizeRanges.put("100KB-500KB", picSizes.stream().filter(size -> size >= 100 * 1024 && size < 500 * 1024).count());
        sizeRanges.put("500KB-1MB", picSizes.stream().filter(size -> size >= 500 * 1024 && size < 1 * 1024 * 1024).count());
        sizeRanges.put(">1MB", picSizes.stream().filter(size -> size >= 1 * 1024 * 1024).count());

        // 转换为响应对象
        return sizeRanges.entrySet().stream()
                .map(entry -> new SpaceSizeAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }


    /**
     * 获取空间用户上传行为分析
     */
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        // 1.是否有该空间范围的操作权限
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);

        // 2.构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        // 2.1.判断是否是进行某个用户的行为分析，是的话就拼接查询条件
        Long userId = spaceUserAnalyzeRequest.getUserId();
        queryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
        // 2.2.根据所查询空间范围构造查询条件
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);
        // 2.3.分析前端所传的时间维度构造查询条件：每日、每周、每月
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        switch (timeDimension) {
            case "day":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m-%d') AS period", "COUNT(*) AS count");
                break;
            case "week":
                queryWrapper.select("YEARWEEK(createTime) AS period", "COUNT(*) AS count");
                break;
            case "month":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m') AS period", "COUNT(*) AS count");
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间维度");
        }

        // 3.按分组的字段排序
        queryWrapper.groupBy("period").orderByAsc("period");

        // 4.查询结果并转换
        List<Map<String, Object>> queryResult = pictureService.getBaseMapper().selectMaps(queryWrapper);
        return queryResult.stream()
                .map(result -> {
                    String period = result.get("period").toString();
                    Long count = ((Number) result.get("count")).longValue();
                    return new SpaceUserAnalyzeResponse(period, count);
                })
                .collect(Collectors.toList());
    }


    /**
     * 按存储使用量排‌序查询前 N 个空间，返回对应的空间信息
     * 只有管理‍员可以查看空间排行
     */
    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        // 1.仅管理员可查看空间排行
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权查看空间排行");

        // 2.构造查询条件
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "spaceName", "userId", "totalSize")
                .orderByDesc("totalSize")  //按照使用大小降序排序
                .last("LIMIT " + spaceRankAnalyzeRequest.getTopN()); // 取前 N 名

        // 3.查询结果
        return spaceService.list(queryWrapper);
    }


    /**
     * 校验用户是否有对应空间分析的权限
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        // 1.根据请求参数，来进行一些判断，检查权限
        if (spaceAnalyzeRequest.isQueryAll() || spaceAnalyzeRequest.isQueryPublic()) {
            // 全空间分析或者公共图库分析，仅管理员有权限进行分析
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权访问公共图库");
        } else {
            // 2.分析私有空间，仅本人或者管理员有权限访问
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 3.如果空间存在了，是不是要校验这个用户有没有这个空间的权限
            spaceService.checkSpaceAuth(loginUser, space);
        }
    }


    /**
     * 根据请求参数的值，自动填充 Mybatis-Plus 的查询条件，填充 QueryWrapper
     */
    private static void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {
        // 全空间分析
        if (spaceAnalyzeRequest.isQueryAll()) {
            return;
        }
        // 公共图库分析
        if (spaceAnalyzeRequest.isQueryPublic()) {
            queryWrapper.isNull("spaceId");
            return;
        }
        // 私有空间分析
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");
    }
}




