package com.air.yunpicturebackend.manager.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

/**
 * 图片分表算法
 * 我们要实现动态分表，就是要先实现 ShardingSphere 提供的接口，StandardShardingAlgorithm<Long> 这个接口
 */
public class PictureShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    /**
     * 接口里的就这个方法我们要实现
     *
     * preciseShardingValue 的值就是我们的 spaceId ，因为我们的 yaml配置文件中配置了，我们分片是按照 spaceId 这一列，所以这个参数就是 spaceId
     * availableTargetNames 就是我们所有支持的分表
     * availableTargetNames 可用的目标表的值是由配置文件中的 actual-data-nodes 这个字段决定的
     * 这个字段我们已经推论过了，spaceId 是一个长整型，可用表名太多了，没有办法指定一个范围
     * 既然实际的数据节点既然已经是一个固定值 picture 了，那我是不是就没有办法查实际表了 availableTargetNames.contains(realTableName) 这就有问题了
     * 而且还有一个点是，ShardingSphere 它不仅仅会应用我们这个分表算法逻辑，自己也带了一个校验，当它发现你要查的表不在这个目标表中
     * 就不在 actual-data-nodes 配置中，框架自己会报错，这 actual-data-nodes 设置不了范围值，覆盖不了 spaceId
     *
     * 解决方法：
     * 如果我们自己在代码中指定可用的表有哪些，是不是这个问题就解决了，ShardingSphere 灵活的框架，我们是可以在运行的时候，动态更改 actual-data-nodes 这个配置的
     * 虽然我们在配置阶段不能写死它的值，但是如果我们在运行的时候动态更改这个配置的值，一切的问题就很简单了
     * 我们实际能用的表，就等于我们查询空间表中空间的类型是团队空间而且级别为旗舰空间版空间的 spaceId
     * 我们可以在项目启动的时候，查一下空间表把已有的 spaceId 作为的 actual-data-nodes 的值，这样我们下面的代码就不用改了
     * 我们可以自己实现一个动态分表的管理器
     * 既然框架自身不支持动态维护分表，那我们可以写一个分表管理器，自己来维护分表列表，并更新到 ShardingSphere 的 actual-data-nodes 配置中。
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> preciseShardingValue) {
        Long spaceId = preciseShardingValue.getValue(); // 拿到 spaceId
        String logicTableName = preciseShardingValue.getLogicTableName();  // 可以通过这个拿到逻辑表，就是 Picture 表

        // 如果 spaceId 为 null 就是要查询所有的图片，起码表示要查公共图库的
        // 没有指定查询空间的范围，直接返回逻辑表，表示接下来让 ShardingSphere 查询所有的表，挨个去查询
        if (spaceId == null) {
            return logicTableName;
        }

        // 如果有 spaceId ，就拼接出来真实的表名了
        String realTableName = "picture_" + spaceId;
        // 如果真实的表名就在我们已经支持分表的表名里，那就查真实的单张表
        if (availableTargetNames.contains(realTableName)) {
            return realTableName;
        } else {
            // 否则，如果表名不在目标范围内，要么就报错，要么就返回逻辑表也就是所有的表
            // 让它再挨个去遍历，就是一张表我找不到，就把所有的表查完
            return logicTableName;
        }
    }


    /**
     * 这个方法不用管，这个是范围类的分片查询，才要实现这个，我们项目中没有根据多个 spaceId 去查的情况，所以这个方法不用实现
     */
    @Override
    public Collection<String> doSharding(Collection<String> collection, RangeShardingValue<Long> rangeShardingValue) {
        return new ArrayList<>();
    }

    @Override
    public Properties getProps() {
        return null;
    }

    @Override
    public void init(Properties properties) {
    }
}
