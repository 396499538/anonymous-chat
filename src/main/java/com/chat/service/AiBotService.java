package com.chat.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Service
public class AiBotService {
    
    private final Random random = new Random();
    
    // 脑筋急转弯列表（问题）
    private static final List<String> BRAIN_TEASER_QUESTIONS = Arrays.asList(
        "什么东西越洗越脏？",
        "什么门永远关不上？",
        "什么书在书店买不到？",
        "什么人始终不敢洗澡？",
        "什么果不能吃？",
        "什么蛋不能吃？",
        "什么车最不可能发生车祸？",
        "什么路不能走？",
        "什么布剪不断？",
        "什么花不能摘？",
        "什么海没有水？",
        "什么牛不会吃草？",
        "什么马不会跑？",
        "什么鸡没有翅膀？",
        "什么狗不会叫？",
        "什么帽不能戴？",
        "什么锁没有钥匙？",
        "什么桥下面没有水？",
        "什么船不在水里航行？",
        "什么伞不能遮雨？",
        "什么笔不能写字？",
        "什么钟不能走？",
        "什么灯不亮？",
        "什么线不能缝衣服？",
        "什么球不能踢？",
        "什么瓜不能吃？",
        "什么池没有水？",
        "什么虎不吃人？",
        "什么鱼不能吃？",
        "什么鼠最爱干净？",
        "什么猪不会跑？",
        "什么羊不会叫？",
        "什么兔不吃萝卜？",
        "什么蛇没有毒？",
        "什么龙不会飞？",
        "什么猫不抓老鼠？",
        "什么熊不吃鱼？",
        "什么鹿不会跑？",
        "什么狼不吃肉？",
        "什么狮不吼叫？",
        "什么豹不奔跑？",
        "什么狐不狡猾？",
        "什么龟不长寿？",
        "什么鹤不飞翔？",
        "什么燕不筑巢？",
        "什么鹊不报喜？",
        "什么鹰不捕猎？",
        "什么鸽不送信？",
        "什么鸦不黑色？",
        "什么雀不跳跃？"
    );
    
    // 脑筋急转弯答案列表
    private static final List<String> BRAIN_TEASER_ANSWERS = Arrays.asList(
        "水",
        "球门",
        "遗书",
        "泥人",
        "恶果",
        "笨蛋",
        "风车",
        "电路",
        "瀑布",
        "火花",
        "火海",
        "蜗牛",
        "木马",
        "田鸡",
        "热狗",
        "螺丝帽",
        "密码锁",
        "立交桥",
        "宇宙飞船",
        "降落伞",
        "电笔",
        "时钟图片",
        "关灯",
        "光线",
        "眼球",
        "傻瓜",
        "电池",
        "纸老虎",
        "木鱼",
        "袋鼠",
        "玩具猪",
        "洋娃娃",
        "玩具兔",
        "玩具蛇",
        "恐龙",
        "机器猫",
        "玩具熊",
        "长颈鹿（玩具）",
        "白眼狼",
        "石狮子",
        "玩具豹",
        "狐狸精",
        "玩具龟",
        "丹顶鹤（标本）",
        "燕子（风筝）",
        "喜鹊（画）",
        "老鹰（玩具）",
        "和平鸽（雕塑）",
        "乌鸦（白色）",
        "麻雀（玩具）"
    );
    
    /**
     * 获取随机脑筋急转弯问题
     */
    public String getRandomQuestion() {
        return BRAIN_TEASER_QUESTIONS.get(random.nextInt(BRAIN_TEASER_QUESTIONS.size()));
    }
    
    /**
     * 根据索引获取问题
     */
    public String getQuestionByIndex(int index) {
        if (index >= 0 && index < BRAIN_TEASER_QUESTIONS.size()) {
            return BRAIN_TEASER_QUESTIONS.get(index);
        }
        return "不知道";
    }
    
    /**
     * 根据索引获取答案
     */
    public String getAnswerByIndex(int index) {
        if (index >= 0 && index < BRAIN_TEASER_ANSWERS.size()) {
            return BRAIN_TEASER_ANSWERS.get(index);
        }
        return "不知道";
    }
    
    /**
     * 获取问题和答案的索引对
     */
    public int getRandomQuestionIndex() {
        return random.nextInt(BRAIN_TEASER_QUESTIONS.size());
    }
}
