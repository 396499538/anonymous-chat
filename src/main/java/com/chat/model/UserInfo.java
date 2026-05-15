package com.chat.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserInfo {
    private String id;       //用户ID
    private String nickname; //随机昵称
    private String avatar;   //头像URL
    
    // 手动添加全参构造函数
    public UserInfo(String id, String nickname, String avatar) {
        this.id = id;
        this.nickname = nickname;
        this.avatar = avatar;
    }
}
