package com.zqp.controller;

import com.zqp.entity.User;
import com.zqp.service.UserService;
import com.zqp.util.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 根据id查询用户（带二级缓存）
     */
    @GetMapping("/get/{id}")
    public Result<User> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return Result.success(user);
    }

    /**
     * 新增用户
     */
    @PostMapping("/add")
    public Result<Integer> addUser(@RequestBody User user) {
        int rows = userService.addUser(user);
        return Result.success(rows);
    }

    /**
     * 修改用户
     */
    @PutMapping("/update")
    public Result<Integer> updateUser(@RequestBody User user) {
        int rows = userService.editUser(user);
        return Result.success(rows);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/delete/{id}")
    public Result<Integer> deleteUser(@PathVariable Long id) {
        int rows = userService.removeUser(id);
        return Result.success(rows);
    }

    /**
     * 查询所有用户
     */
    @GetMapping("/list")
    public Result<List<User>> listUser() {
        List<User> userList = userService.listUser();
        return Result.success(userList);
    }
}