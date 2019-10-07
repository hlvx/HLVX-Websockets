package com.github.hlvx.websocket.io.writers;

import io.vertx.core.buffer.Buffer;

public class SimpleBinaryWriter implements Writer {
    @Override
    public void writeData(Object data, Buffer out) {
        if (data instanceof Buffer) out.setBuffer(0, (Buffer) data);
        else throw new RuntimeException("SimpleBinaryWriter only supports Buffer");
    }
}
