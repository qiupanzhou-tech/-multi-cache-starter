package com.zqp.warmup;

import com.zqp.cache.MultiLevelCache;
import com.zqp.entity.User;
import com.zqp.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 用户缓存预热任务（示例）
 *
 * 启动时将所有用户数据加载到多级缓存中。
 * 实现 CacheWarmupTask 接口即可被自动发现并异步执行。
 */
@Component
public class UserCacheWarmupTask implements CacheWarmupTask {

    private static final Logger log = LoggerFactory.getLogger(UserCacheWarmupTask.class);

    private static final String USER_CACHE_KEY = "user:info:";
    private static final String USER_LIST_KEY = "user:list";
    private static final long TTL_SECONDS = 1800;

    @Resource
    private UserMapper userMapper;

    @Resource
    private MultiLevelCache cache;

    @Override
    public void warmup() {
        log.info("Loading users from DB...");
        List<User> users = userMapper.selectUserList();

        if (users.isEmpty()) {
            log.info("No users found, skip warmup");
            return;
        }

        // 逐条缓存用户信息
        for (User user : users) {
            String key = USER_CACHE_KEY + user.getId();
            cache.put(key, user, TTL_SECONDS);
        }

        // 缓存全量列表
        cache.put(USER_LIST_KEY, users, 600);

        log.info("User cache warmup done: {} records", users.size());
    }

    @Override
    public String name() {
        return "用户缓存预热";
    }
}
