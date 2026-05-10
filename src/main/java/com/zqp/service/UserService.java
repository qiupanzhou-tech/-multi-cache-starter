package com.zqp.service;

import com.zqp.entity.User;
import java.util.List;

public interface UserService {

    /**
     * 根据id获取用户（带二级缓存逻辑）
     */
    User getUserById(Long id);

    /**
     * 新增用户
     */
    int addUser(User user);

    /**
     * 修改用户
     */
    int editUser(User user);

    /**
     * 删除用户
     */
    int removeUser(Long id);

    /**
     * 查询所有用户
     */
    List<User> listUser();
}