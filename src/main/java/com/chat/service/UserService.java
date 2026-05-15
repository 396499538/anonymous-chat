package com.chat.service;

import com.chat.model.UserInfo;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    // 存储所有在线用户
    private final Map<String, UserInfo> onlineUsers = new ConcurrentHashMap<>();

    // 常用汉字用于生成昵称
    private static final String[] NICKNAME_CHARS = {
            "晨", "曦", "夜", "星", "月", "云", "风", "雨", "雪", "霜",
            "花", "叶", "竹", "松", "梅", "兰", "菊", "桃", "柳", "杨",
            "明", "亮", "光", "彩", "虹", "霞", "雾", "涛", "波", "浪",
            "天", "地", "海", "山", "川", "湖", "江", "河", "林", "森",
            "龙", "凤", "虎", "鹤", "鸽", "鹰", "燕", "蝶", "蜂", "蝉",
            "春", "夏", "秋", "冬", "暖", "凉", "热", "冷", "柔", "刚",
            "美", "丽", "秀", "雅", "静", "动", "快", "乐", "安", "宁",
            "智", "慧", "勇", "诚", "信", "义", "礼", "仁", "爱", "善",
            "梦", "幻", "诗", "画", "琴", "棋", "书", "香", "茶", "酒",
            "玉", "珍", "宝", "珠", "金", "银", "铜", "铁", "石", "木"
    };

    // 中国复姓列表
    private static final String[] COMPOUND_SURNAMES = {
            "欧阳", "太史", "端木", "上官", "司马", "东方", "独孤", "南宫",
            "万俟", "闻人", "夏侯", "诸葛", "尉迟", "公羊", "赫连", "澹台",
            "皇甫", "宗政", "濮阳", "公冶", "太叔", "申屠", "公孙", "慕容",
            "仲孙", "钟离", "长孙", "宇文", "司徒", "鲜于", "司空", "子车",
            "颛孙", "巫马", "公西", "漆雕", "乐正", "壤驷", "公良", "拓跋",
            "夹谷", "宰父", "穀梁", "段干", "百里", "东郭", "南门", "呼延"
    };

    // 名字用字（单字名）
    private static final String[] GIVEN_NAMES = {
            "伟", "芳", "娜", "敏", "静", "丽", "强", "磊", "洋", "艳",
            "勇", "军", "杰", "娟", "涛", "明", "超", "秀英", "霞", "平",
            "辉", "玲", "桂", "凤", "建华", "建国", "建军", "红", "玉兰", "玉梅",
            "瑞", "宏", "志", "文", "华", "国", "永", "德", "庆", "贤",
            "宇", "轩", "涵", "梓", "浩", "然", "一", "诺", "欣", "怡",
            "晨", "曦", "雨", "萱", "佳", "琪", "思", "妍", "睿", "哲"
    };

    // 头像颜色配置
    private static final String[] AVATAR_COLORS = {
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7",
            "#DDA0DD", "#98D8C8", "#F7DC6F", "#BB8FCE", "#85C1E9",
            "#F8B500", "#00CED1", "#FF69B4", "#32CD32", "#FF4500",
            "#9370DB", "#20B2AA", "#FF6347", "#7B68EE", "#00FA9A"
    };

    // 百家姓或常用前缀
    private static final String[] NICKNAME_PREFIX = {
            "小", "老", "大", "阿", "神秘", "快乐", "开心", "阳光", "清风", "明月",
            "星空", "云朵", "微风", "暖阳", "彩虹", "晚霞", "晨露", "落叶", "飞鸟", "游鱼"
    };

    public UserInfo createUser() {
        String id = UUID.randomUUID().toString().replace("-", "");
        String nickname = generateNickname();
        String avatar = generateAvatar();

        UserInfo user = new UserInfo(id, nickname, avatar);
        onlineUsers.put(id, user);

        return user;
    }

    /**
     * 创建自定义用户（带指定昵称和头像）
     */
    public UserInfo createCustomUser(String nickname, String avatar) {
        String id = UUID.randomUUID().toString().replace("-", "");
        
        // 如果没有提供头像，生成一个
        if (avatar == null || avatar.trim().isEmpty()) {
            avatar = generateAvatar();
        }
        
        UserInfo user = new UserInfo(id, nickname, avatar);
        onlineUsers.put(id, user);
        
        return user;
    }
    
    /**
     * 生成随机昵称（复姓 + 名字 或 3个汉字）
     */
    public String generateRandomNickname() {
        return generateNickname();
    }

    public UserInfo getUser(String id) {
        return onlineUsers.get(id);
    }

    /**
     * 添加用户到在线用户列表（用于服务器重启后重建用户）
     */
    public void addUser(UserInfo user) {
        onlineUsers.put(user.getId(), user);
    }

    public void removeUser(String id) {
        onlineUsers.remove(id);
    }

    public List<UserInfo> getOnlineUsers() {
        return new ArrayList<>(onlineUsers.values());
    }

    public int getOnlineCount() {
        return onlineUsers.size();
    }

    private String generateNickname() {
        Random random = new Random();
        
        // 50%概率使用复姓昵称，50%概率使用3个汉字昵称
        if (random.nextBoolean()) {
            return generateCompoundSurnameNickname(random);
        } else {
            return generateThreeCharNickname(random);
        }
    }
    
    /**
     * 生成复姓昵称（复姓 + 名字）
     */
    private String generateCompoundSurnameNickname(Random random) {
        // 随机选择一个复姓
        String surname = COMPOUND_SURNAMES[random.nextInt(COMPOUND_SURNAMES.length)];
        
        // 随机选择1-2个字的名字
        int nameLength = random.nextBoolean() ? 1 : 2; // 50%概率单字名或双字名
        StringBuilder givenName = new StringBuilder();
        
        Set<Integer> usedIndices = new HashSet<>();
        while (givenName.length() < nameLength) {
            int index = random.nextInt(GIVEN_NAMES.length);
            if (!usedIndices.contains(index)) {
                usedIndices.add(index);
                givenName.append(GIVEN_NAMES[index]);
            }
        }
        
        return surname + givenName.toString();
    }
    
    /**
     * 生成3个汉字昵称
     */
    private String generateThreeCharNickname(Random random) {
        StringBuilder nickname = new StringBuilder();

        // 生成3个不同的汉字
        Set<Integer> usedIndices = new HashSet<>();
        while (nickname.length() < 3) {
            int index = random.nextInt(NICKNAME_CHARS.length);
            if (!usedIndices.contains(index)) {
                usedIndices.add(index);
                nickname.append(NICKNAME_CHARS[index]);
            }
        }

        // 30%概率在末尾添加常见后缀
        if (random.nextInt(100) < 30) {
            String[] suffix = {"子", "的", "头", "儿", "妹", "哥"};
            nickname.append(suffix[random.nextInt(suffix.length)]);
        }

        return nickname.toString();
    }

    private String generateAvatar() {
        Random random = new Random();
        String color = AVATAR_COLORS[random.nextInt(AVATAR_COLORS.length)];
        // 生成一个基于颜色和文字的 SVG 头像
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(
                createAvatarSvg(color).getBytes()
        );
    }

    private String createAvatarSvg(String color) {
        Random random = new Random();
        String[] emojis = {"😀", "😎", "🤗", "😇", "🥰", "😇", "🤩", "😋", "🤭", "🙃"};
        String emoji = emojis[random.nextInt(emojis.length)];

        return String.format(
                "<svg xmlns='http://www.w3.org/2000/svg' width='100' height='100' viewBox='0 0 100 100'>" +
                        "<rect width='100' height='100' fill='%s' rx='15'/>" +
                        "<text x='50' y='65' font-size='50' text-anchor='middle' fill='white'>%s</text>" +
                        "</svg>",
                color, emoji
        );
    }
}
