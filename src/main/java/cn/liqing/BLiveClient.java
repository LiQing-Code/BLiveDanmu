package cn.liqing;

import cn.liqing.model.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class BLiveClient extends WebSocketClient {
    static final Logger LOGGER = LoggerFactory.getLogger(BLiveClient.class);

    public int room;
    public ArrayList<DanmuHandler> danmuHandlers = new ArrayList<>();

    public BLiveClient() {
        super(URI.create("wss://broadcastlv.chat.bilibili.com:2245/sub"));
    }

    public BLiveClient(int room) {
        this();
        this.room = room;
    }

    public void send(Operation operation, byte[] body) {
        send(new Packet(operation, body).pack());
    }

    public void send(Operation operation, Object body) {
        send(operation, new Gson().toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    @Nullable Timer heartbeatTimer;

    @SuppressWarnings("all")
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        LOGGER.info("发送认证包，room:{}", room);
        send(Operation.AUTH, new Auth(room));

        heartbeatTimer = new Timer();
        heartbeatTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                LOGGER.info("发送心跳包，room:{}", room);
                send(Operation.HEARTBEAT, new byte[0]);
            }
        }, 0, 20 * 1000);
        danmuHandlers.stream().anyMatch(handler -> handler.onOpen());
    }

    @SuppressWarnings("all")
    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
        danmuHandlers.stream().anyMatch(handler -> handler.onClose(code, reason, remote));
    }

    public void onPacket(@NotNull Packet packet) {
        for (var handler : danmuHandlers) {
            if (handler.onPacket(packet))
                break;
        }
        if (packet.operation == Operation.SEND_SMS_REPLY) {
            String bodyStr = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(packet.body)).toString();
            //LOGGER.info(bodyStr);
            var json = new Gson().fromJson(bodyStr, JsonObject.class);
            String cmd = json.get("cmd").getAsString();

            switch (cmd) {
                case "DANMU_MSG" -> {
                    try {
                        JsonArray info = json.get("info").getAsJsonArray();
                        var danmu = new Danmu();
                        danmu.user.uid = info.get(2).getAsJsonArray().get(0).getAsInt();
                        danmu.user.name = info.get(2).getAsJsonArray().get(1).getAsString();
                        danmu.user.guardLevel = info.get(7).getAsShort();

                        var fansMedal = info.get(3).getAsJsonArray();
                        if (fansMedal != null && fansMedal.size() >= 2) {
                            danmu.user.fansMedal = new User.FansMedal();
                            danmu.user.fansMedal.level = info.get(3).getAsJsonArray().get(0).getAsInt();
                            danmu.user.fansMedal.name = info.get(3).getAsJsonArray().get(1).getAsString();
                        }

                        danmu.body = info.get(1).getAsString();
                        int isEmoji = info.get(0).getAsJsonArray().get(12).getAsInt();
                        if (isEmoji == 1) {
                            var emoji = new Emoji();
                            emoji.user = danmu.user;
                            emoji.body = danmu.body;
                            emoji.uri = info.get(0).getAsJsonArray().get(13)
                                    .getAsJsonObject().get("url").getAsString();
                            boolean ignore = danmuHandlers.stream()
                                    .anyMatch(danmuHandler -> danmuHandler.onEmoji(emoji));
                        } else {
                            boolean ignore = danmuHandlers.stream()
                                    .anyMatch(danmuHandler -> danmuHandler.onDanmu(danmu));
                        }
                    } catch (RuntimeException ex) {
                        LOGGER.error("解析弹幕包出错", ex);
                    }
                }
                case "SEND_GIFT" -> {
                    try {
                        JsonObject data = json.get("data").getAsJsonObject();
                        var gift = new Gift();
                        gift.user.uid = data.get("uid").getAsInt();
                        gift.user.name = data.get("uname").getAsString();
                        gift.user.guardLevel = data.get("guard_level").getAsShort();

                        var fansMedal = data.get("medal_info").getAsJsonObject();
                        if (fansMedal != null) {
                            gift.user.fansMedal = new User.FansMedal();
                            gift.user.fansMedal.name = fansMedal.get("medal_name").getAsString();
                            gift.user.fansMedal.level = fansMedal.get("medal_level").getAsInt();
                            if (gift.user.name.length() == 0)
                                gift.user.fansMedal = null;
                        }

                        gift.id = data.get("giftId").getAsInt();
                        gift.name = data.get("giftName").getAsString();
                        if (Objects.equals(data.get("coin_type").getAsString(), "gold"))
                            gift.price = data.get("total_coin").getAsFloat() / 1000;
                        else
                            gift.price = 0;
                        gift.num = data.get("num").getAsInt();
                        boolean ignore = danmuHandlers.stream().anyMatch(danmuHandler -> danmuHandler.onGift(gift));
                    } catch (RuntimeException ex) {
                        LOGGER.error("解析礼物包出错", ex);
                    }
                }
                case "SUPER_CHAT_MESSAGE" -> {
                    try {
                        JsonObject data = json.get("data").getAsJsonObject();
                        var sc = new SuperChat();
                        sc.user.uid = data.get("uid").getAsInt();
                        sc.user.name = data.get("user_info").getAsJsonObject()
                                .get("uname").getAsString();
                        sc.user.guardLevel = data.get("user_info").getAsJsonObject()
                                .get("guard_level").getAsShort();

                        var fansMedal = data.get("medal_info").getAsJsonObject();
                        if (fansMedal != null) {
                            sc.user.fansMedal = new User.FansMedal();
                            sc.user.fansMedal.name = fansMedal.get("medal_name").getAsString();
                            sc.user.fansMedal.level = fansMedal.get("medal_level").getAsInt();
                            if (sc.user.name.length() == 0)
                                sc.user.fansMedal = null;
                        }

                        sc.id = data.get("id").getAsInt();
                        sc.body = data.get("message").getAsString();
                        sc.price = data.get("price").getAsInt();
                        sc.time = data.get("time").getAsInt();
                        boolean ignore = danmuHandlers.stream().anyMatch(danmuHandler -> danmuHandler.onSuperChat(sc));
                    } catch (RuntimeException ex) {
                        LOGGER.error("解析醒目留言包出错", ex);
                    }
                }
                case "USER_TOAST_MSG" -> {
                    try {
                        JsonObject data = json.get("data").getAsJsonObject();
                        var guard = new Guard();
                        guard.user.uid = data.get("uid").getAsInt();
                        guard.user.name = data.get("uname").getAsString();
                        guard.user.guardLevel = data.get("guard_level").getAsShort();
                        guard.id = data.get("gift_id").getAsInt();
                        guard.name = data.get("role_name").getAsString();
                        guard.price = data.get("price").getAsFloat() / 1000;
                        guard.num = data.get("num").getAsInt();
                        guard.level = guard.user.guardLevel;
                        guard.unit = data.get("unit").getAsString();
                        boolean ignore = danmuHandlers.stream().anyMatch(danmuHandler -> danmuHandler.onGuard(guard));
                    } catch (RuntimeException ex) {
                        LOGGER.error("解析舰长包出错", ex);
                    }
                }
                case "INTERACT_WORD" -> {
                    try {
                        JsonObject data = json.get("data").getAsJsonObject();
                        var interactive = new Interactive();
                        interactive.user.uid = data.get("uid").getAsInt();
                        interactive.user.name = data.get("uname").getAsString();

                        var fansMedal = data.get("fans_medal").getAsJsonObject();
                        if (fansMedal != null) {
                            interactive.user.fansMedal = new User.FansMedal();
                            interactive.user.fansMedal.name = fansMedal.get("medal_name").getAsString();
                            interactive.user.fansMedal.level = fansMedal.get("medal_level").getAsInt();
                            interactive.user.guardLevel = fansMedal.get("guard_level").getAsShort();
                            if (interactive.user.fansMedal.name.length() == 0)
                                interactive.user.fansMedal = null;
                        }

                        interactive.type = data.get("msg_type").getAsInt();
                        boolean ignore = danmuHandlers.stream().anyMatch(danmuHandler -> danmuHandler.onInteractive(interactive));
                    } catch (RuntimeException ex) {
                        LOGGER.error("解析互动包出错", ex);
                    }
                }
            }

        }
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        Packet.unPack(bytes).forEach(this::onPacket);
    }

    @Override
    public void onMessage(String message) {
    }

    @SuppressWarnings("all")
    @Override
    public void onError(Exception ex) {
        danmuHandlers.stream().anyMatch(handler -> handler.onError(ex));
    }
}
