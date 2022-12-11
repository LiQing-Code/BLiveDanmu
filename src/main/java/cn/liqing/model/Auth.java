package cn.liqing.model;

@SuppressWarnings("unused")
public class Auth {
    public final int uid = 0;
    public final int roomid;
    public final int protover = 2;
    public final String platform = "web";
    public final int type = 2;

    public Auth(int roomid) {
        this.roomid = roomid;
    }
}
