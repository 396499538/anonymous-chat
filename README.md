# 匿名聊天室

基于 Spring Boot + WebSocket + HTML5 的实时匿名聊天室

## 功能特性

- **匿名聊天**: 进入自动生成随机汉字昵称和表情头像
- **实时通信**: 基于 WebSocket/SockJS/STOMP 的实时消息推送
- **用户列表**: 左侧显示当前在线用户
- **富文本输入**: 支持多行输入和快捷键发送
- **QQ表情**: 100+ 常用表情选择
- **响应式布局**: 适配桌面端和移动端
- **消息类型**: 支持普通消息、系统消息（加入/离开提示）

## 技术栈

- **后端**: Spring Boot 3.2.0 + WebSocket + STOMP
- **前端**: HTML5 + CSS3 + JavaScript + jQuery + SockJS + STOMP.js
- **构建**: Maven

## 项目结构

```
anonymous-chat/
├── pom.xml                          # Maven 配置
├── README.md
└── src/
    └── main/
        ├── java/com/chat/
        │   ├── ChatApplication.java          # 启动类
        │   ├── config/
        │   │   ├── WebSocketConfig.java       # WebSocket 配置
        │   │   ├── WebSocketEventListener.java # WebSocket 事件监听
        │   │   └── CorsConfig.java            # 跨域配置
        │   ├── controller/
        │   │   ├── ChatController.java        # WebSocket 消息控制器
        │   │   ├── ApiController.java         # REST API 控制器
        │   │   └── PageController.java        # 页面控制器
        │   ├── model/
        │   │   ├── ChatMessage.java           # 聊天消息模型
        │   │   └── UserInfo.java             # 用户信息模型
        │   └── service/
        │       └── UserService.java          # 用户服务
        └── resources/
            ├── application.properties        # 应用配置
            └── templates/
                └── chat.html                 # 聊天室页面
```

## 快速开始

### 1. 确保环境

- JDK 17+
- Maven 3.6+

### 2. 编译运行

```bash
# 编译项目
mvn clean package -DskipTests

# 运行项目
mvn spring-boot:run
```

### 3. 访问聊天室

打开浏览器访问: http://localhost:8080

## 使用说明

1. **进入聊天**: 打开页面后自动创建匿名身份（随机汉字昵称 + 表情头像）
2. **发送消息**: 在输入框输入内容，点击发送按钮或按 Enter 键发送
3. **插入表情**: 点击表情图标打开表情选择器，点击表情插入到输入框
4. **查看用户**: 左侧栏显示当前所有在线用户
5. **多设备**: 可在多个浏览器/标签页打开，体验多人聊天

## WebSocket API

### 连接端点
- `/ws` - WebSocket 连接端点（支持 SockJS）
- `/ws` - 原始 WebSocket 连接

### 消息主题
- `/topic/public` - 公共消息频道（所有人可见）
- `/topic/users` - 用户列表更新频道
- `/user/queue/private` - 私有消息频道（用户信息）

### 消息格式
```json
{
  "type": "CHAT|JOIN|LEAVE|SYSTEM",
  "sender": "用户昵称",
  "senderId": "用户ID",
  "content": "消息内容",
  "avatar": "头像URL",
  "timestamp": 1234567890,
  "onlineCount": 10
}
```

## 许可证

MIT License
