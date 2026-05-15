package com.chat.controller;

import com.chat.model.ChatMessage;
import com.chat.model.UserInfo;
import com.chat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Base64;
import java.util.List;
import java.util.Random;

@Controller
public class ChatController {

    @Autowired
    private UserService userService;

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;
    
    @Autowired
    private com.chat.service.AiBotService aiBotService;
    
    // AI机器人定时器
    private java.util.Timer aiBotTimer = null;
    private final Object timerLock = new Object();
    
    // AI机器人状态管理
    private volatile AiBotState currentAiState = AiBotState.IDLE;
    private volatile int currentQuestionIndex = -1;
    private volatile long lastMessageTime = 0;
    private volatile boolean aiBotEnabled = true; // AI机器人是否启用
    private volatile long aiWakeUpTime = 0; // AI唤醒时间
    private volatile boolean afterWakeUpPromptSent = false; // 是否已发送唤醒后提示
    
    // 趣味性开场白列表
    private static final String[] INTRO_MESSAGES = {
        "哇塞，这里安静得能听到针掉地上的声音~ 来点刺激的怎么样？",
        "大家都去偷懒了吗？我来活跃一下气氛！",
        "这么久没人说话，我都要睡着啦！来个脑筋急转弯提提神~",
        "咳咳，检测到聊天室陷入沉默...是时候展现真正的技术了！",
        "无聊指数已达99%！急需一个脑筋急转弯拯救世界！",
        "咦？人呢？都潜水去了吗？那我就不客气地出题啦~"
    };
    
    // 趣味性提示语列表
    private static final String[] PROMPT_MESSAGES = {
        "这么久都没人答对，是不是题目太难了？要不要我给点提示呀？（回复'要'或'不要'）",
        "你们都卡在哪儿啦？要不要我公布答案让你们开开眼界？（回复'要'或'不要'）",
        "看来这道题把大家都难住了~ 要我揭晓谜底吗？（回复'要'或'不要'）",
        "时间过得真快，还是没人猜出来...需要我剧透吗？（回复'要'或'不要'）",
        "我都等得花儿都谢了~ 要不要直接看答案？（回复'要'或'不要'）"
    };
    
    // 趣味性公布答案列表
    private static final String[] ANSWER_MESSAGES = {
        "哈哈，终于等到这一刻！答案就是——",
        "好了好了，不卖关子了，正确答案是——",
        "见证奇迹的时刻到了！答案是——",
        "让你们久等啦！其实答案很简单——",
        "揭晓时刻！这个脑筋急转弯的答案是——"
    };
    
    // 趣味性调皮回复列表
    private static final String[] PLAYFUL_MESSAGES = {
        "哎呀，{user} 真是个倔强的小可爱！好吧，那我就保守这个秘密啦~ 😜",
        "{user} 居然拒绝了我！哼，那答案就让它成为一个谜吧~ 🤫",
        "哇，{user} 这么有个性！行，我就不告诉你，急死你~ 😏",
        "{user} 说不想知道？那我偏不说！嘿嘿~ 🤭",
        "好吧 {user}，既然你这么坚持，那我就继续保持神秘感咯~ ✨"
    };
    
    // AI简单问答回复列表
    private static final String[][] AI_CHAT_RESPONSES = {
        // 问候
        {"你好", "您好", "hello", "hi", "hey"},
        {"👋 你好呀！有什么我可以帮你的吗？", "😊 嗨！很高兴见到你！", "✨ 你好！我是AI小助手，随时为你服务~"},
        
        // 感谢
        {"谢谢", "感谢", "thanks", "thank you"},
        {"不客气！能帮到你就好~ 😊", "不用谢！这是我应该做的！✨", "哈哈，小事一桩！😄"},
        
        // 再见
        {"再见", "拜拜", "bye", "goodbye"},
        {"再见！下次再聊哦~ 👋", "拜拜！期待下次见面！😊", "好的，再见！记得想我的时候@我哦~ ✨"},
        
        // 身份
        {"你是谁", "你是ai", "你是什么", "who are you"},
        {"🤖 我是AI小助手，可以陪你聊天、出脑筋急转弯哦~", "✨ 我是聊天室的AI机器人，随时准备和你互动！", "😊 我是AI小助手，无聊的时候找我玩呀~"},
        
        // 能力
        {"你能做什么", "你会什么", "what can you do"},
        {"💡 我可以出脑筋急转弯、陪你聊天解闷哦~", "🎯 我会出题考考你，也会回答你的问题！", "✨ 我能让你开心，也能让你思考，试试看？"}
    };
    
    // AI机器人状态枚举
    private enum AiBotState {
        IDLE,                    // 空闲
        AWAKE_WAITING_PROMPT,    // 唤醒后等待发送开场白
        ASKED_QUESTION,          // 已提出问题，等待回答
        WAITING_ANSWER,          // 等待用户回复是否公布答案
        CONTINUE_GUESS,          // 用户拒绝公布答案，继续猜
        AWAITING_AUTO_EXIT       // 等待自动退出
    }

    /**
     * 用户连接时创建用户信息
     */
    @MessageMapping("/join")
    public void join(@Payload ChatMessage message, StompHeaderAccessor headerAccessor) {
        // 优先从消息体中获取用户ID（前端传递的）
        String userIdFromClient = null;
        if (message != null && message.getSenderId() != null && !message.getSenderId().isEmpty()) {
            userIdFromClient = message.getSenderId();
            System.out.println("前端传递的用户ID: " + userIdFromClient);
        }
        
        // 检查session中是否已有用户
        String userIdFromSession = (String) headerAccessor.getSessionAttributes().get("userId");
        
        UserInfo user;
        boolean isNewUser = false;
        String finalUserId;
        
        // 优先级：前端传来的ID > Session中的ID > 创建新用户
        if (userIdFromClient != null && userService.getUser(userIdFromClient) != null) {
            // 前端传来的用户ID有效，恢复该用户
            user = userService.getUser(userIdFromClient);
            finalUserId = userIdFromClient;
            headerAccessor.getSessionAttributes().put("userId", finalUserId);
            System.out.println("✓ 恢复前端传来的用户: " + user.getNickname() + " (ID: " + finalUserId + ")");
        } else if (userIdFromSession != null && userService.getUser(userIdFromSession) != null) {
            // Session中有有效的用户ID
            user = userService.getUser(userIdFromSession);
            finalUserId = userIdFromSession;
            System.out.println("✓ 使用Session中的用户: " + user.getNickname() + " (ID: " + finalUserId + ")");
        } else if (userIdFromClient != null && message != null && message.getSender() != null) {
            // 前端传来了用户ID和用户信息（昵称、头像），但服务器重启后用户不存在
            // 这种情况下，使用前端传来的用户信息重新创建用户（保持相同的ID和信息）
            finalUserId = userIdFromClient;
            String nickname = message.getSender();
            String avatar = message.getAvatar();
            
            // 如果没有提供头像，生成一个
            if (avatar == null || avatar.isEmpty()) {
                avatar = generateDefaultAvatar();
            }
            
            user = new UserInfo(finalUserId, nickname, avatar);
            userService.addUser(user);  // 将用户添加到在线用户列表
            headerAccessor.getSessionAttributes().put("userId", finalUserId);
            System.out.println("✓ 根据前端信息重建用户: " + user.getNickname() + " (ID: " + finalUserId + ")");
        } else {
            // 创建新用户
            user = userService.createUser();
            finalUserId = user.getId();
            headerAccessor.getSessionAttributes().put("userId", finalUserId);
            isNewUser = true;
            System.out.println("★ 创建新用户: " + user.getNickname() + " (ID: " + finalUserId + ")");

            // 广播用户加入消息
            ChatMessage joinMessage = new ChatMessage();
            joinMessage.setType("JOIN");
            joinMessage.setSender(user.getNickname());
            joinMessage.setSenderId(user.getId());
            joinMessage.setAvatar(user.getAvatar());
            joinMessage.setContent("欢迎 " + user.getNickname() + " 加入聊天室！");
            joinMessage.setOnlineCount(userService.getOnlineCount());
            joinMessage.setTimestamp(System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/public", joinMessage);
        }
        
        // 从 STOMP headers 中获取 login（即 requestId 或 userId）
        String login = headerAccessor.getLogin();
        System.out.println("STOMP login header: " + login);
        
        // 发送用户信息给当前连接者 - 使用 login 作为目标用户
        if (login != null && !login.isEmpty()) {
            messagingTemplate.convertAndSendToUser(login, "/queue/private", user);
            System.out.println("→ 已发送用户信息到 /user/" + login + "/queue/private: " + user.getNickname());
        } else {
            System.err.println("⚠ 警告: 没有 login header，无法发送私有消息！");
        }
        
        // 延迟100ms后广播最新的在线用户列表，确保所有客户端都已收到私有消息
        // 这样可以避免前端在 currentUser 未设置时就收到用户列表
        new Thread(() -> {
            try {
                Thread.sleep(100);
                List<UserInfo> users = userService.getOnlineUsers();
                System.out.println("广播用户列表，当前在线人数: " + users.size());
                messagingTemplate.convertAndSend("/topic/users", users);
                
                // 启动AI机器人定时器（如果是第一个用户）
                startAiBotTimer();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 处理聊天消息
     */
    @MessageMapping("/chat")
    public void sendMessage(@Payload ChatMessage message, StompHeaderAccessor headerAccessor) {
        String userId = (String) headerAccessor.getSessionAttributes().get("userId");
        UserInfo user = userService.getUser(userId);
        
        if (user != null) {
            message.setSenderId(user.getId());
            message.setSender(user.getNickname());
            message.setAvatar(user.getAvatar());
            message.setTimestamp(System.currentTimeMillis());
            message.setOnlineCount(userService.getOnlineCount());
            
            // 解析消息中的@用户
            List<String> mentionedUserIds = parseMentionedUsers(message.getContent());
            message.setMentionedUserIds(mentionedUserIds);
            
            messagingTemplate.convertAndSend("/topic/public", message);
            
            // 更新最后消息时间
            lastMessageTime = System.currentTimeMillis();
            
            // 检查是否是AI机器人控制指令
            if (handleAiBotCommand(message.getContent(), user.getNickname())) {
                return; // 如果是指令，不再执行后续逻辑
            }
            
            // 检查是否是AI机器人互动阶段的用户回复
            handleAiBotReply(message.getContent(), user.getNickname());
            
            // 重置AI机器人定时器
            resetAiBotTimer();
        }
    }
    
    /**
     * 解析消息中被@的用户ID
     */
    private List<String> parseMentionedUsers(String content) {
        List<String> mentionedIds = new java.util.ArrayList<>();
        if (content == null || content.isEmpty()) {
            return mentionedIds;
        }
        
        // 查找所有 @昵称 的模式（匹配非空白字符）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("@([^\\s]+)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String mentionedNickname = matcher.group(1);
            // 根据昵称查找用户ID
            for (UserInfo userInfo : userService.getOnlineUsers()) {
                if (userInfo.getNickname().equals(mentionedNickname)) {
                    mentionedIds.add(userInfo.getId());
                    break;
                }
            }
        }
        
        return mentionedIds;
    }

    /**
     * 获取当前在线用户列表
     */
    @MessageMapping("/getUsers")
    public void getUsers() {
        messagingTemplate.convertAndSend("/topic/users", userService.getOnlineUsers());
    }
    
    /**
     * 生成默认头像（当用户提供昵称但没有提供头像时）
     */
    private String generateDefaultAvatar() {
        Random random = new Random();
        String[] colors = {"#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7",
                          "#DDA0DD", "#98D8C8", "#F7DC6F", "#BB8FCE", "#85C1E9"};
        String[] emojis = {"😀", "😎", "🤗", "😇", "🥰", "🤩", "😋", "🤭", "🙃", "😊"};
        
        String color = colors[random.nextInt(colors.length)];
        String emoji = emojis[random.nextInt(emojis.length)];
        
        String svg = String.format(
            "<svg xmlns='http://www.w3.org/2000/svg' width='100' height='100' viewBox='0 0 100 100'>" +
            "<rect width='100' height='100' fill='%s' rx='15'/>" +
            "<text x='50' y='65' font-size='50' text-anchor='middle' fill='white'>%s</text>" +
            "</svg>",
            color, emoji
        );
        
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes());
    }
    
    /**
     * 启动AI机器人定时器
     */
    private void startAiBotTimer() {
        synchronized (timerLock) {
            // 如果已经有定时器，不重复创建
            if (aiBotTimer != null) {
                return;
            }
            
            System.out.println("🤖 AI机器人已启动，将在60秒（1分钟）无人说话时发送脑筋急转弯");
            
            aiBotTimer = new java.util.Timer("AiBotTimer", true);
            aiBotTimer.scheduleAtFixedRate(new java.util.TimerTask() {
                @Override
                public void run() {
                    checkAndSendBrainTeaser();
                }
            }, 60000, 60000); // 60秒（1分钟）后开始，每60秒检查一次
        }
    }
    
    /**
     * 重置AI机器人定时器
     */
    private void resetAiBotTimer() {
        synchronized (timerLock) {
            if (aiBotTimer != null) {
                aiBotTimer.cancel();
                aiBotTimer = null;
                System.out.println("🔄 检测到用户发言，重置AI机器人定时器");
                
                // 重新启动定时器
                aiBotTimer = new java.util.Timer("AiBotTimer", true);
                aiBotTimer.scheduleAtFixedRate(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        checkAndSendBrainTeaser();
                    }
                }, 60000, 60000); // 60秒（1分钟）后开始，每60秒检查一次
            }
        }
    }
    
    /**
     * 检查并发送脑筋急转弯（根据状态决定行为）
     */
    private void checkAndSendBrainTeaser() {
        // 如果AI机器人被禁用，不执行
        if (!aiBotEnabled) {
            return;
        }
        
        // 检查是否有在线用户
        if (userService.getOnlineCount() == 0) {
            synchronized (timerLock) {
                if (aiBotTimer != null) {
                    aiBotTimer.cancel();
                    aiBotTimer = null;
                    currentAiState = AiBotState.IDLE;
                    System.out.println("🔕 没有在线用户，停止AI机器人");
                }
            }
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastMessage = currentTime - lastMessageTime;
        
        switch (currentAiState) {
            case IDLE:
                // 空闲状态，60秒无人说话时自动开启脑筋急转弯
                if (timeSinceLastMessage >= 60000) {
                    System.out.println("🤖 检测到60秒无人说话，自动开启脑筋急转弯");
                    sendQuestionPhase();
                }
                break;
                
            case AWAKE_WAITING_PROMPT:
                // 唤醒后1分钟没人说话，发送趣味开场白
                if (timeSinceLastMessage >= 60000 && !afterWakeUpPromptSent) {
                    sendWakeUpPrompt();
                    afterWakeUpPromptSent = true;
                }
                break;
                
            case ASKED_QUESTION:
                // 已提出问题，等待30秒后如果没有人回答，进入第二轮
                if (timeSinceLastMessage >= 30000) {
                    sendPromptAnswerPhase();
                }
                break;
                
            case WAITING_ANSWER:
                // 等待用户回复是否公布答案，30秒后自动公布
                if (timeSinceLastMessage >= 30000) {
                    sendFinalAnswerPhase();
                }
                break;
                
            case CONTINUE_GUESS:
                // 用户拒绝公布答案后继续猜，30秒后自动公布答案
                if (timeSinceLastMessage >= 30000) {
                    sendFinalAnswerPhase();
                }
                break;
                
            case AWAITING_AUTO_EXIT:
                // 一轮结束后，1分钟没人说话自动退出
                if (timeSinceLastMessage >= 60000) {
                    autoExitAi();
                }
                break;
        }
    }
    
    /**
     * 第一阶段：发送开场白和问题
     */
    private void sendQuestionPhase() {
        // 获取随机问题索引
        currentQuestionIndex = aiBotService.getRandomQuestionIndex();
        // 根据索引获取问题，确保问题和答案对应
        String question = aiBotService.getQuestionByIndex(currentQuestionIndex);
        
        // 随机选择开场白
        Random random = new Random();
        String introMessage = INTRO_MESSAGES[random.nextInt(INTRO_MESSAGES.length)];
        
        // 发送开场白
        ChatMessage introMsg = new ChatMessage();
        introMsg.setType("CHAT");
        introMsg.setSender("🤖 AI小助手");
        introMsg.setSenderId("ai_bot");
        introMsg.setAvatar(generateAiBotAvatar());
        introMsg.setContent(introMessage);
        introMsg.setOnlineCount(userService.getOnlineCount());
        introMsg.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/public", introMsg);
        
        // 延迟1-2秒后发送问题（增加自然感）
        new Thread(() -> {
            try {
                Thread.sleep(1000 + random.nextInt(1000));
                
                ChatMessage questionMessage = new ChatMessage();
                questionMessage.setType("CHAT");
                questionMessage.setSender("🤖 AI小助手");
                questionMessage.setSenderId("ai_bot");
                questionMessage.setAvatar(generateAiBotAvatar());
                questionMessage.setContent("🧠 " + question + "\n💡 回复格式：直接输入你的答案\n⏱️ 30秒后公布答案");
                questionMessage.setOnlineCount(userService.getOnlineCount());
                questionMessage.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/public", questionMessage);
                
                currentAiState = AiBotState.ASKED_QUESTION;
                lastMessageTime = System.currentTimeMillis(); // 重置计时器
                
                System.out.println("🤖 AI机器人提出问题 (索引: " + currentQuestionIndex + "): " + question);
                
                // 启动30秒延迟任务，如果无人回答则进入下一阶段
                new Thread(() -> {
                    try {
                        Thread.sleep(30000); // 等待30秒
                        
                        // 如果30秒后状态仍然是ASKED_QUESTION，说明没有人回答正确
                        if (currentAiState == AiBotState.ASKED_QUESTION) {
                            sendPromptAnswerPhase();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    /**
     * 唤醒后发送趣味开场白
     */
    private void sendWakeUpPrompt() {
        Random random = new Random();
        String introMessage = INTRO_MESSAGES[random.nextInt(INTRO_MESSAGES.length)];
        
        ChatMessage promptMsg = new ChatMessage();
        promptMsg.setType("CHAT");
        promptMsg.setSender("🤖 AI小助手");
        promptMsg.setSenderId("ai_bot");
        promptMsg.setAvatar(generateAiBotAvatar());
        promptMsg.setContent(introMessage);
        promptMsg.setOnlineCount(userService.getOnlineCount());
        promptMsg.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/public", promptMsg);
        
        System.out.println("🤖 AI机器人发送唤醒后提示");
        
        // 再等1分钟后如果没有人回复，自动出题
        // 这里不需要额外处理，因为定时器会继续检查，1分钟后会调用sendQuestionPhase
        // 我们需要在sendWakeUpPrompt后设置一个标志，让下一次60秒检测时出题
        // 实际上，我们应该在这里直接启动一个延迟任务
        new Thread(() -> {
            try {
                Thread.sleep(60000); // 等待1分钟
                
                // 如果1分钟后仍然没有消息，自动出题
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastMessageTime >= 60000 && currentAiState == AiBotState.AWAKE_WAITING_PROMPT) {
                    sendQuestionPhase();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    /**
     * 自动退出AI
     */
    private void autoExitAi() {
        aiBotEnabled = false;
        
        // 停止定时器
        synchronized (timerLock) {
            if (aiBotTimer != null) {
                aiBotTimer.cancel();
                aiBotTimer = null;
            }
        }
        
        // 重置状态
        currentAiState = AiBotState.IDLE;
        currentQuestionIndex = -1;
        aiWakeUpTime = 0;
        afterWakeUpPromptSent = false;
        
        ChatMessage exitMessage = new ChatMessage();
        exitMessage.setType("CHAT");
        exitMessage.setSender("🤖 AI小助手");
        exitMessage.setSenderId("ai_bot");
        exitMessage.setAvatar(generateAiBotAvatar());
        exitMessage.setContent("😴 看来大家都去忙了，我先休息啦~\n💡 想我的时候发送 \"@ai\" 或 \"唤醒ai\" 就可以叫我回来哦！");
        exitMessage.setOnlineCount(userService.getOnlineCount());
        exitMessage.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/public", exitMessage);
        
        System.out.println("🤖 AI机器人自动退出");
    }
    
    /**
     * 第二阶段：提示是否公布答案
     */
    private void sendPromptAnswerPhase() {
        Random random = new Random();
        String promptMessage = PROMPT_MESSAGES[random.nextInt(PROMPT_MESSAGES.length)];
            
        ChatMessage promptMsg = new ChatMessage();
        promptMsg.setType("CHAT");
        promptMsg.setSender("🤖 AI小助手");
        promptMsg.setSenderId("ai_bot");
        promptMsg.setAvatar(generateAiBotAvatar());
        promptMsg.setContent(promptMessage);
        promptMsg.setOnlineCount(userService.getOnlineCount());
        promptMsg.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/public", promptMsg);
            
        currentAiState = AiBotState.WAITING_ANSWER;
        lastMessageTime = System.currentTimeMillis(); // 重置计时器
            
        System.out.println("🤖 AI机器人提示是否公布答案");
        
        // 启动5秒延迟任务，如果用户未回复则自动公布答案
        new Thread(() -> {
            try {
                Thread.sleep(5000); // 等待5秒
                
                // 如果5秒后状态仍然是WAITING_ANSWER，说明用户没有回复
                if (currentAiState == AiBotState.WAITING_ANSWER) {
                    sendFinalAnswerPhase();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    /**
     * 第三阶段：最终公布答案
     */
    private void sendFinalAnswerPhase() {
        if (currentQuestionIndex >= 0) {
            String answer = aiBotService.getAnswerByIndex(currentQuestionIndex);
            Random random = new Random();
            
            // 随机选择开场语
            String answerIntro = ANSWER_MESSAGES[random.nextInt(ANSWER_MESSAGES.length)];
            
            ChatMessage answerMessage = new ChatMessage();
            answerMessage.setType("CHAT");
            answerMessage.setSender("🤖 AI小助手");
            answerMessage.setSenderId("ai_bot");
            answerMessage.setAvatar(generateAiBotAvatar());
            answerMessage.setContent(answerIntro + "\n✨ " + answer + "\n\n😎 是不是很简单？下次再来挑战更难的！");
            answerMessage.setOnlineCount(userService.getOnlineCount());
            answerMessage.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/public", answerMessage);
            
            System.out.println("🤖 AI机器人公布答案: " + answer);
        }
        
        // 重置问题索引，但进入等待自动退出状态
        currentQuestionIndex = -1;
        currentAiState = AiBotState.AWAITING_AUTO_EXIT;
        lastMessageTime = System.currentTimeMillis(); // 重置计时器，开始计旹1分钟
        
        System.out.println("🤖 AI机器人一轮结束，等待1分钟后自动退出");
    }
    
    /**
     * 处理AI机器人控制指令（唤醒/退出）
     */
    private boolean handleAiBotCommand(String userMessage, String userName) {
        String trimmedMessage = userMessage.trim().toLowerCase();
        
        // 检查是否是唤醒指令
        if (trimmedMessage.contains("@ai") || 
            trimmedMessage.contains("@ai小助手") || 
            trimmedMessage.contains("唤醒ai") ||
            trimmedMessage.equals("ai在吗") ||
            trimmedMessage.equals("ai出来")) {
            
            if (!aiBotEnabled) {
                // AI已禁用，重新启用
                aiBotEnabled = true;
                aiWakeUpTime = System.currentTimeMillis();
                afterWakeUpPromptSent = false;
                currentAiState = AiBotState.AWAKE_WAITING_PROMPT;
                
                // 延迟3秒后发送唤醒回复，确保用户消息先显示
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        
                        ChatMessage enableMessage = new ChatMessage();
                        enableMessage.setType("CHAT");
                        enableMessage.setSender("🤖 AI小助手");
                        enableMessage.setSenderId("ai_bot");
                        enableMessage.setAvatar(generateAiBotAvatar());
                        enableMessage.setContent("✨ " + userName + " 把我唤醒了！我又可以陪大家玩啦~\n💡 提示：发送 \"@ai 退出\" 可以让我休息哦");
                        enableMessage.setOnlineCount(userService.getOnlineCount());
                        enableMessage.setTimestamp(System.currentTimeMillis());
                        messagingTemplate.convertAndSend("/topic/public", enableMessage);
                        
                        System.out.println("🤖 AI机器人被 " + userName + " 唤醒");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
                
                // 重新启动定时器
                resetAiBotTimer();
            } else {
                // AI已启用，回应一下
                aiWakeUpTime = System.currentTimeMillis();
                afterWakeUpPromptSent = false;
                currentAiState = AiBotState.AWAKE_WAITING_PROMPT;
                
                // 延迟3秒后发送回应，确保用户消息先显示
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        
                        ChatMessage responseMessage = new ChatMessage();
                        responseMessage.setType("CHAT");
                        responseMessage.setSender("🤖 AI小助手");
                        responseMessage.setSenderId("ai_bot");
                        responseMessage.setAvatar(generateAiBotAvatar());
                        responseMessage.setContent("🙋‍♂️ 我在呢，" + userName + "！有什么需要帮忙的吗？");
                        responseMessage.setOnlineCount(userService.getOnlineCount());
                        responseMessage.setTimestamp(System.currentTimeMillis());
                        messagingTemplate.convertAndSend("/topic/public", responseMessage);
                        
                        System.out.println("🤖 AI机器人回应 " + userName + " 的呼唤");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
            
            return true; // 是指令，返回true
        }
        
        // 检查是否是退出指令
        if (trimmedMessage.contains("@ai 退出") || 
            trimmedMessage.contains("@ai小助手 退出") || 
            trimmedMessage.contains("ai退出") ||
            trimmedMessage.contains("ai休息") ||
            trimmedMessage.contains("ai睡觉") ||
            trimmedMessage.equals("ai晚安")) {
            
            if (aiBotEnabled) {
                // 禁用AI机器人
                aiBotEnabled = false;
                
                // 停止定时器
                synchronized (timerLock) {
                    if (aiBotTimer != null) {
                        aiBotTimer.cancel();
                        aiBotTimer = null;
                    }
                }
                
                // 重置状态
                currentAiState = AiBotState.IDLE;
                currentQuestionIndex = -1;
                
                // 延迟3秒后发送退出回复，确保用户消息先显示
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        
                        ChatMessage disableMessage = new ChatMessage();
                        disableMessage.setType("CHAT");
                        disableMessage.setSender("🤖 AI小助手");
                        disableMessage.setSenderId("ai_bot");
                        disableMessage.setAvatar(generateAiBotAvatar());
                        disableMessage.setContent("😴 好的，" + userName + "，我去休息啦~\n💡 想我的时候发送 \"@ai\" 或 \"唤醒ai\" 就可以叫我回来哦！");
                        disableMessage.setOnlineCount(userService.getOnlineCount());
                        disableMessage.setTimestamp(System.currentTimeMillis());
                        messagingTemplate.convertAndSend("/topic/public", disableMessage);
                        
                        System.out.println("🤖 AI机器人被 " + userName + " 禁用");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            } else {
                // 延迟3秒后发送已休息回复
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                        
                        ChatMessage alreadyDisabledMessage = new ChatMessage();
                        alreadyDisabledMessage.setType("CHAT");
                        alreadyDisabledMessage.setSender("🤖 AI小助手");
                        alreadyDisabledMessage.setSenderId("ai_bot");
                        alreadyDisabledMessage.setAvatar(generateAiBotAvatar());
                        alreadyDisabledMessage.setContent("😴 我已经在休息啦，" + userName + "~\n💡 发送 \"@ai\" 可以唤醒我哦！");
                        alreadyDisabledMessage.setOnlineCount(userService.getOnlineCount());
                        alreadyDisabledMessage.setTimestamp(System.currentTimeMillis());
                        messagingTemplate.convertAndSend("/topic/public", alreadyDisabledMessage);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
            
            return true; // 是指令，返回true
        }
        
        return false; // 不是指令，返回false
    }
    

    
    /**
     * 处理AI简单问答互动
     */
    private boolean handleAiChat(String userMessage, String userName) {
        // 只有在AWAKE_WAITING_PROMPT状态下才进行问答互动
        if (currentAiState != AiBotState.AWAKE_WAITING_PROMPT) {
            return false;
        }
        
        String lowerMessage = userMessage.toLowerCase().trim();
        Random random = new Random();
        
        // 遍历问答回复列表
        for (String[] responseGroup : AI_CHAT_RESPONSES) {
            // responseGroup[0] 是关键词数组（实际上是第一个元素，后面的是回复）
            // 这里我们需要重新组织数据结构
        }
        
        // 简单的关键词匹配
        String response = null;
        
        // 问候
        if (lowerMessage.contains("你好") || lowerMessage.contains("您好") || 
            lowerMessage.equals("hello") || lowerMessage.equals("hi") || lowerMessage.equals("hey")) {
            String[] replies = {"👋 你好呀！有什么我可以帮你的吗？", "😊 嗨！很高兴见到你！", "✨ 你好！我是AI小助手，随时为你服务~"};
            response = replies[random.nextInt(replies.length)];
        }
        // 感谢
        else if (lowerMessage.contains("谢谢") || lowerMessage.contains("感谢") || 
                 lowerMessage.equals("thanks") || lowerMessage.contains("thank you")) {
            String[] replies = {"不客气！能帮到你就好~ 😊", "不用谢！这是我应该做的！✨", "哈哈，小事一桩！😄"};
            response = replies[random.nextInt(replies.length)];
        }
        // 再见
        else if (lowerMessage.contains("再见") || lowerMessage.contains("拜拜") || 
                 lowerMessage.equals("bye") || lowerMessage.equals("goodbye")) {
            String[] replies = {"再见！下次再聊哦~ 👋", "拜拜！期待下次见面！😊", "好的，再见！记得想我的时候@我哦~ ✨"};
            response = replies[random.nextInt(replies.length)];
        }
        // 身份
        else if (lowerMessage.contains("你是谁") || lowerMessage.contains("你是ai") || 
                 lowerMessage.contains("你是什么") || lowerMessage.contains("who are you")) {
            String[] replies = {"🤖 我是AI小助手，可以陪你聊天、出脑筋急转弯哦~", "✨ 我是聊天室的AI机器人，随时准备和你互动！", "😊 我是AI小助手，无聊的时候找我玩呀~"};
            response = replies[random.nextInt(replies.length)];
        }
        // 能力
        else if (lowerMessage.contains("你能做什么") || lowerMessage.contains("你会什么") || 
                 lowerMessage.contains("what can you do")) {
            String[] replies = {"💡 我可以出脑筋急转弯、陪你聊天解闷哦~", "🎯 我会出题考考你，也会回答你的问题！", "✨ 我能让你开心，也能让你思考，试试看？"};
            response = replies[random.nextInt(replies.length)];
        }
        
        if (response != null) {
            ChatMessage chatResponse = new ChatMessage();
            chatResponse.setType("CHAT");
            chatResponse.setSender("🤖 AI小助手");
            chatResponse.setSenderId("ai_bot");
            chatResponse.setAvatar(generateAiBotAvatar());
            chatResponse.setContent(response);
            chatResponse.setOnlineCount(userService.getOnlineCount());
            chatResponse.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/public", chatResponse);
            
            System.out.println("🤖 AI机器人回复用户: " + response);
            return true;
        }
        
        return false;
    }
    
    /**
     * 处理用户对AI机器人的回复
     */
    private void handleAiBotReply(String userMessage, String userName) {
        // 如果处于提问阶段或继续猜阶段，检测用户答案
        if (currentAiState == AiBotState.ASKED_QUESTION || currentAiState == AiBotState.CONTINUE_GUESS) {
            checkUserAnswer(userMessage, userName);
            return;
        }
            
        // 如果处于等待答案回复阶段，检测是否要公布答案
        if (currentAiState != AiBotState.WAITING_ANSWER) {
            // 如果在AWAITING_AUTO_EXIT状态下有用户消息，取消自动退出
            if (currentAiState == AiBotState.AWAITING_AUTO_EXIT) {
                currentAiState = AiBotState.IDLE;
                System.out.println("🤖 用户发言，取消自动退出");
            }
            return; // 只有在等待答案回复阶段才处理
        }
            
        String trimmedMessage = userMessage.trim().toLowerCase();
        Random random = new Random();
            
        // 更精确地检测用户是否回复"要"（肯定词）
        boolean wantAnswer = trimmedMessage.equals("要") || 
                            trimmedMessage.equals("yes") || 
                            trimmedMessage.equals("y") ||
                            trimmedMessage.equals("好") ||
                            trimmedMessage.equals("好的") ||
                            trimmedMessage.equals("是的") ||
                            trimmedMessage.contains("想要") ||
                            trimmedMessage.contains("公布") ||
                            trimmedMessage.contains("告诉") ||
                            trimmedMessage.equals("ok");
            
        // 更精确地检测用户是否回复"不要"（否定词）
        boolean dontWantAnswer = trimmedMessage.equals("不要") || 
                                trimmedMessage.equals("no") || 
                                trimmedMessage.equals("n") ||
                                trimmedMessage.equals("别") ||
                                trimmedMessage.equals("不用") ||
                                trimmedMessage.contains("不想") ||
                                trimmedMessage.contains("不公布") ||
                                trimmedMessage.contains("继续猜");
            
        if (wantAnswer) {
            // 用户想要答案，先调皮一下再公布
            ChatMessage teaserMessage = new ChatMessage();
            teaserMessage.setType("CHAT");
            teaserMessage.setSender("🤖 AI小助手");
            teaserMessage.setSenderId("ai_bot");
            teaserMessage.setAvatar(generateAiBotAvatar());
            teaserMessage.setContent("😏 好吧好吧，看在你这么诚恳的份上...那就告诉你吧！");
            teaserMessage.setOnlineCount(userService.getOnlineCount());
            teaserMessage.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/public", teaserMessage);
                
            // 延迟3秒后公布答案
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    sendFinalAnswerPhase();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
                
        } else if (dontWantAnswer) {
            // 随机选择调皮回复，并替换用户名
            String playfulTemplate = PLAYFUL_MESSAGES[random.nextInt(PLAYFUL_MESSAGES.length)];
            String playfulMessage = playfulTemplate.replace("{user}", userName);
                
            ChatMessage playfulMsg = new ChatMessage();
            playfulMsg.setType("CHAT");
            playfulMsg.setSender("🤖 AI小助手");
            playfulMsg.setSenderId("ai_bot");
            playfulMsg.setAvatar(generateAiBotAvatar());
            playfulMsg.setContent(playfulMessage);
            playfulMsg.setOnlineCount(userService.getOnlineCount());
            playfulMsg.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/public", playfulMsg);
                
            // 不重置状态，而是进入继续猜的状态
            currentAiState = AiBotState.CONTINUE_GUESS;
            lastMessageTime = System.currentTimeMillis(); // 重置计时器
                
            // 延迟3秒后发送鼓励继续猜的消息
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                        
                    String[] encourageMessages = {
                        "💪 好吧，那再给你一次机会！继续猜猜看~",
                        "🤔 真的不想知道吗？那再想想吧，我相信你能猜出来！",
                        "✨ 没关系，多思考一下更有趣！再来试试？",
                        "🎯 好的，那我再等等，说不定你一会儿就猜到了呢~",
                        "😄 有骨气！那就靠自己的智慧来解开谜题吧！"
                    };
                        
                    ChatMessage encourageMsg = new ChatMessage();
                    encourageMsg.setType("CHAT");
                    encourageMsg.setSender("🤖 AI小助手");
                    encourageMsg.setSenderId("ai_bot");
                    encourageMsg.setAvatar(generateAiBotAvatar());
                    encourageMsg.setContent(encourageMessages[random.nextInt(encourageMessages.length)]);
                    encourageMsg.setOnlineCount(userService.getOnlineCount());
                    encourageMsg.setTimestamp(System.currentTimeMillis());
                    messagingTemplate.convertAndSend("/topic/public", encourageMsg);
                        
                    System.out.println("🤖 AI机器人鼓励用户继续猜答案");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
                
            System.out.println("🤖 用户拒绝公布答案，进入继续猜状态");
        } else {
            // 用户的回复不明确，提示用户明确回复
            String[] unclearResponses = {
                "🤔 我没太听明白呢~ 请回复'要'或'不要'哦！",
                "😅 你是想要答案还是想继续猜呢？回复'要'或'不要'告诉我吧~",
                "💡 小提示：回复'要'我就公布答案，回复'不要'你就继续猜~"
            };
                
            ChatMessage unclearMsg = new ChatMessage();
            unclearMsg.setType("CHAT");
            unclearMsg.setSender("🤖 AI小助手");
            unclearMsg.setSenderId("ai_bot");
            unclearMsg.setAvatar(generateAiBotAvatar());
            unclearMsg.setContent(unclearResponses[random.nextInt(unclearResponses.length)]);
            unclearMsg.setOnlineCount(userService.getOnlineCount());
            unclearMsg.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/public", unclearMsg);
                
            System.out.println("🤖 用户回复不明确: " + userMessage);
        }
    }
    
    /**
     * 检查用户答案是否正确
     */
    private void checkUserAnswer(String userMessage, String userName) {
        if (currentQuestionIndex < 0) {
            return;
        }
        
        String correctAnswer = aiBotService.getAnswerByIndex(currentQuestionIndex);
        String userAnswer = userMessage.trim();
        
        // 简单判断：用户答案包含正确答案或正确答案包含用户答案
        boolean isCorrect = userAnswer.contains(correctAnswer) || correctAnswer.contains(userAnswer);
        
        Random random = new Random();
        
        if (isCorrect) {
            // 回答正确
            String[] correctResponses = {
                "🎉 哇塞，" + userName + " 太厉害了！完全正确！",
                "✨ 答对了！" + userName + " 真是个聪明的小天才！",
                "👏 恭喜 " + userName + "！答案就是【" + correctAnswer + "】，你太棒了！",
                "🌟 没错没错！" + userName + " 一语中的，佩服佩服！"
            };
            
            ChatMessage correctMsg = new ChatMessage();
            correctMsg.setType("CHAT");
            correctMsg.setSender("🤖 AI小助手");
            correctMsg.setSenderId("ai_bot");
            correctMsg.setAvatar(generateAiBotAvatar());
            correctMsg.setContent(correctResponses[random.nextInt(correctResponses.length)]);
            correctMsg.setOnlineCount(userService.getOnlineCount());
            correctMsg.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/public", correctMsg);
            
            System.out.println("🤖 用户 " + userName + " 回答正确 (问题索引: " + currentQuestionIndex + "): " + userAnswer);
            
            // 重置问题索引，进入等待自动退出状态
            currentQuestionIndex = -1;
            currentAiState = AiBotState.AWAITING_AUTO_EXIT;
            lastMessageTime = System.currentTimeMillis(); // 重置计时器
        } else {
            // 回答错误
            String[] wrongResponses;
            
            // 根据当前状态选择不同的错误提示
            if (currentAiState == AiBotState.CONTINUE_GUESS) {
                wrongResponses = new String[] {
                    "❌ 不对哦，" + userName + "！再想想看~",
                    "😅 还是不对呢，" + userName + "。加油，你可以的！",
                    "🤔 嗯...这个答案不对，" + userName + " 再猜猜？",
                    "💭 差一点点，" + userName + " 继续努力！"
                };
            } else {
                wrongResponses = new String[] {
                    "❌ 哎呀，" + userName + " 的答案不对哦~ 再想想？",
                    "😅 不太对呢，" + userName + "。提示：答案是两个字以内",
                    "🤔 嗯...这个答案有点偏差，" + userName + " 要不要再猜猜？",
                    "💭 接近了但还不够准确，" + userName + " 继续加油！"
                };
            }
            
            ChatMessage wrongMsg = new ChatMessage();
            wrongMsg.setType("CHAT");
            wrongMsg.setSender("🤖 AI小助手");
            wrongMsg.setSenderId("ai_bot");
            wrongMsg.setAvatar(generateAiBotAvatar());
            wrongMsg.setContent(wrongResponses[random.nextInt(wrongResponses.length)]);
            wrongMsg.setOnlineCount(userService.getOnlineCount());
            wrongMsg.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/public", wrongMsg);
            
            System.out.println("🤖 用户 " + userName + " 回答错误 (问题索引: " + currentQuestionIndex + "): " + userAnswer + " (正确答案: " + correctAnswer + ")");
        }
    }
    
    /**
     * 生成AI机器人头像
     */
    private String generateAiBotAvatar() {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='100' height='100' viewBox='0 0 100 100'>" +
                    "<defs>" +
                    "<linearGradient id='aiGrad' x1='0%' y1='0%' x2='100%' y2='100%'>" +
                    "<stop offset='0%' style='stop-color:#667eea;stop-opacity:1' />" +
                    "<stop offset='100%' style='stop-color:#764ba2;stop-opacity:1' />" +
                    "</linearGradient>" +
                    "</defs>" +
                    "<rect width='100' height='100' fill='url(#aiGrad)' rx='15'/>" +
                    "<text x='50' y='65' font-size='50' text-anchor='middle' fill='white'>🤖</text>" +
                    "</svg>";
        
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes());
    }
}
