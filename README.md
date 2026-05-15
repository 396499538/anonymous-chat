# 匿名聊天室

基于 Spring Boot + WebSocket + HTML5 的实时匿名聊天室，支持 AI 小助手互动

## ✨ 功能特性

### 核心功能
- **匿名聊天**: 进入自动生成随机汉字昵称和表情头像
- **实时通信**: 基于 WebSocket/SockJS/STOMP 的实时消息推送
- **用户列表**: 左侧显示当前在线用户及状态
- **富文本输入**: 支持多行输入、快捷键发送（Enter/Ctrl+Enter）
- **QQ表情**: 100+ 常用表情选择器
- **响应式布局**: 完美适配桌面端、平板和移动端
- **消息类型**: 支持普通消息、系统消息（加入/离开提示）、@提醒
- **会话恢复**: 刷新页面或重启服务器后自动恢复用户身份

### AI 小助手
- **智能唤醒**: 发送 `@ai` 唤醒 AI 小助手
- **答题互动**: AI 出题，用户回答，自动判断对错
- **定时触发**: 60秒无人说话时自动出脑筋急转弯
- **退出指令**: 发送 `@ai 退出` 让 AI 休息
- **趣味互动**: 多种题型和幽默回复

### 用户体验
- **@用户提醒**: 支持 @其他用户，高亮显示
- **重置身份**: 一键重置，生成新的匿名身份
- **移动端优化**: 汉堡菜单、侧边栏滑动、触摸友好
- **优雅动画**: 流畅的过渡效果和交互动画

## 🛠️ 技术栈

### 后端
- **框架**: Spring Boot 2.7.18
- **WebSocket**: spring-boot-starter-websocket + STOMP
- **模板引擎**: Thymeleaf
- **JSON处理**: Jackson
- **代码简化**: Lombok
- **构建工具**: Maven

### 前端
- **核心**: HTML5 + CSS3 + JavaScript (ES6+)
- **库**: jQuery 3.x
- **WebSocket**: SockJS + STOMP.js
- **图标**: Font Awesome 6
- **样式**: 自定义 CSS + Flexbox 布局

## 📁 项目结构

```
anonymous-chat/
├── pom.xml                          # Maven 配置文件
├── README.md                        # 项目说明文档
├── package.bat                      # Windows 打包脚本
├── run.bat                          # Windows 运行脚本
└── src/
    └── main/
        ├── java/com/chat/
        │   ├── ChatApplication.java          # Spring Boot 启动类
        │   ├── config/
        │   │   ├── WebSocketConfig.java       # WebSocket 配置类
        │   │   ├── WebSocketEventListener.java # WebSocket 事件监听器
        │   │   └── CorsConfig.java            # CORS 跨域配置
        │   ├── controller/
        │   │   ├── ChatController.java        # WebSocket 消息控制器
        │   │   ├── ApiController.java         # REST API 控制器
        │   │   └── PageController.java        # 页面路由控制器
        │   ├── model/
        │   │   ├── ChatMessage.java           # 聊天消息数据模型
        │   │   └── UserInfo.java              # 用户信息数据模型
        │   └── service/
        │       ├── UserService.java           # 用户管理服务
        │       └── AiBotService.java          # AI 小助手服务
        └── resources/
            ├── application.properties         # 应用配置文件
            ├── static/                        # 静态资源
            │   ├── css/
            │   │   └── chat.css               # 聊天室样式文件
            │   └── js/
            │       └── chat.js                # 聊天室 JavaScript 逻辑
            └── templates/
                └── chat.html                  # 聊天室主页面
```

## 🚀 快速开始

### 1. 环境要求

- **JDK**: 11 或更高版本
- **Maven**: 3.6 或更高版本
- **浏览器**: 支持 WebSocket 的现代浏览器（Chrome、Firefox、Edge、Safari）

### 2. 编译运行

#### 方式一：使用 Maven 命令

```bash
# 清理并打包（跳过测试）
mvn clean package -DskipTests

# 直接运行
mvn spring-boot:run
```

#### 方式二：使用脚本（Windows）

```bash
# 打包
package.bat

# 运行
run.bat
```

### 3. 访问聊天室

打开浏览器访问: **http://localhost:10000**

可以在多个浏览器窗口或标签页中打开，体验多人聊天效果。

## 📖 使用说明

### 基本操作

1. **进入聊天**: 打开页面后自动创建匿名身份（随机汉字昵称 + 表情头像）
2. **发送消息**: 
   - 在输入框输入内容
   - 点击发送按钮或按 `Enter` 键发送
   - 按 `Ctrl + Enter` 换行
3. **插入表情**: 点击表情图标 😊 打开表情选择器，点击表情插入到输入框
4. **查看用户**: 左侧栏显示当前所有在线用户及其状态
5. **@提醒**: 输入 `@` 符号，选择要提醒的用户
6. **重置身份**: 点击右上角的 🔄 按钮，生成新的匿名身份

### AI 小助手使用

1. **唤醒 AI**: 发送 `@ai` 或 `@AI` 唤醒 AI 小助手
2. **答题互动**: 
   - AI 会出脑筋急转弯或趣味问题
   - 直接回答即可，AI 会自动判断对错
   - 答对有奖励哦！
3. **退出 AI**: 发送 `@ai 退出` 或 `@ai 休息` 让 AI 暂时离开
4. **自动触发**: 如果 60 秒内无人说话，AI 会自动出题活跃气氛

### 移动端使用

- **打开菜单**: 点击左上角 ☰ 汉堡菜单按钮
- **关闭菜单**: 点击菜单外区域或右上角 ✕ 按钮
- **触摸友好**: 所有按钮和交互都针对触摸优化

## 🔌 WebSocket API

### 连接端点

- **WebSocket**: `/ws` - WebSocket 连接端点（支持 SockJS 降级）
- **STOMP**: 基于 STOMP 协议的消息通信

### 消息主题（Topics）

| 主题 | 说明 | 类型 |
|------|------|------|
| `/topic/public` | 公共消息频道 | 广播 |
| `/topic/users` | 在线用户列表更新 | 广播 |
| `/user/queue/private` | 私有消息（用户信息） | 点对点 |

### 消息发送端点

| 端点 | 说明 |
|------|------|
| `/app/join` | 用户加入聊天室 |
| `/app/chat` | 发送聊天消息 |
| `/app/getUsers` | 获取在线用户列表 |

### 消息格式

#### 1. 聊天消息 (ChatMessage)

```json
{
  "type": "CHAT",              // 消息类型: CHAT, JOIN, LEAVE, SYSTEM
  "sender": "张三",             // 发送者昵称
  "senderId": "abc123",        // 发送者ID
  "content": "大家好！",        // 消息内容
  "avatar": "data:image...",   // 头像 URL (Base64 SVG)
  "timestamp": 1234567890,     // 时间戳 (毫秒)
  "onlineCount": 10,           // 在线人数
  "mentionedUserIds": []       // 被@的用户ID列表
}
```

#### 2. 用户信息 (UserInfo)

```json
{
  "id": "abc123",              // 用户唯一ID
  "nickname": "张三",           // 用户昵称
  "avatar": "data:image..."    // 头像 URL (Base64 SVG)
}
```

### 消息类型说明

- **CHAT**: 普通聊天消息
- **JOIN**: 用户加入提示
- **LEAVE**: 用户离开提示
- **SYSTEM**: 系统消息（如 AI 出题、答案公布等）

## 📦 打包部署

### 本地开发

```bash
# 直接运行（热重载）
mvn spring-boot:run
```

### 生产环境打包

```bash
# 清理并打包（跳过测试）
mvn clean package -DskipTests

# 生成的 JAR 文件位于 target/ 目录
# anonymous-chat-1.0.0.jar
```

### 运行 JAR 包

```bash
# 默认端口 (10000)
java -jar target/anonymous-chat-1.0.0.jar

# 指定端口
java -jar target/anonymous-chat-1.0.0.jar --server.port=9090

# 后台运行 (Linux/Mac)
nohup java -jar target/anonymous-chat-1.0.0.jar > app.log 2>&1 &

# 后台运行 (Windows PowerShell)
Start-Process java -ArgumentList "-jar","target/anonymous-chat-1.0.0.jar" -NoNewWindow
```

### Docker 部署（可选）

创建 `Dockerfile`:

```dockerfile
FROM openjdk:11-jdk-slim

WORKDIR /app

COPY target/anonymous-chat-1.0.0.jar app.jar

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "app.jar"]
```

构建和运行：

```bash
# 构建镜像
docker build -t chat-room .

# 运行容器
docker run -d -p 10000:10000 --name chat-room chat-room

# 查看日志
docker logs -f chat-room
```

## ⚙️ 配置说明

### application.properties

主要配置项：

```properties
# 服务器端口
server.port=10000

# WebSocket 端点
spring.websocket.path=/ws

# Thymeleaf 配置
spring.thymeleaf.cache=false
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html

# 静态资源配置
spring.web.resources.static-locations=classpath:/static/
```

## ❓ 常见问题

### 1. 打包时找不到符号？

确保 Lombok 依赖正确配置，并且 IDE 已启用注解处理器。

**IntelliJ IDEA 用户：**
- 安装 Lombok 插件
- File → Settings → Build → Compiler → Annotation Processors → ✅ Enable annotation processing

### 2. 端口被占用？

修改端口或关闭占用程序：

```bash
# 使用其他端口
java -jar target/anonymous-chat-1.0.0.jar --server.port=8081
```

### 3. WebSocket 连接失败？

- 检查防火墙设置
- 确认浏览器支持 WebSocket
- 查看浏览器控制台错误信息
- 检查服务器日志

### 4. 刷新页面后用户丢失？

这是正常行为。如需保持会话，可以：
- 使用 localStorage 保存用户信息（已实现）
- 刷新页面时自动恢复身份

## 📝 开发说明

### 添加新功能

1. **后端**: 在 `ChatController.java` 中添加新的 `@MessageMapping`
2. **前端**: 在 `chat.js` 中调用 `stompClient.send()`
3. **模型**: 如需新字段，在 `ChatMessage.java` 中添加

### 自定义 AI 题目

编辑 `AiBotService.java` 中的题目列表：

```java
private static final String[] QUESTIONS = {
    "你的新题目...",
    // ...
};

private static final String[] ANSWERS = {
    "对应的答案...",
    // ...
};
```

### 样式定制

修改 `static/css/chat.css` 中的 CSS 变量：

```css
:root {
    --primary-color: #667eea;     /* 主题色 */
    --secondary-color: #764ba2;   /* 辅助色 */
    --sidebar-bg: #2c3e50;        /* 侧边栏背景 */
    /* ... */
}
```

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

## 👨‍💻 作者

- **赵瑞宏**

## 🙏 致谢

感谢以下开源项目：

- [Spring Boot](https://spring.io/projects/spring-boot)
- [SockJS](https://github.com/sockjs/sockjs-client)
- [STOMP.js](https://github.com/jmesnil/stomp-websocket)
- [jQuery](https://jquery.com/)
- [Font Awesome](https://fontawesome.com/)

---

⭐ 如果这个项目对你有帮助，请给个 Star 支持一下！
