package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String type;      //消息类型: JOIN, LEAVE, CHAT, SYSTEM, USER_LIST
    private String sender;    //发送者昵称
    private String senderId;  //发送者ID
    private String content;   //消息内容
    private String avatar;    //用户头像
    private long timestamp;   //时间戳
    private int onlineCount;  //在线人数
    private List<String> mentionedUserIds; //被@的用户ID列表
}
