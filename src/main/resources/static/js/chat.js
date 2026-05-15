// ==================== 配置 ====================
// WebSocket URL - 使用原生 WebSocket，根据当前页面协议自动选择 ws 或 wss
const WS_PROTOCOL = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
const WS_URL = WS_PROTOCOL + '//' + window.location.host + '/ws';

// QQ表情列表
const QQ_EMOJIS = [
    '😀', '😁', '😂', '🤣', '😃', '😄', '😅', '😆', '😉', '😊',
    '😋', '😎', '😍', '😘', '🥰', '😗', '😙', '😚', '🙂', '🤗',
    '🤩', '🤔', '🤨', '😐', '😑', '😶', '🙄', '😏', '😣', '😥',
    '😮', '🤐', '😯', '😪', '😫', '🥱', '😴', '😌', '😛', '😜',
    '😝', '🤤', '😒', '😓', '😔', '😕', '🙃', '🤑', '😲', '🙁',
    '😖', '😞', '😟', '😤', '😢', '😭', '😦', '😧', '😨', '😩',
    '🤯', '😰', '😱', '🥵', '🥶', '😳', '🤪', '😵', '🥴', '😠',
    '😡', '🤬', '😷', '🤒', '🤕', '🤢', '🤮', '🤧', '😵', '💀',
    '💩', '🤡', '👹', '👺', '👻', '👽', '👾', '🤖', '😺', '😸',
    '😹', '😻', '😼', '😽', '🙀', '😿', '😾', '🙈', '🙉', '🙊',
    '💋', '💌', 'ERING', '💎', '💐', '💑', '💒', '💓', '💔', '💕',
    '💖', '💗', '💘', '💙', '💚', '💛', '💜', '💝', '💞', '💟',
    '👍', '👎', '👏', '🙌', '👐', '🤲', '🤝', '🙏', '💪', '🦾',
    '❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍', '🤎', '💔'
];

// ==================== 全局变量 ====================
let stompClient = null;
let currentUser = null;
let isConnected = false;
let onlineUsers = []; // 在线用户列表
let mentionSearchText = ''; // @搜索文本
let mentionSelectedIndex = -1; // @选择索引

// ==================== 初始化 ====================
$(document).ready(function() {
    initEmojiPicker();

    // 先测试 API 是否可用
    testApiConnection();

    // 监听回车键发送消息
    $('#messageInput').keypress(function(e) {
        if (e.which === 13 && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    // 监听输入框的@符号，显示用户选择提示
    $('#messageInput').on('input', function() {
        handleMentionInput($(this));
    });

    // 监听键盘事件，支持上下键选择用户
    $('#messageInput').keydown(function(e) {
        const $suggestions = $('#mentionSuggestions');
        if ($suggestions.hasClass('active')) {
            if (e.which === 38) { // 上箭头
                e.preventDefault();
                navigateMentionList(-1);
            } else if (e.which === 40) { // 下箭头
                e.preventDefault();
                navigateMentionList(1);
            } else if (e.which === 9 || e.which === 13) { // Tab 或 Enter
                if (mentionSelectedIndex >= 0) {
                    e.preventDefault();
                    selectMentionUser(mentionSelectedIndex);
                }
            } else if (e.which === 27) { // Esc
                hideMentionSuggestions();
            }
        }
    });

    // 点击页面其他地方关闭表情选择器
    $(document).click(function(e) {
        const $emojiPicker = $('#emojiPicker');
        const $inputArea = $('.input-area');

        // 如果点击的不是 input-area 区域，则关闭表情选择器
        if (!$inputArea.is(e.target) && $inputArea.has(e.target).length === 0) {
            $emojiPicker.removeClass('active');
        }
    });

    // 为表情按钮添加点击事件，阻止冒泡
    $('#emojiBtn').click(function(e) {
        e.stopPropagation();
    });

    // 为表情选择器添加点击事件，阻止冒泡
    $('#emojiPicker').click(function(e) {
        e.stopPropagation();
    });

    // 页面关闭或刷新时主动断开连接
    window.addEventListener('beforeunload', function() {
        if (stompClient && isConnected) {
            stompClient.disconnect();
        }
    });

    // 页面隐藏时（切换标签页）也断开连接
    document.addEventListener('visibilitychange', function() {
        if (document.visibilityState === 'hidden') {
            // 页面隐藏，保持连接
        } else {
            // 如果连接已断开且 currentUser 存在，尝试重连
            if (!isConnected && currentUser) {
                // 从 localStorage 获取用户 ID 用于重连
                let cachedUserId = null;
                const savedUser = localStorage.getItem('chat_user');
                if (savedUser) {
                    try {
                        const parsedUser = JSON.parse(savedUser);
                        if (parsedUser && parsedUser.id) {
                            cachedUserId = parsedUser.id;
                        }
                        currentUser = parsedUser;
                    } catch (e) {
                        // 解析失败，忽略
                    }
                }
                connectWebSocket(cachedUserId);
            }
        }
    });
});

// ==================== 创建用户弹窗 ====================
let selectedAvatar = null;

function showCreateUserDialog() {
    $('#createUserDialog').fadeIn(300);

    // 生成随机昵称
    refreshNickname();

    // 生成头像选项
    generateAvatarOptions();
}

function refreshNickname() {
    $.get('/api/generateNickname', function(response) {
        $('#nicknameInput').val(response.nickname);
    }).fail(function() {
        // 如果 API 失败，使用默认昵称
        $('#nicknameInput').val('聊天用户');
    });
}

function refreshAvatars() {
    // 重新生成头像选项
    generateAvatarOptions();

    // 添加旋转动画效果
    const $btn = $('.refresh-avatar-btn i');
    $btn.addClass('fa-spin');
    setTimeout(() => {
        $btn.removeClass('fa-spin');
    }, 500);
}

function generateAvatarOptions() {
    const $selector = $('#avatarSelector');
    $selector.empty();

    // 更多的颜色和表情选项
    const allColors = ['#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
        '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E9',
        '#FF9FF3', '#54A0FF', '#5F27CD', '#01A3A4', '#F368E0',
        '#FF6348', '#7BED9F', '#70A1FF', '#FFA502', '#2ED573'];
    const allEmojis = ['😀', '😎', '🤗', '😇', '🥰', '🤩', '😋', '🤭', '🙃', '😊',
        '😜', '🤪', '😝', '🤑', '🤓', '😈', '👻', '👽', '🤖', '💩'];

    // 随机打乱数组
    const shuffleArray = (array) => {
        const shuffled = [...array];
        for (let i = shuffled.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
        }
        return shuffled;
    };

    // 随机选择10个颜色和10个表情
    const colors = shuffleArray(allColors).slice(0, 10);
    const emojis = shuffleArray(allEmojis).slice(0, 10);

    for (let i = 0; i < 10; i++) {
        const color = colors[i];
        const emoji = emojis[i];
        const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='100' height='100' viewBox='0 0 100 100'>
                    <rect width='100' height='100' fill='${color}' rx='15'/>
                    <text x='50' y='65' font-size='50' text-anchor='middle' fill='white'>${emoji}</text>
                </svg>`;

        // 使用 encodeURIComponent 和 btoa 配合处理 Unicode 字符
        const avatarData = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svg)));

        const $avatar = $(`<img class="avatar-option" src="${avatarData}" data-index="${i}" alt="Avatar ${i+1}">`);
        $avatar.click(function() {
            $('.avatar-option').removeClass('selected');
            $(this).addClass('selected');
            selectedAvatar = avatarData;
        });

        $selector.append($avatar);
    }

    // 随机选择一个头像
    const randomIndex = Math.floor(Math.random() * 10);
    const $randomAvatar = $selector.find(`.avatar-option[data-index="${randomIndex}"]`);
    $randomAvatar.addClass('selected');
    selectedAvatar = $randomAvatar.attr('src');
}

function createUser() {
    const nickname = $('#nicknameInput').val().trim();

    if (!nickname) {
        showNotification('请输入昵称', 'warning', '提示');
        return;
    }

    if (!selectedAvatar) {
        showNotification('请选择头像', 'warning', '提示');
        return;
    }

    // 禁用按钮，防止重复点击
    $('.create-user-btn').prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> 创建中...');

    // 发送请求到后端
    $.ajax({
        url: '/api/createCustomUser',
        method: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            nickname: nickname,
            avatar: selectedAvatar
        }),
        success: function(user) {
            // 保存用户信息到 localStorage
            localStorage.setItem('chat_user', JSON.stringify(user));
            currentUser = user;

            // 恢复按钮状态
            $('.create-user-btn').prop('disabled', false).html('<i class="fas fa-check-circle"></i> 创建用户');

            // 关闭弹窗
            $('#createUserDialog').fadeOut(300);

            // 连接 WebSocket
            connectWebSocket(user.id);

            showNotification('欢迎, ' + nickname + '!', 'success', '创建成功');
        },
        error: function(xhr) {
            showNotification('创建用户失败，请重试', 'error', '错误');
            $('.create-user-btn').prop('disabled', false).html('<i class="fas fa-check-circle"></i> 创建用户');
        }
    });
}

// ==================== API 连接测试 ====================
function testApiConnection() {
    console.log('Testing API connection...');
    // 先断开旧的连接（如果有）
    if (stompClient && isConnected) {
        stompClient.disconnect();
        stompClient = null;
        isConnected = false;
    }
    // 检查是否有缓存的用户 ID（用于会话恢复）
    let cachedUserId = null;
    const savedUser = localStorage.getItem('chat_user');
    if (savedUser) {
        try {
            const parsedUser = JSON.parse(savedUser);
            if (parsedUser && parsedUser.id) {
                cachedUserId = parsedUser.id;
                currentUser = parsedUser;
                console.log("当前用户 currentUser: ", currentUser);
                // 有缓存用户，直接连接 WebSocket
                connectWebSocket(cachedUserId);
                return;
            }
        } catch (e) {
            localStorage.removeItem('chat_user');
        }
    }

    // 没有缓存用户，显示创建用户弹窗
    showCreateUserDialog();
}

// ==================== WebSocket连接 ====================
function connectWebSocket(cachedUserId) {
    console.log('========== 开始连接 WebSocket ==========');
    console.log('传入的 cachedUserId:', cachedUserId);
    console.log('当前的 currentUser:', currentUser);

    // 如果已经有活跃连接，先断开
    if (stompClient && isConnected) {
        stompClient.disconnect();
        stompClient = null;
        isConnected = false;
    }

    // 使用 SockJS + STOMP (提供更好的兼容性)
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    // 禁用 STOMP 的调试日志
    stompClient.debug = null;

    // 设置 login header：优先使用缓存的用户 ID，否则生成临时 ID
    const headers = {};
    if (cachedUserId) {
        headers.login = cachedUserId;
        console.log('使用 cachedUserId 作为 login:', cachedUserId);
    } else {
        // 生成一个唯一的请求 ID，用于接收用户信息
        const requestId = 'req_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
        headers.login = requestId;
        console.log('无 cachedUserId，使用 requestId 作为 login:', requestId);
    }
    console.log('STOMP headers:', headers);

    stompClient.connect(headers, function(frame) {
        console.log('✓ STOMP 连接成功');
        isConnected = true;

        // 订阅公共消息频道
        stompClient.subscribe('/topic/public', function(message) {
            const msg = JSON.parse(message.body);
            handleMessage(msg);
        });

        // 订阅用户列表更新 - 添加防抖处理，避免频繁更新
        let userListUpdateTimer = null;
        stompClient.subscribe('/topic/users', function(message) {
            const users = JSON.parse(message.body);

            // 清除之前的定时器
            if (userListUpdateTimer) {
                clearTimeout(userListUpdateTimer);
            }

            // 延迟50ms更新，避免短时间内多次更新
            userListUpdateTimer = setTimeout(function() {
                updateUserList(users);
            }, 50);
        });

        // 订阅私有消息（用户信息）- 这是设置 currentUser 的唯一来源
        // STOMP 会自动将 /user/queue/private 转换为 /user/{login}/queue/private
        stompClient.subscribe('/user/queue/private', function(message) {
            console.log('========== 收到私有消息 ==========');
            const userData = JSON.parse(message.body);
            console.log('后端返回的用户信息:', userData);
            console.log('当前的 currentUser:', currentUser);

            // 检查是否与当前用户不同
            if (currentUser && currentUser.id !== userData.id) {
                console.warn('⚠ 用户ID不匹配！');
                console.warn('  localStorage 中的用户ID:', currentUser.id);
                console.warn('  后端返回的用户ID:', userData.id);
                console.warn('  这可能导致消息判断错误！');

                // 如果用户ID发生变化，说明是新的会话，需要完全刷新
                $('#messagesContainer').html(`
                            <div class="empty-state">
                                <i class="fas fa-comment-dots"></i>
                                <p>欢迎来到匿名聊天室<br>开始聊天吧！</p>
                            </div>
                        `);
            } else if (currentUser && currentUser.id === userData.id) {
                console.log('✓ 用户ID匹配，确认是同一用户');
            } else {
                console.log('✓ 首次设置 currentUser');
            }

            // ★ 关键：只有在这里才设置 currentUser（从后端权威数据源）
            currentUser = userData;
            console.log('✓ currentUser 已设置为:', currentUser.nickname, '(', currentUser.id, ')');

            // 同步更新 localStorage
            try {
                localStorage.setItem('chat_user', JSON.stringify(userData));
                console.log('✓ 已同步到 localStorage');

                // 显示欢迎通知
                showNotification('欢迎, ' + userData.nickname + '!', 'success', '连接成功');
            } catch (e) {
                console.error('✗ 更新 localStorage 失败:', e);
            }

            console.log('====================================');

            // 收到用户信息后，立即请求最新的用户列表
            setTimeout(function() {
                if (stompClient && isConnected) {
                    stompClient.send("/app/getUsers", {}, JSON.stringify({}));
                }
            }, 100);
        });

        // 加入聊天室（在连接成功后发送）
        // 如果有缓存的用户 ID，传递它以便后端恢复用户
        const joinMessage = {};
        if (cachedUserId && currentUser) {
            // 传递完整的用户信息，以便服务器重启后能重建用户
            joinMessage.senderId = cachedUserId;
            joinMessage.sender = currentUser.nickname;
            joinMessage.avatar = currentUser.avatar;
            console.log('发送 join 消息，携带完整用户信息:', {
                senderId: cachedUserId,
                sender: currentUser.nickname,
                avatar: currentUser.avatar ? '已提供' : '未提供'
            });
        } else if (cachedUserId) {
            // 只有 userId，没有 currentUser（异常情况）
            joinMessage.senderId = cachedUserId;
            console.log('发送 join 消息，仅携带 senderId:', cachedUserId);
        } else {
            console.log('发送 join 消息，不携带用户信息（后端将创建新用户）');
        }
        stompClient.send("/app/join", {}, JSON.stringify(joinMessage));

    }, function(error) {
        console.error('✗ STOMP 连接失败:', error);
        isConnected = false;
        setTimeout(connectWebSocket, 5000);
    });

    // 连接关闭时重连
    socket.onclose = function() {
        console.log('WebSocket 连接关闭');
        isConnected = false;

        // 如果 currentUser 存在（说明已经成功获取过用户信息），则尝试重连
        if (currentUser) {
            // 从 localStorage 获取用户 ID 用于重连
            let cachedUserId = null;
            const savedUser = localStorage.getItem('chat_user');
            if (savedUser) {
                try {
                    const parsedUser = JSON.parse(savedUser);
                    if (parsedUser && parsedUser.id) {
                        cachedUserId = parsedUser.id;
                    }
                } catch (e) {
                    // 解析失败，忽略
                }
            }
            setTimeout(() => connectWebSocket(cachedUserId), 3000);
        }
    };
}

// ==================== 用户管理 ====================

function updateUserList(users) {
    const $userList = $('#userList');
    $userList.empty();

    // 更新全局在线用户列表（用于@功能）
    updateOnlineUsers(users);

    // 只显示在线用户，如果列表为空则显示提示
    if (!users || users.length === 0) {
        $userList.html('<div class="empty-users"><i class="fas fa-user-slash"></i><p>暂无在线用户</p></div>');
        return;
    }

    // 确保按用户ID排序，保持列表稳定
    const sortedUsers = users.sort((a, b) => a.id.localeCompare(b.id));

    let selfFound = false;
    sortedUsers.forEach(function(user) {
        // 更严格的自我判断逻辑 - 必须同时匹配 ID
        let isSelf = false;
        if (currentUser && user.id === currentUser.id) {
            isSelf = true;
            selfFound = true;
        }

        // 构建用户项 HTML
        const userNameHtml = isSelf
            ? `<span class="user-name">${user.nickname} <span class="online-indicator" title="在线"></span></span>`
            : `<span class="user-name">${user.nickname}</span>`;

        const userItem = $(`
                    <div class="user-item ${isSelf ? 'self' : ''}">
                        <img class="user-avatar" src="${user.avatar}" alt="${user.nickname}">
                        ${userNameHtml}
                    </div>
                `);
        $userList.append(userItem);
    });

    if (!selfFound && currentUser) {
        // 未找到当前用户，忽略
    }

    $('#onlineCount').text(users.length);
}

// ==================== 消息处理 ====================
function handleMessage(message) {
    const $container = $('#messagesContainer');

    // 移除空状态提示
    if ($container.find('.empty-state').length > 0) {
        $container.empty();
    }

    const isSelf = currentUser && message.senderId === currentUser.id;

    const time = formatTime(message.timestamp);

    if (message.type === 'SYSTEM' || message.type === 'JOIN' || message.type === 'LEAVE') {
        // 系统消息
        const systemMsg = $(`
                    <div class="message system">
                        <div class="message-bubble">${message.content}</div>
                    </div>
                `);
        $container.append(systemMsg);
    } else {
        // 检查是否是AI机器人消息
        const isAiBot = message.senderId === 'ai_bot';

        // 普通消息
        const msgHtml = $(`
                    <div class="message ${isSelf ? 'self' : ''} ${isAiBot ? 'ai-bot' : ''}">
                        <img class="message-avatar" src="${message.avatar}" alt="${message.sender}">
                        <div class="message-content">
                            <div class="message-info">
                                <span class="message-sender">${message.sender}</span>
                                <span class="message-time">${time}</span>
                            </div>
                            <div class="message-bubble">${formatMessageContent(message.content)}</div>
                        </div>
                    </div>
                `);
        $container.append(msgHtml);

        // 检查是否被@
        if (!isSelf && message.mentionedUserIds && message.mentionedUserIds.includes(currentUser.id)) {
            showNotification(`${message.sender} 提到了你`, 'info', '@提醒');

            // 播放提示音（如果浏览器支持）
            playMentionSound();
        }
    }

    // 滚动到底部
    $container.scrollTop($container[0].scrollHeight);
}

/**
 * 格式化消息内容，高亮@用户
 */
function formatMessageContent(content) {
    // 将 @昵称 替换为带样式的 span
    return escapeHtml(content).replace(/@([^\s]+)/g, '<span class="mention-highlight">@$1</span>');
}

/**
 * 播放@提醒声音
 */
function playMentionSound() {
    try {
        // 创建一个简单的提示音
        const audioContext = new (window.AudioContext || window.webkitAudioContext)();
        const oscillator = audioContext.createOscillator();
        const gainNode = audioContext.createGain();

        oscillator.connect(gainNode);
        gainNode.connect(audioContext.destination);

        oscillator.frequency.value = 800;
        oscillator.type = 'sine';

        gainNode.gain.setValueAtTime(0.3, audioContext.currentTime);
        gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.5);

        oscillator.start(audioContext.currentTime);
        oscillator.stop(audioContext.currentTime + 0.5);
    } catch (e) {
        // 如果音频播放失败，静默忽略
    }
}

function sendMessage() {
    const $input = $('#messageInput');
    const content = $input.val().trim();

    if (!content) {
        return;
    }

    if (!currentUser) {
        showNotification('正在连接服务器，请稍候...', 'warning', '连接中');
        return;
    }

    if (stompClient && isConnected) {
        const message = {
            type: 'CHAT',
            sender: currentUser.nickname,
            senderId: currentUser.id,
            avatar: currentUser.avatar,
            content: content
        };

        stompClient.send("/app/chat", {}, JSON.stringify(message));
        $input.val('');
        autoResizeTextarea($input);
    } else {
        showNotification('连接已断开，正在重连...', 'error', '连接异常');
        connectWebSocket();
    }
}

// ==================== 表情选择器 ====================
function initEmojiPicker() {
    const $picker = $('#emojiPicker');
    QQ_EMOJIS.forEach(function(emoji) {
        const $emoji = $(`<span class="emoji-item">${emoji}</span>`);
        $emoji.click(function(e) {
            e.stopPropagation();  // 阻止事件冒泡
            insertEmoji(emoji);
        });
        $picker.append($emoji);
    });

    // 点击表情选择器本身时阻止事件冒泡
    $picker.click(function(e) {
        e.stopPropagation();
    });
}

function toggleEmojiPicker() {
    const $picker = $('#emojiPicker');
    $picker.toggleClass('active');
    // 阻止事件冒泡，避免触发 document 的点击事件
    return false;
}

function insertEmoji(emoji) {
    const $input = $('#messageInput');
    const position = $input[0].selectionStart;
    const text = $input.val();
    $input.val(text.substring(0, position) + emoji + text.substring(position));
    $input[0].selectionStart = $input[0].selectionEnd = position + emoji.length;
    $input.focus();
    autoResizeTextarea($input);
}

function clearInput() {
    const $input = $('#messageInput');
    $input.val('');
    $input.focus();
    autoResizeTextarea($input);
}

// 自动调整文本框高度
function autoResizeTextarea($textarea) {
    if (!$textarea || !$textarea.length) return;

    $textarea.css('height', 'auto');
    const newHeight = Math.min($textarea[0].scrollHeight, 120);
    $textarea.css('height', newHeight + 'px');
}

// 监听输入框内容变化，自动调整高度
$(document).on('input', '#messageInput', function() {
    autoResizeTextarea($(this));
});

// ==================== 重置身份 ====================
function resetIdentity() {
    showConfirm(
        '确定要重置身份吗？这将创建一个新的匿名身份，当前身份将无法恢复。',
        function() {
            // 先断开 WebSocket 连接
            if (stompClient) {
                try {
                    stompClient.disconnect();
                } catch (e) {
                    // 断开失败，忽略
                }
                stompClient = null;
            }
            isConnected = false;

            // 清除本地存储的用户信息
            localStorage.removeItem('chat_user');
            currentUser = null;

            showNotification('身份已重置，请创建新身份', 'success', '重置成功');

            // 清空消息列表
            $('#messagesContainer').html(`
                        <div class="empty-state">
                            <i class="fas fa-comment-dots"></i>
                            <p>欢迎来到匿名聊天室<br>开始聊天吧！</p>
                        </div>
                    `);

            // 清空用户列表
            $('#userList').html('<div class="empty-users"><i class="fas fa-user-slash"></i><p>暂无在线用户</p></div>');
            $('#onlineCount').text('0');

            // 显示创建用户弹窗
            setTimeout(() => {
                showCreateUserDialog();
            }, 300);
        },
        function() {
            // 用户点击取消，不做任何操作
        }
    );
}

// ==================== 工具函数 ====================
function formatTime(timestamp) {
    const date = new Date(timestamp);
    const hours = date.getHours().toString().padStart(2, '0');
    const minutes = date.getMinutes().toString().padStart(2, '0');
    return hours + ':' + minutes;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function toggleSidebar() {
    $('#sidebar').toggleClass('active');
    $('.mobile-overlay').toggleClass('active');
}

// ==================== 自定义通知系统 ====================
function showNotification(message, type = 'info', title = '') {
    // 移除现有的通知
    $('.custom-notification').remove();

    const icons = {
        info: '<i class="fas fa-info-circle"></i>',
        success: '<i class="fas fa-check-circle"></i>',
        error: '<i class="fas fa-times-circle"></i>',
        warning: '<i class="fas fa-exclamation-triangle"></i>'
    };

    const titles = {
        info: title || '提示',
        success: title || '成功',
        error: title || '错误',
        warning: title || '警告'
    };

    const notification = $(`
                <div class="custom-notification ${type}">
                    <div class="notification-icon">${icons[type]}</div>
                    <div class="notification-content">
                        <div class="notification-title">${titles[type]}</div>
                        <div class="notification-message">${message}</div>
                    </div>
                    <button class="notification-close" onclick="$(this).parent().remove()">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
            `);

    $('body').append(notification);

    // 3秒后自动关闭
    setTimeout(() => {
        notification.addClass('hiding');
        setTimeout(() => notification.remove(), 300);
    }, 3000);
}

// ==================== 自定义确认对话框 ====================
function showConfirm(message, onConfirm, onCancel) {
    // 移除现有的确认对话框
    $('.confirm-overlay').remove();

    const confirmDialog = $(`
                <div class="confirm-overlay">
                    <div class="confirm-dialog">
                        <div class="confirm-icon">
                            <i class="fas fa-exclamation-triangle"></i>
                        </div>
                        <div class="confirm-title">确认操作</div>
                        <div class="confirm-message">${message}</div>
                        <div class="confirm-buttons">
                            <button class="confirm-btn confirm-btn-cancel" id="confirmCancel">取消</button>
                            <button class="confirm-btn confirm-btn-confirm" id="confirmOk">确定</button>
                        </div>
                    </div>
                </div>
            `);

    $('body').append(confirmDialog);

    // 点击取消按钮
    $('#confirmCancel').click(function() {
        closeConfirm();
        if (onCancel && typeof onCancel === 'function') {
            onCancel();
        }
    });

    // 点击确定按钮
    $('#confirmOk').click(function() {
        closeConfirm();
        if (onConfirm && typeof onConfirm === 'function') {
            onConfirm();
        }
    });

    // 点击遮罩层关闭
    $('.confirm-overlay').click(function(e) {
        if ($(e.target).hasClass('confirm-overlay')) {
            closeConfirm();
            if (onCancel && typeof onCancel === 'function') {
                onCancel();
            }
        }
    });

    // ESC 键关闭
    $(document).one('keydown.confirm', function(e) {
        if (e.key === 'Escape') {
            closeConfirm();
            if (onCancel && typeof onCancel === 'function') {
                onCancel();
            }
        }
    });
}

function closeConfirm() {
    $('.confirm-overlay').fadeOut(200, function() {
        $(this).remove();
    });
    $(document).off('keydown.confirm');
}

// ==================== @用户功能 ====================

/**
 * 处理@输入
 */
function handleMentionInput($input) {
    const value = $input.val();
    const cursorPos = $input[0].selectionStart;

    // 查找光标前的最后一个@符号
    const textBeforeCursor = value.substring(0, cursorPos);
    const lastAtIndex = textBeforeCursor.lastIndexOf('@');

    if (lastAtIndex !== -1) {
        // 检查@后面是否有空格（如果有空格，不显示提示）
        const textAfterAt = textBeforeCursor.substring(lastAtIndex + 1);
        if (!textAfterAt.includes(' ')) {
            mentionSearchText = textAfterAt;
            showMentionSuggestions(mentionSearchText);
            return;
        }
    }

    hideMentionSuggestions();
}

/**
 * 显示@用户提示框
 */
function showMentionSuggestions(searchText) {
    const $suggestions = $('#mentionSuggestions');
    $suggestions.empty();

    // 过滤在线用户
    const filteredUsers = onlineUsers.filter(user => {
        if (!searchText) return true;
        return user.nickname.toLowerCase().includes(searchText.toLowerCase());
    });

    if (filteredUsers.length === 0) {
        hideMentionSuggestions();
        return;
    }

    // 生成提示列表
    filteredUsers.forEach((user, index) => {
        const $item = $(`
                    <div class="mention-item" data-index="${index}" data-user-id="${user.id}">
                        <img class="mention-avatar" src="${user.avatar}" alt="${user.nickname}">
                        <span class="mention-name">${user.nickname}</span>
                    </div>
                `);

        $item.click(function() {
            selectMentionUser(index);
        });

        $suggestions.append($item);
    });

    $suggestions.addClass('active');
    mentionSelectedIndex = 0;
    updateMentionSelection();
}

/**
 * 隐藏@用户提示框
 */
function hideMentionSuggestions() {
    $('#mentionSuggestions').removeClass('active');
    mentionSelectedIndex = -1;
}

/**
 * 导航@用户列表
 */
function navigateMentionList(direction) {
    const $suggestions = $('#mentionSuggestions');
    const items = $suggestions.find('.mention-item');

    if (items.length === 0) return;

    mentionSelectedIndex += direction;

    if (mentionSelectedIndex < 0) {
        mentionSelectedIndex = items.length - 1;
    } else if (mentionSelectedIndex >= items.length) {
        mentionSelectedIndex = 0;
    }

    updateMentionSelection();
}

/**
 * 更新@用户选择状态
 */
function updateMentionSelection() {
    const $suggestions = $('#mentionSuggestions');
    const items = $suggestions.find('.mention-item');

    items.removeClass('selected');

    if (mentionSelectedIndex >= 0 && mentionSelectedIndex < items.length) {
        const $selected = items.eq(mentionSelectedIndex);
        $selected.addClass('selected');

        // 滚动到可见区域
        const container = $suggestions[0];
        const selectedElement = $selected[0];
        if (selectedElement.offsetTop < container.scrollTop ||
            selectedElement.offsetTop + selectedElement.offsetHeight > container.scrollTop + container.clientHeight) {
            container.scrollTop = selectedElement.offsetTop;
        }
    }
}

/**
 * 选择@用户
 */
function selectMentionUser(index) {
    const $suggestions = $('#mentionSuggestions');
    const $selected = $suggestions.find(`.mention-item[data-index="${index}"]`);

    if ($selected.length === 0) return;

    const userId = $selected.data('user-id');
    const user = onlineUsers.find(u => u.id === userId);

    if (!user) return;

    const $input = $('#messageInput');
    const value = $input.val();
    const cursorPos = $input[0].selectionStart;

    // 查找最后一个@符号的位置
    const textBeforeCursor = value.substring(0, cursorPos);
    const lastAtIndex = textBeforeCursor.lastIndexOf('@');

    if (lastAtIndex !== -1) {
        // 替换@后面的文本为用户昵称
        const beforeAt = value.substring(0, lastAtIndex);
        const afterCursor = value.substring(cursorPos);
        const newValue = beforeAt + '@' + user.nickname + ' ' + afterCursor;

        $input.val(newValue);

        // 设置光标位置
        const newCursorPos = lastAtIndex + user.nickname.length + 2; // +2 for @ and space
        $input[0].selectionStart = newCursorPos;
        $input[0].selectionEnd = newCursorPos;

        // 聚焦输入框
        $input.focus();
    }

    hideMentionSuggestions();
}

/**
 * 更新在线用户列表（用于@功能）
 */
function updateOnlineUsers(users) {
    onlineUsers = users;
}