package com.mikuac.shiro.common.utils;

import com.mikuac.shiro.bo.ArrayMsg;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * Created on 2021/8/10.
 *
 * @author Zero
 * @version $Id: $Id
 */
@Slf4j
@SuppressWarnings("unused")
public class ShiroUtils {

    private final static String CQ_CODE_SPLIT = "(?<=\\[CQ:[^]]{1,99999}])|(?=\\[CQ:[^]]{1,99999}])";

    private final static String CQ_CODE_REGEX = "\\[CQ:([^,\\[\\]]+)((?:,[^,=\\[\\]]+=[^,\\[\\]]*)*)]";

    /**
     * 判断是否为全体at
     *
     * @param msg 消息
     * @return 是否为全体at
     */
    public static boolean isAtAll(String msg) {
        return msg.contains("[CQ:at,qq=all]");
    }

    /**
     * 判断是否为全体at
     *
     * @param arrayMsg 消息链
     * @return 是否为全体at
     */
    public static boolean isAtAll(List<ArrayMsg> arrayMsg) {
        return arrayMsg.stream().anyMatch(it -> "all".equals(it.getData().get("qq")));
    }

    /**
     * 获取消息内所有at对象账号（不包含全体 at）
     *
     * @param arrayMsg 消息链
     * @return at对象列表
     */
    public static List<Long> getAtList(List<ArrayMsg> arrayMsg) {
        return arrayMsg
                .stream()
                .filter(it -> MsgTypeEnum.AT == it.getType() && !"all".equals(it.getData().get("qq")))
                .map(it -> Long.parseLong(it.getData().get("qq")))
                .collect(Collectors.toList());
    }

    /**
     * 获取消息内所有图片链接
     *
     * @param arrayMsg 消息链
     * @return 图片链接列表
     */
    public static List<String> getMsgImgUrlList(List<ArrayMsg> arrayMsg) {
        return arrayMsg
                .stream()
                .filter(it -> MsgTypeEnum.IMAGE == it.getType())
                .map(it -> it.getData().get("url"))
                .collect(Collectors.toList());
    }

    /**
     * 获取消息内所有视频链接
     *
     * @param arrayMsg 消息链
     * @return 视频链接列表
     */
    public static List<String> getMsgVideoUrlList(List<ArrayMsg> arrayMsg) {
        return arrayMsg
                .stream()
                .filter(it -> MsgTypeEnum.VIDEO == it.getType())
                .map(it -> it.getData().get("url"))
                .collect(Collectors.toList());
    }

    /**
     * 获取群头像
     *
     * @param groupId 群号
     * @param size    头像尺寸
     * @return 头像链接 （size为0返回真实大小, 40(40*40), 100(100*100), 640(640*640)）
     */
    public static String getGroupAvatar(long groupId, int size) {
        return String.format("https://p.qlogo.cn/gh/%s/%s/%s", groupId, groupId, size);
    }

    /**
     * 获取用户昵称
     *
     * @param userId QQ号
     * @return 用户昵称
     */
    public static String getNickname(long userId) {
        String url = String.format("https://r.qzone.qq.com/fcg-bin/cgi_get_portrait.fcg?uins=%s", userId);
        String result = NetUtils.get(url, "GBK");
        if (result != null && !result.isEmpty()) {
            String nickname = result.split(",")[6];
            return nickname.substring(1, nickname.length() - 1);
        }
        return "";
    }

    /**
     * 获取用户头像
     *
     * @param userId QQ号
     * @param size   头像尺寸
     * @return 头像链接 （size为0返回真实大小, 40(40*40), 100(100*100), 640(640*640)）
     */
    public static String getUserAvatar(long userId, int size) {
        return String.format("https://q2.qlogo.cn/headimg_dl?dst_uin=%s&spec=%s", userId, size);
    }

    /**
     * 消息解码
     *
     * @param string 需要解码的内容
     * @return 解码处理后的字符串
     */
    public static String unescape(String string) {
        return string.replace("&#44;", ",").replace("&#91;", "[").replace("&#93;", "]").replace("&amp;", "&");
    }

    /**
     * 消息编码
     *
     * @param string 需要编码的内容
     * @return 编码处理后的字符串
     */
    public static String escape(String string) {
        return string.replace("&", "&amp;").replace(",", "&#44;").replace("[", "&#91;").replace("]", "&#93;");
    }

    /**
     * 消息编码（可用于转义CQ码，防止文本注入）
     *
     * @param string 需要编码的内容
     * @return 编码处理后的字符串
     */
    public static String escape2(String string) {
        return string.replace("[", "&#91;").replace("]", "&#93;");
    }

    /**
     * string 消息上报转消息链
     * 建议传入 event.getMessage 而非 event.getRawMessage
     * 例如 go-cq-http rawMessage 不包含图片 url
     *
     * @param msg 需要修改客户端消息上报类型为 string
     * @return 消息链
     */
    public static List<ArrayMsg> rawToArrayMsg(String msg) {
        List<ArrayMsg> arrayMsgList = new ArrayList<>();
        try {
            Arrays.stream(msg.split(CQ_CODE_SPLIT)).filter(s -> !s.isEmpty()).forEach(s -> {
                Matcher matcher = RegexUtils.regexMatcher(CQ_CODE_REGEX, s);
                ArrayMsg arrayMsg = new ArrayMsg();
                Map<String, String> data = new HashMap<>(16);
                if (matcher == null) {
                    arrayMsg.setType(MsgTypeEnum.TEXT);
                    data.put("text", s);
                } else {
                    arrayMsg.setType(MsgTypeEnum.valueOf(matcher.group(1).toUpperCase()));
                    Arrays.stream(matcher.group(2).split(","))
                            .filter(args -> !args.isEmpty())
                            .forEach(args -> {
                                String k = args.substring(0, args.indexOf("="));
                                String v = ShiroUtils.unescape(args.substring(args.indexOf("=") + 1));
                                data.put(k, v);
                            });
                }
                arrayMsg.setData(data);
                arrayMsgList.add(arrayMsg);
            });
        } catch (Exception e) {
            log.error("Raw message convert failed: {}", e.getMessage());
        }
        return arrayMsgList;
    }

    /**
     * 从 MsgChainBean 生成 CQ Code
     *
     * @param o {@link ArrayMsg}
     * @return CQ Code
     */
    public static String jsonToCode(ArrayMsg o) {
        StringBuilder builder = new StringBuilder();
        builder.append("[CQ:").append(o.getType());
        o.getData().forEach((k, v) -> builder.append(",").append(k).append("=").append(v));
        builder.append("]");
        return builder.toString();
    }

    /**
     * 创建自定义消息合并转发
     *
     * @param uin      发送者QQ号
     * @param name     发送者显示名字
     * @param contents 消息列表，每个元素视为一个消息节点
     *                 <a href="https://docs.go-cqhttp.org/cqcode/#%E5%90%88%E5%B9%B6%E8%BD%AC%E5%8F%91">参考文档</a>
     * @return 转发消息
     */
    public static List<Map<String, Object>> generateForwardMsg(long uin, String name, List<String> contents) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        contents.forEach(msg -> {
            Map<String, Object> node = new HashMap<String, Object>(16) {{
                put("type", "node");
                put("data", new HashMap<String, Object>(16) {{
                    put("name", name);
                    put("uin", uin);
                    put("content", msg);
                }});
            }};
            nodes.add(node);
        });
        return nodes;
    }

}
