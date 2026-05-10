package com.zqp.service.impl;

import com.zqp.annotation.CacheEvict;
import com.zqp.annotation.CacheProtect;
import com.zqp.entity.User;
import com.zqp.mapper.UserMapper;
import com.zqp.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    // ========== 读操作：@CacheProtect 注解驱动 ==========

    @Override
    @CacheProtect(key = "user:info", keyExpression = "#id", ttl = 1800)
    public User getUserById(Long id) {
        return userMapper.selectUserById(id);
    }

    @Override
    @CacheProtect(key = "user:list", ttl = 600)
    public List<User> listUser() {
        return userMapper.selectUserList();
    }

    // ========== 写操作：@CacheEvict 自动清缓存 ==========

    @Override
    @CacheEvict(keys = {"user:list"})
    public int addUser(User user) {
        return userMapper.insertUser(user);
    }

    @Override
    @CacheEvict(keys = {"user:info:#user.id", "user:list"})
    public int editUser(User user) {
        return userMapper.updateUser(user);
    }

    @Override
    @CacheEvict(keys = {"user:info:#id", "user:list"})
    public int removeUser(Long id) {
        return userMapper.deleteUserById(id);
    }
}
