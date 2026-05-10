package com.zqp.mapper;

import com.zqp .entity.User;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface UserMapper {

    /**
     * 根据id查询用户
     */
    User selectUserById(@Param("id") Long id);

    /**
     * 新增用户
     */
    int insertUser(User user);

    /**
     * 修改用户
     */
    int updateUser(User user);

    /**
     * 删除用户
     */
    int deleteUserById(@Param("id") Long id);

    /**
     * 查询所有用户
     */
    List<User> selectUserList();
}