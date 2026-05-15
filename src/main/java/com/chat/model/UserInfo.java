package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
    private String id;       //用户ID
    private String nickname; //随机昵称
    private String avatar;   //头像URL
}
