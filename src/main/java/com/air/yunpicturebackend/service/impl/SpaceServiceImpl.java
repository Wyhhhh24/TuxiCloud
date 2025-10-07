package com.air.yunpicturebackend.service.impl;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import com.air.yunpicturebackend.exception.ThrowUtils;
import com.air.yunpicturebackend.model.dto.space.SpaceAddRequest;
import com.air.yunpicturebackend.model.dto.space.SpaceQueryRequest;
import com.air.yunpicturebackend.model.entity.SpaceUser;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.enums.SpaceLevelEnum;
import com.air.yunpicturebackend.model.enums.SpaceRoleEnum;
import com.air.yunpicturebackend.model.enums.SpaceTypeEnum;
import com.air.yunpicturebackend.model.vo.SpaceVO;
import com.air.yunpicturebackend.model.vo.UserVO;
import com.air.yunpicturebackend.service.SpaceUserService;
import com.air.yunpicturebackend.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.air.yunpicturebackend.model.entity.Space;
import com.air.yunpicturebackend.service.SpaceService;
import com.air.yunpicturebackend.mapper.SpaceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author 30280
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-09-05 15:26:40
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService{


    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private SpaceUserService spaceUserService;

    // TODO 不用分表了
    // @Resource
    // @Lazy
    // private DynamicShardingManager dynamicShardingManager;

    /**
     * 创建空间
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        //1.填充参数默认值
        Space space = BeanUtil.copyProperties(spaceAddRequest, Space.class);
        if(StrUtil.isBlank(space.getSpaceName())){
            //如果未传空间名称，就设置默认空间名
            space.setSpaceName(loginUser.getUserName()+"的空间");
        }
        if(space.getSpaceLevel() == null){
            // 如果未指定空间级别，级别就默认设置为普通空间级别
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        // 创建空间我们可以创建两种类型的空间，用户创建空间的时候如果未指定空间类型，类型就默认设置为私有空间
        if(space.getSpaceType() == null){
            // 如果未指定空间类型，类型就默认设置为私有空间
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 根据空间级别填充限额信息
        fillSpaceBySpaceLevel(space);

        // 2.校验参数，空间名称和空间级别，传入的是 true 表示创建空间的校验逻辑
        validSpace(space, true);

        // 3.校验权限，非管理员只能创建普通级别的空间
        // 如果用户想要创建非普通空间级别的空间，并且他还不是管理员，就报错
        Long userId = loginUser.getId();
        space.setUserId(userId);
        if(space.getSpaceLevel() != SpaceLevelEnum.COMMON.getValue() && !userService.isAdmin(loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"普通用户没有权限创建普通级别以外的空间");
        }

        // 4.控制同一用户只能创建一个私有空间，以及一个团队空间（加锁，要上事务）
        // 如果要对这个方法加锁的话，我们这个锁能加到什么力度，假如说有10个不同的用户，同时调用了创建空间的方法，觉得这10个用户可以同时创建空间吗？
        // 还是一个一个排序，依次执行，不同用户能不能同时执行？那肯定是可以的，因为不同的用户，每个用户都可以创建一个，那不同的用户创建自己的谁也不碍着谁
        // 所以呢，锁的力度，不要加到整个方法上，而是每个用户可以有一把自己的锁，防止一个用户一下子点击10下创建空间，一个人创建10个空间

        // java 8 之后，我们有一个字符串常量池的概念，相同值的字符串它是有一个固定的同样的存储空间，为了保证这个锁对象它是同一把锁，是用的同样的存储空间
        // 所以我们加一个 intern() ，取到不同的 String 对象的同一个值，否则哪怕是同一个用户得到的也是不同的对象，每一个 String 都是不同的对象
        String lock = String.valueOf(userId).intern();

        // 使用本地锁
        // 这里面的代码，就是要被锁住的代码，只要是同一个用户，哪怕同一时间点击了两次，它会依次的进入这个锁的代码块中去，不会并发执行
        synchronized (lock){
            // 把所有操作数据库的操作都放到一个事务里,transactionStatus这个参数用来记录当前事务的状态，方法的返回值，就是方法内部的返回值
            Long newSpaceId = transactionTemplate.execute(transactionStatus -> {
                // 判断是否已经创建私有空间或者团队空间，现在用户分别只能对应创建一个
                // 这个exists()方法和count()方法相比的话，一般exists()方法效率更快一点，exists()方法是查询数据库中有没有符合条件的数据，count()是查询条数
                boolean exists = lambdaQuery()
                        .eq(Space::getUserId, userId)
                        .eq(Space::getSpaceType, space.getSpaceType())
                        .exists();
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户每类空间只能创建一个");
                boolean result = save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "出现异常，创建空间失败");
                // 创建成功后，如果是团队空间，关联新增团队成员记录
                if (SpaceTypeEnum.TEAM.getValue() == spaceAddRequest.getSpaceType()) {
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(userId);
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue()); //一定是管理员
                    result = spaceUserService.save(spaceUser);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                }
                // 创建分表（仅对团队空间有效） TODO 现在不用分表了
                // dynamicShardingManager.createSpacePictureTable(space);
                return space.getId();
            });
            //如果这个 id 为空的话，那它就会取一个其它的值，这里设置为 -1L ，但这行代码可写可不写，一般都不会为空的
            return Optional.ofNullable(newSpaceId).orElse( -1L);
        }
    }


    /**
     * 根据空间级别,自动填充限额，补充信息
     * 在创建؜或更新空间时，需要根据空间级别自动填‌充分配的空间的容量，以及空间最大的图片条数
     * 如果这个方法是管理员调用的话，它在填充space对象的时候，既指定了空间级别，又指定了空间容量的话
     * 应该先以管理员所设置的值为主
     * 空间容量到底是使用管理员指定的，还是系统默认的，我们肯定是优先走管理员设置的
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        //根据空间级别获取对应的枚举
        SpaceLevelEnum enumByValue = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());

        if(enumByValue != null){
            // 如果这个方法是管理员调用的时候，管理员指定了空间的限额，填充space对象时
            // 应该先以管理员所设置的值为主
            // 只有管理员没有设置这些值的时候，我们才根据默认级别设置默认值
            // 便于系统进行扩展
            if(space.getMaxCount() == null){
                space.setMaxCount(enumByValue.getMaxCount());
            }
            if(space.getMaxSize() == null){
                space.setMaxSize(enumByValue.getMaxSize());
            }
        }
    }


    /**
     * 私有空间权限校验，判断该用户有没有权限访问该空间
     *
     * @param loginUser
     * @param space
     */
    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        // 仅本人或管理员可访问
        if (!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }


    /**
     * 校验空间数据的方法，创建空间和修改空间时，信息的校验逻辑是不一致的，校验规则是不一致的
     * 比如：创建空间时，必须要传空间名称，修改空间的时候可以不改空间名称，可以不用传空间名称
     * 所以传的参数多加一个
     * 通过 boolean add 参数进行区分是创建空间还是修改空间，ture 为创建空间
     */
    @Override
    public void validSpace(Space space, boolean add) {
        //1.校验参数是否为空
        ThrowUtils.throwIf(space ==null , ErrorCode.PARAMS_ERROR);

        //从对象中取值
        Integer spaceLevel = space.getSpaceLevel();
        String spaceName = space.getSpaceName();
        Integer spaceType = space.getSpaceType();
        //根据传过来的空间级别，获取对应的枚举，也就是可以通过能否获取对应的枚举，判断空间级别在不在
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        //根据传过来的空间类型，获取对应的枚举
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);

        // 2.校验参数，创建空间比修改空间的逻辑多一点
        // 创建空间的时候，空间名称和空间级别以及空间类型都不能为空，修改空间的时候可以为空
        if(add){
            ThrowUtils.throwIf(StrUtil.isBlank(spaceName), ErrorCode.PARAMS_ERROR,"空间名称不能为空");
            ThrowUtils.throwIf(spaceLevel ==null, ErrorCode.PARAMS_ERROR,"空间级别不能为空");
            ThrowUtils.throwIf(spaceType == null, ErrorCode.PARAMS_ERROR,"空间类型不能为空");
        }

        // 3.修改和创建空间都需要这些判断
        // 空间类型的话不可以进行修改，如果可以修改的话，那可能得要修改几个表的数据了，这里还是进行判断一下，实际用户是不能修改空间类型的
        // 首先所传的参数不为空，才进行接下来的逻辑判断，创建空间肯定是不会为空的，修改空间的话，这些参数有的话才进行修改
        if(spaceLevel != null && spaceLevelEnum == null){
            //想设置的空间级别，如果设置的级别在系统默认的级别中都不存在的话，直接报错
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"不存在该级别的空间");
        }
        if(spaceName != null && spaceName.length() > 30){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间名称过长");
        }
        if(spaceType != null && spaceTypeEnum == null){
            //想设置的空间类型，如果设置的空间类型在系统默认的级别中都不存在，直接报错
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"不存在该类型的空间");
        }
    }


    /**
     * 获取 SpaceVO 封装对象
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        //对象封装类的封装
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        //查询关联用户
        Long userId = spaceVO.getUserId();
        if(userId != null && userId > 0){
            User user = userService.getById(userId);
            //如果能查到用户就进行封装，否则就是 null
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }


    /**
     * 获取 SpaceVO 分页封装
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        // 1.获取查询出的记录
        List<Space> records = spacePage.getRecords();

        // 2.构造所需返回的 Page<SpaceVO> 对象
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());

        // 3.如果查询出的记录为空的话，也就不需要进行封装了，直接返回
        if(CollUtil.isEmpty(records)){
            return spaceVOPage;
        }

        // 4.Space列表 => SpaceVO列表 ，将全部的 Space 对象，转换为 SpaceVO 对象
        List<SpaceVO> spaceVOList = records.stream()
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());

        // 5.获取所有空间的用户id集合
        Set<Long> userIdSet = records.stream()
                .map(Space::getUserId)
                .collect(Collectors.toSet());

        // 6.根据用户id集合，查询出所有的用户信息，封装到一个 Map 中一一对应，这种数据结构后面拿数据的时候，更高效
        Map<Long, List<User>> collect = userService.listByIds(userIdSet)
                .stream().collect(Collectors.groupingBy(User::getId));

        // 7.对 SpaceVO 集合中的每一个封装对象进行填充，按id进行匹配
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if(collect.containsKey(userId)){
                user = collect.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });

        // 8.将 集合封装到 Page 对象中
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }


    /**
     * 根据查询条件，获取 QueryWrapper
     * @param spaceQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        // 如果查询条件为空直接放回
        if(spaceQueryRequest == null){
            return queryWrapper;
        }
        queryWrapper.eq(ObjectUtil.isNotEmpty(spaceQueryRequest.getId()), "id", spaceQueryRequest.getId())
                .eq(ObjectUtil.isNotEmpty(spaceQueryRequest.getUserId()), "userId", spaceQueryRequest.getUserId())
                .eq(ObjectUtil.isNotEmpty(spaceQueryRequest.getSpaceLevel()), "spaceLevel", spaceQueryRequest.getSpaceLevel())
                .eq(ObjectUtil.isNotEmpty(spaceQueryRequest.getSpaceType()), "spaceType", spaceQueryRequest.getSpaceType())
                .like(StrUtil.isNotBlank(spaceQueryRequest.getSpaceName()), "spaceName", spaceQueryRequest.getSpaceName());

        // 排序           条件：排序字段是否非空，为空的话就不排序     是否升序（true=升序，false=降序）         排序字段名
        queryWrapper.orderBy(StrUtil.isNotEmpty(spaceQueryRequest.getSortField()),
                spaceQueryRequest.getSortOrder().equals("ascend"), spaceQueryRequest.getSortField());
        //如果前端没有传入排序字段（sortField 为空或 null），则 SQL 查询不会添加 ORDER BY 子句，即查询结果会按数据库默认顺序返回，不进行特定排序。

        return queryWrapper;
    }
}




