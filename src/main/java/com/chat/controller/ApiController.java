package com.chat.controller;

import com.chat.model.UserInfo;
import com.chat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private UserService userService;

    /**
     * 创建新用户
     */
    @PostMapping("/createUser")
    public ResponseEntity<UserInfo> createUser() {
        UserInfo user = userService.createUser();
        return ResponseEntity.ok(user);
    }

    /**
     * 创建自定义用户（带昵称和头像）
     */
    @PostMapping("/createCustomUser")
    public ResponseEntity<UserInfo> createCustomUser(@RequestBody Map<String, String> request) {
        String nickname = request.get("nickname");
        String avatar = request.get("avatar");
        
        if (nickname == null || nickname.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        UserInfo user = userService.createCustomUser(nickname.trim(), avatar);
        return ResponseEntity.ok(user);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/user/{id}")
    public ResponseEntity<UserInfo> getUser(@PathVariable String id) {
        UserInfo user = userService.getUser(id);
        if (user != null) {
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 获取在线用户列表
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserInfo>> getOnlineUsers() {
        return ResponseEntity.ok(userService.getOnlineUsers());
    }

    /**
     * 获取在线人数
     */
    @GetMapping("/onlineCount")
    public ResponseEntity<Integer> getOnlineCount() {
        return ResponseEntity.ok(userService.getOnlineCount());
    }

    /**
     * 生成随机昵称
     */
    @GetMapping("/generateNickname")
    public ResponseEntity<Map<String, String>> generateNickname() {
        String nickname = userService.generateRandomNickname();
        Map<String, String> response = new HashMap<>();
        response.put("nickname", nickname);
        return ResponseEntity.ok(response);
    }

    /**
     * 验证用户是否有效
     */
    @PostMapping("/validateUser")
    public ResponseEntity<Map<String, Object>> validateUser(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        UserInfo user = userService.getUser(userId);
        
        Map<String, Object> response = new HashMap<>();
        if (user != null) {
            response.put("valid", true);
            response.put("user", user);
        } else {
            response.put("valid", false);
        }
        
        return ResponseEntity.ok(response);
    }
}
