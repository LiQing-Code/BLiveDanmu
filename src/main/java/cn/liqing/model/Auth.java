package cn.liqing.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.UUID;

@SuppressWarnings("unused")
public class Auth {
    public final int uid;
    public final int roomid;
    public final int protover = 2;
    public final String buvid;
    public final String platform = "web";
    public final int type = 2;
    public final String key;

    public Auth(int roomid, int uid, String buvid, String key) {
        this.roomid = roomid;
        this.uid = uid;
        this.buvid = buvid;
        this.key = key;
    }

    public static Auth create(int roomid, String cookie) throws IOException, URISyntaxException, InterruptedException {
        String key = GetKey(roomid, cookie);
        //String key = "y3o_9IwCMNg9hiaAT9_UcmiME0mVhVDmPjIas2csafHbjpJuhevLddB5oCvaGO-JxgVnf0zE2v4YVme0Yhs--3l6L74nXdvVlhNbmc2Xz2sSCGrhAd0V-PbOlU_ZV2yB6MNxucKhuXuXljJaT5lHZ7X-28vwU7NaINwbb2KFbHpnO0eM82I5o8GYMQ==";
        String buvid = extractCookieValue("buvid3", cookie);
        //String buvid = GeneratedUUID();
        int uid = Integer.parseInt(Objects.requireNonNull(extractCookieValue("DedeUserID", cookie)));
        return new Auth(roomid, uid, buvid, key);
    }

    public static Auth create(int roomid) throws IOException, URISyntaxException, InterruptedException {
        String key = GetKey(roomid, "");
        String buvid = GeneratedUUID();
        return new Auth(roomid, 0, buvid, key);
    }

    private static String GetKey(int roomId, String cookie) throws IOException, InterruptedException, URISyntaxException {
        HttpClient client = HttpClient.newHttpClient();
        String wsInfoURL = "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo?id=%s&type=0";
        URI uri = new URI(String.format(wsInfoURL, roomId));
        
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept","*/*")
                .header("Cookie", cookie).build();
        // 输出请求信息
        System.out.println("Request URI: " + request.uri());
        System.out.println("Request Headers: " + request.headers());
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode rootNode = new ObjectMapper().readTree(response.body());
        return rootNode.path("data").path("token").asText();
    }

    private static String extractCookieValue(String cookieName, String cookieString) {
        String[] cookies = cookieString.split(";");

        for (String cookie : cookies) {
            cookie = cookie.trim(); // 去除首尾空格

            if (cookie.startsWith(cookieName + "=")) {
                // 找到对应的 Cookie，提取值
                return cookie.substring(cookieName.length() + 1); // 加上等号长度
            }
        }

        // 没有找到对应的 Cookie
        return null;
    }

    // 生成随机的UUID
    private static String GeneratedUUID() {
        UUID uuid = UUID.randomUUID();

        // 获取当前时间的毫秒数，用来生成后缀
        long currentTimeMillis = System.currentTimeMillis();
        String suffix = String.format("%05d", currentTimeMillis % 100000); // 取后五位，不足五位补零

        // 拼接结果
        return String.format("%s-%s-%s-%s-%s%sinfoc",
                uuid.toString().substring(0, 8),
                uuid.toString().substring(9, 13),
                uuid.toString().substring(14, 18),
                uuid.toString().substring(19, 23),
                uuid.toString().substring(24, 36),
                suffix);
    }
}
