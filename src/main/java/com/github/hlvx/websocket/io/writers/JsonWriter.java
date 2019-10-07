package com.github.hlvx.websocket.io.writers;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;

public class JsonWriter implements Writer {
    @Override
    public void writeData(Object data, Buffer out) {
        out.setBuffer(0, Json.encodeToBuffer(data));
    }
}
