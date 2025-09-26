package com.air.yunpicturebackend.manager.sharding;

import com.air.yunpicturebackend.model.entity.Space;
import com.air.yunpicturebackend.model.enums.SpaceLevelEnum;
import com.air.yunpicturebackend.model.enums.SpaceTypeEnum;
import com.air.yunpicturebackend.service.SpaceService;
import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.jdbc.core.connection.ShardingSphereConnection;
import org.apache.shardingsphere.infra.metadata.database.rule.ShardingSphereRuleMetaData;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

//@Component  TODO 现在不用分库分表了，就把这个 bean 注释掉，不要加载这个 bean 了，用它的地方也注释掉
@Slf4j
public class DynamicShardingManager {

    @Resource
    private DataSource dataSource;

    @Resource
    private SpaceService spaceService;

    private static final String LOGIC_TABLE_NAME = "picture";

    private static final String DATABASE_NAME = "logic_db"; // 配置文件中的数据库名称


    /**
     * 初始化的时候，获取到现在可用的分表名称
     * 运用 PostConstruct 注解，在 DynamicShardingManager 这个 Bean 加载之后，我们就会首次执行分表
     * 来更新我们的 actual-data-nodes 这样一个可用分表数
     */
    @PostConstruct
    public void initialize() {
        log.info("初始化动态分表配置...");
        updateShardingTableNodes();
    }


    /**
     * 动态创建分表
     * 我们的表也要创建，ShardingSphere 只是帮我们路由，从哪里去读，从哪里去写
     * 但是它不会帮我们自动去建表的，所以我们还需要自己去建表
     * 创建空间的时候，就调用它
     */
    public void createSpacePictureTable(Space space) {
        // 动态创建分表
        // 仅为旗舰版团队空间创建分表
        if (space.getSpaceType() == SpaceTypeEnum.TEAM.getValue() && space.getSpaceLevel() == SpaceLevelEnum.FLAGSHIP.getValue()) {
            Long spaceId = space.getId();
            String tableName = "picture_" + spaceId;
            // 创建新表，动态拼接 sql
            String createTableSql = "CREATE TABLE " + tableName + " LIKE picture";
            try {
                // MP 提供的动态 sql 的执行器
                SqlRunner.db().update(createTableSql);
                // 更新分表，也就是更新配置
                updateShardingTableNodes();
            } catch (Exception e) {
                e.printStackTrace();
                log.error("创建图片空间分表失败，空间 id = {}", space.getId());
            }
        }
    }


    /**
     * 更新 ShardingSphere 的 actual-data-nodes 动态表名配置
     * 不仅是在首次加载项目的时候要调用更新，还要在创建新的团队空间的时候也要去更新这个分表数
     */
    private void updateShardingTableNodes() {
        // 1.获取可用的分表数据
        Set<String> tableNames = fetchAllPictureTableNames();
        // 2.生成前缀配置，给所有的表名拼接上 yun_picture.
        // 最终得到 yun_picture.picture_123123123123, yun_picture.picture_123123123124 这样一个字符串
        String newActualDataNodes = tableNames.stream()
                .map(tableName -> "yun_picture." + tableName) // 确保前缀合法
                .collect(Collectors.joining(","));

        log.info("动态分表 actual-data-nodes 配置: {}", newActualDataNodes);


        // 下面就是样板代码了，更改 ShardingSphere 的配置
        ContextManager contextManager = getContextManager();
        ShardingSphereRuleMetaData ruleMetaData = contextManager.getMetaDataContexts()
                .getMetaData()
                .getDatabases()
                .get(DATABASE_NAME)
                .getRuleMetaData();

        Optional<ShardingRule> shardingRule = ruleMetaData.findSingleRule(ShardingRule.class);
        if (shardingRule.isPresent()) {
            ShardingRuleConfiguration ruleConfig = (ShardingRuleConfiguration) shardingRule.get().getConfiguration();
            List<ShardingTableRuleConfiguration> updatedRules = ruleConfig.getTables()
                    .stream()
                    .map(oldTableRule -> {
                        if (LOGIC_TABLE_NAME.equals(oldTableRule.getLogicTable())) {                        //这一行是关键代码
                            ShardingTableRuleConfiguration newTableRuleConfig = new ShardingTableRuleConfiguration(LOGIC_TABLE_NAME, newActualDataNodes);
                            newTableRuleConfig.setDatabaseShardingStrategy(oldTableRule.getDatabaseShardingStrategy());
                            newTableRuleConfig.setTableShardingStrategy(oldTableRule.getTableShardingStrategy());
                            newTableRuleConfig.setKeyGenerateStrategy(oldTableRule.getKeyGenerateStrategy());
                            newTableRuleConfig.setAuditStrategy(oldTableRule.getAuditStrategy());
                            return newTableRuleConfig;
                        }
                        return oldTableRule;
                    })
                    .collect(Collectors.toList());
            ruleConfig.setTables(updatedRules);
            contextManager.alterRuleConfiguration(DATABASE_NAME, Collections.singleton(ruleConfig));
            contextManager.reloadDatabase(DATABASE_NAME);
            log.info("动态分表规则更新成功！");
        } else {
            log.error("未找到 ShardingSphere 的分片规则配置，动态分表更新失败。");
        }
    }


    /**
     * 获取所有动态表名，包括初始表 picture 和分表 picture_{spaceId}
     */
    private Set<String> fetchAllPictureTableNames() {
        // 为了测试方便，直接对所有团队空间分表（实际上线改为仅对旗舰版生效）  todo 这里需要修改
        // 查出来所有的 spaceId
        Set<Long> spaceIds = spaceService.lambdaQuery()
                .eq(Space::getSpaceType, SpaceTypeEnum.TEAM.getValue())
                .eq(Space::getSpaceLevel,SpaceLevelEnum.FLAGSHIP.getValue())
                .list()
                .stream()
                .map(Space::getId)
                .collect(Collectors.toSet());
        // 根据这个 id 拼接上我们的逻辑表表名，也就是 picture
        Set<String> tableNames = spaceIds.stream()
                .map(spaceId -> LOGIC_TABLE_NAME + "_" + spaceId)
                .collect(Collectors.toSet());
        // 如果要查逻辑表（所有的表），也就是 picture 本身，需要把逻辑表加到我们的可用表里，否则当我们查 picture 表的时候也会报错
        tableNames.add(LOGIC_TABLE_NAME); // 补充逻辑表
        return tableNames;
    }


    /**
     * 获取 ShardingSphere ContextManager
     */
    private ContextManager getContextManager() {
        try (ShardingSphereConnection connection = dataSource.getConnection().unwrap(ShardingSphereConnection.class)) {
            return connection.getContextManager();
        } catch (SQLException e) {
            throw new RuntimeException("获取 ShardingSphere ContextManager 失败", e);
        }
    }
}
