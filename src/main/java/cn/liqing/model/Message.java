package cn.liqing.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.Nullable;

public class Message {
    public @Nullable String cmd;
    public @Nullable JsonNode data;
    public @Nullable JsonNode info;
}
