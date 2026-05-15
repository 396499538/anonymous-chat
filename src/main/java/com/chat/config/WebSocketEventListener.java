package com.chat.config;

import com.chat.model.ChatMessage;
import com.chat.model.UserInfo;
import com.chat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;

@Component
public class WebSocketEventListener {

    @Autowired
    private UserService userService;

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        System.out.println("New WebSocket connection: " + headerAccessor.getSessionId());
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        System.out.println("WebSocket connection closed: " + sessionId);

        // 从 session 获取用户ID并移除
        if (headerAccessor.getSessionAttributes() != null) {
            Object userIdObj = headerAccessor.getSessionAttributes().get("userId");
            if (userIdObj instanceof String) {
                String userId = (String) userIdObj;
                UserInfo user = userService.getUser(userId);
                if (user != null) {
                    userService.removeUser(userId);

                    // 广播用户离开消息
                    ChatMessage leaveMessage = new ChatMessage();
                    leaveMessage.setType("LEAVE");
                    leaveMessage.setSender(user.getNickname());
                    leaveMessage.setSenderId(user.getId());
                    leaveMessage.setContent(user.getNickname() + " 离开了聊天室");
                    leaveMessage.setOnlineCount(userService.getOnlineCount());
                    leaveMessage.setTimestamp(System.currentTimeMillis());

                    messagingTemplate.convertAndSend("/topic/public", leaveMessage);

                    // 更新用户列表
                    List<UserInfo> users = userService.getOnlineUsers();
                    messagingTemplate.convertAndSend("/topic/users", users);
                }
            }
        }
    }
}
