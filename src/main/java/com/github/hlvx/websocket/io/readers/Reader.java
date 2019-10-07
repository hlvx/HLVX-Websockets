package com.github.hlvx.websocket.io.readers;

import com.github.hlvx.websocket.models.CommandData;
import io.vertx.core.buffer.Buffer;

public interface Reader<T> {
    CommandData readData(Buffer data);
}
