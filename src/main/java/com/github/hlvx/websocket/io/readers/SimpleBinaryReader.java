package com.github.hlvx.websocket.io.readers;

import com.github.hlvx.websocket.models.CommandData;
import io.vertx.core.buffer.Buffer;

public class SimpleBinaryReader implements Reader<Buffer> {
    @Override
    public CommandData<Short, Buffer> readData(Buffer data) {
        return new CommandData(data.getShort(0), data);
    }
}
