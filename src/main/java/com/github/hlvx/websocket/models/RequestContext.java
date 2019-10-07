package com.github.hlvx.websocket.models;

import com.github.hlvx.websocket.io.readers.Reader;
import com.github.hlvx.websocket.io.writers.Writer;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;

import java.util.HashMap;
import java.util.Map;

public class RequestContext {
    private final Buffer data;
    private final WebSocketContext webSocketContext;
    private final Writer writer;
    private final Reader reader;
    private CommandData commandData;
    private final Map<Class<?>, Object> registeredObjects = new HashMap<>();

    public RequestContext(WebSocketContext context, Buffer data, Writer writer, Reader reader) {
        this.data = data;
        this.webSocketContext = context;
        this.writer = writer;
        this.reader = reader;
        registerObject(this);
        registerObject(webSocketContext);
    }

    public Buffer getData() {
        return data;
    }

    public WebSocketContext getWebSocketContext() {
        return webSocketContext;
    }

    public Writer getWriter() {
        return writer;
    }

    public Reader getReader() {
        return reader;
    }

    public CommandData getCommandData() {
        return commandData;
    }

    public void setCommandData(CommandData commandData) {
        this.commandData = commandData;
    }

    public void registerObject(Object obj) {
        registeredObjects.put(obj.getClass(), obj);
    }

    public Object getRegisteredObject(Class<?> objType) {
        return registeredObjects.get(objType);
    }
}
