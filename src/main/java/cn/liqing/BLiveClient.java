package cn.liqing;

import cn.liqing.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public abstract class BLiveClient {
    static final Logger LOGGER = LoggerFactory.getLogger(BLiveClient.class);
    public SocketState state;
    public int room;
    final WebSocketClient socket;

    public BLiveClient() {
        this(URI.create("wss://broadcastlv.chat.bilibili.com:2245/sub"));
    }

    public BLiveClient(URI serverUri) {
        socket = new WebSocketClient(serverUri) {
            @Nullable Timer heartbeatTimer;

            @Override
            public void onOpen(ServerHandshake handshakedata) {
                BLiveClient.this.onOpen();
                //设置socket状态为已连接
                state = SocketState.CONNECTED;

                //发送认证包
                byte[] body;
                try {
                    body = new ObjectMapper().writeValueAsBytes(new Auth(room));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("序列化Auth对象失败", e);
                }
                send(new Packet(Operation.AUTH, body).pack());

                //设置一个定时器每隔20秒发送一次心跳包
                heartbeatTimer = new Timer();
                heartbeatTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        //发送心跳包
                        send(new Packet(Operation.HEARTBEAT, new byte[0]).pack());
                    }
                }, 0, 20 * 1000);
            }

            @Override
            public void onMessage(ByteBuffer bytes) {
                var packets = Packet.unPack(bytes);
                packets.forEach(packet -> onPacket(packet));
            }

            @Override
            public void onMessage(String message) {
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                state = SocketState.CLOSED;
                BLiveClient.this.onClose(code, reason, remote);
            }

            @Override
            public void onClosing(int code, String reason, boolean remote) {
                state = SocketState.CLOSING;
            }

            @Override
            public void onError(Exception ex) {
                BLiveClient.this.onError(ex);
            }
        };
    }


    /**
     * 连接到直播间
     * 非阻塞方法,不会等待连接结果,如果已经连接会重新连接
     *
     * @param room 直播间号
     * @return 如果当前正在连接或者关闭返回 false 其他情况返回 true
     */
    public boolean connect(int room) {
        //如果正在连接或者关闭直接返回 false
        if (state == SocketState.CONNECTING || state == SocketState.CLOSING)
            return false;
        //设置socket状态为连接中
        state = SocketState.CONNECTING;
        //设置直播间号
        this.room = room;
        //如果已经连接或断开就重新连接，否则正常连接
        if (socket.isOpen() || socket.isClosed()) {
            socket.reconnect();
        } else {
            socket.connect();
        }
        return true;
    }

    /**
     * 关闭连接
     * 非阻塞方法，不会等待关闭结果
     *
     * @return 如果正在关闭中，返回 false 否则返回 true
     */
    public boolean close() {
        if (state == SocketState.CLOSING)
            return false;
        //设置socket状态为关闭中
        state = SocketState.CLOSING;
        socket.close();
        return true;
    }

    public void onPacket(@NotNull Packet packet) {
        if (packet.operation == Operation.SEND_SMS_REPLY) {
            String bodyStr = new String(packet.body, StandardCharsets.UTF_8);
            LOGGER.debug(bodyStr);

            Message msg;
            try {
                msg = new ObjectMapper().readValue(bodyStr, Message.class);
                if (msg.cmd == null) {
                    LOGGER.warn("消息包中没有cmd");
                    return;
                }
            } catch (Exception ex) {
                LOGGER.error("解析选项出错", ex);
                return;
            }

            switch (msg.cmd) {
                case "DANMU_MSG" -> {
                    try {
                        var info = msg.info;
                        if (info == null) {
                            LOGGER.warn("弹幕包中没有info");
                            return;
                        }
                        //解析用户
                        var danmu = new Danmu();
                        danmu.user.uid = info.at("/2/0").asInt();
                        danmu.user.name = info.at("2/1").asText();
                        danmu.user.guardLevel = info.get(7).asInt();

                        //解析粉丝团
                        var fansMedal = info.get(3);
                        if (fansMedal != null && fansMedal.size() >= 2) {
                            danmu.user.fansMedal = new User.FansMedal();
                            danmu.user.fansMedal.level = fansMedal.get(0).asInt();
                            danmu.user.fansMedal.name = fansMedal.get(1).asText();
                        }

                        //解析内容
                        danmu.body = info.get(1).asText();
                        //判断是弹幕消息还是表情消息
                        int isEmoji = info.at("/0/12").asInt();
                        if (isEmoji == 1) {
                            var emoji = new Emoji();
                            emoji.user = danmu.user;
                            emoji.body = danmu.body;
                            emoji.uri = info.at("/0/13/url").asText();
                            onEmoji(emoji);
                        } else {
                            onDanmu(danmu);
                        }
                    } catch (RuntimeException ex) {
                        LOGGER.error("解析弹幕包出错", ex);
                    }
                }
                case "SEND_GIFT" -> {
                    try {
                        var data = msg.data;
                        if (data == null) {
                            LOGGER.warn("礼物包中没有data");
                            return;
                        }
                        var gift = new Gift();
                        gift.user.uid = data.get("uid").asInt();
                        gift.user.name = data.get("uname").asText();
                        gift.user.guardLevel = data.get("guard_level").asInt();

                        var fansMedal = data.get("medal_info");
                        if (fansMedal != null && !fansMedal.isNull()) {
                            gift.user.fansMedal = new User.FansMedal();
                            gift.user.fansMedal.name = fansMedal.get("medal_name").asText();
                            gift.user.fansMedal.level = fansMedal.get("medal_level").asInt();
                            if (gift.user.name.length() == 0)
                                gift.user.fansMedal = null;
                        }

                        gift.id = data.get("giftId").asInt();
                        gift.name = data.get("giftName").asText();
                        if (Objects.equals(data.get("coin_type").asText(), "gold"))
                            gift.price = data.get("total_coin").asInt() / 1000f;
                        else
                            gift.price = 0;
                        gift.num = data.get("num").asInt();
                        onGift(gift);
                    } catch (RuntimeException ex) {
                        LOGGER.error("解析礼物包出错", ex);
                    }
                }
                case "SUPER_CHAT_MESSAGE" -> {
                    try {
                        var data = msg.data;
                        if (data == null) {
                            LOGGER.warn("醒目留言包中没有data");
                            return;
                        }
                        var sc = new SuperChat();
                        sc.user.uid = data.get("uid").asInt();
                        sc.user.name = data.at("user_info/uname").asText();
                        sc.user.guardLevel = data.at("user_info/guard_level").asInt();

                        var fansMedal = data.get("medal_info");
                        if (fansMedal != null && !fansMedal.isNull()) {
                            sc.user.fansMedal = new User.FansMedal();
                            sc.user.fansMedal.name = fansMedal.get("medal_name").asText();
                            sc.user.fansMedal.level = fansMedal.get("medal_level").asInt();
                            if (sc.user.name.length() == 0)
                                sc.user.fansMedal = null;
                        }

                        sc.id = data.get("id").asInt();
                        sc.body = data.get("message").asText();
                        sc.price = data.get("price").asInt();
                        sc.time = data.get("time").asInt();
                        onSuperChat(sc);
                    } catch (RuntimeException ex) {
                        LOGGER.error("解析醒目留言包出错", ex);
                    }
                }
                case "USER_TOAST_MSG" -> {
                    try {
                        var data = msg.data;
                        if (data == null) {
                            LOGGER.warn("舰长包中没有data");
                            return;
                        }
                        var guard = new Guard();
                        guard.user.uid = data.get("uid").asInt();
                        guard.user.name = data.get("username").asText();
                        guard.user.guardLevel = data.get("guard_level").asInt();
                        guard.id = data.get("gift_id").asInt();
                        guard.name = data.get("role_name").asText();
                        guard.price = data.get("price").asInt() / 1000f;
                        guard.num = data.get("num").asInt();
                        guard.level = guard.user.guardLevel;
                        guard.unit = data.get("unit").asText();
                        onGuard(guard);
                    } catch (RuntimeException ex) {
                        LOGGER.error("解析舰长包出错", ex);
                    }
                }
                case "INTERACT_WORD" -> {
                    try {
                        var data = msg.data;
                        if (data == null) {
                            LOGGER.warn("互动包中没有data");
                            return;
                        }
                        var interactive = new Interactive();
                        interactive.user.uid = data.get("uid").asInt();
                        interactive.user.name = data.get("uname").asText();

                        var fansMedal = data.get("fans_medal");
                        if (fansMedal != null && !fansMedal.isNull()) {
                            interactive.user.fansMedal = new User.FansMedal();
                            interactive.user.fansMedal.name = fansMedal.get("medal_name").asText();
                            interactive.user.fansMedal.level = fansMedal.get("medal_level").asInt();
                            interactive.user.guardLevel = fansMedal.get("guard_level").asInt();
                            if (interactive.user.fansMedal.name.length() == 0)
                                interactive.user.fansMedal = null;
                        }

                        interactive.type = data.get("msg_type").asInt();
                        onInteractive(interactive);
                    } catch (RuntimeException ex) {
                        LOGGER.error("解析互动包出错", ex);
                    }
                }
            }
        }

    }

    @SuppressWarnings("unused")
    public void onDanmu(Danmu danmu) {
    }

    @SuppressWarnings("unused")
    public void onEmoji(Emoji emoji) {
    }

    @SuppressWarnings("unused")
    public void onGift(Gift gift) {
    }

    @SuppressWarnings("unused")
    public void onGuard(Guard guard) {
    }

    @SuppressWarnings("unused")
    public void onInteractive(Interactive interactive) {
    }

    @SuppressWarnings("unused")
    public void onSuperChat(SuperChat superChat) {
    }

    public void onOpen() {
    }

    public void onClose(int code, String reason, boolean remote) {
    }

    public abstract void onError(Exception ex);
}
