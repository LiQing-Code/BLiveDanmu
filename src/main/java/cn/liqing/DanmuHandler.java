package cn.liqing;

import cn.liqing.model.*;

public interface DanmuHandler {
    /**
     * 收到数据包时会执行这个方法，返回 true 会取消执行后续Handler
     */
    default boolean onPacket(Packet packet) {
        return false;
    }

    /**
     * 收到弹幕时会执行这个方法，返回 true 会取消执行后续Handler
     */
    default boolean onDanmu(Danmu danmu) {
        return false;
    }

    default boolean onEmoji(Emoji emoji) {
        return false;
    }

    default boolean onGift(Gift gift) {
        return false;
    }

    default boolean onSuperChat(SuperChat sc) {
        return false;
    }

    default boolean onGuard(Guard guard) {
        return false;
    }

    default boolean onInteractive(Interactive interactive) {
        return false;
    }

    default boolean onOpen() {
        return false;
    }

    default boolean onClose(int code, String reason, boolean remote) {
        return false;
    }

    default boolean onError(Exception ex) {
        return false;
    }
}
