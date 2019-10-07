package com.github.hlvx.websocket.io.writers;

import io.vertx.core.buffer.Buffer;

public interface Writer {
    void writeData(Object data, Buffer out);
}
