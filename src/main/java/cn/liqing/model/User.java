package cn.liqing.model;

import org.jetbrains.annotations.Nullable;

public class User {
    public long uid;
    public String name;
    public @Nullable FansMedal fansMedal;

    /**
     * 0.无 1.总督 2.提督 3.舰长
     */
    public int guardLevel;

    public static class FansMedal {
        public String name;
        public int level;
    }
}
