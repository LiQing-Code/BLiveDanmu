package cn.liqing;

import cn.liqing.model.Packet;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

class BLiveClientTest {

    //@Test
    void connect() throws InterruptedException {
        BLiveClient client = new BLiveClient() {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                System.out.println("已连接");
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("已断开");
            }

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }

            @Override
            public void onPacket(@NotNull Packet packet) {
                System.out.println(packet.operation);
                System.out.println(new String(packet.body, StandardCharsets.UTF_8));
            }
        };
        client.connect(545068);

        while (true)
            Thread.sleep(100);
    }
}