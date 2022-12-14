package cn.liqing;

import cn.liqing.model.Operation;
import cn.liqing.model.Packet;
import cn.liqing.model.SuperChat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

class BLiveClientTest {

    //@Test
    void connect() throws InterruptedException {
        BLiveClient client = new BLiveClient() {
            @Override
            public void onOpen() {
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

    @Test
    void onPacket() throws IOException {
        BLiveClient client = new BLiveClient() {

            @Override
            public void onSuperChat(SuperChat superChat) {

            }

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };
        File file = new File("src/test/resources/test.json");
        var json = new ObjectMapper().readValue(file, JsonNode.class);
        for (int i = 0; i < json.size() - 1; i++) {
            var body = json.get(i).toString().getBytes(StandardCharsets.UTF_8);
            client.onPacket(new Packet(Operation.SEND_SMS_REPLY, body));
        }
    }
}