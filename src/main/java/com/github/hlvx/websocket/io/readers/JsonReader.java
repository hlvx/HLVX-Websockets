package com.github.hlvx.websocket.io.readers;

import com.github.hlvx.websocket.models.CommandData;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public class JsonReader implements Reader<String> {
    private final String commandKey;

    public JsonReader(String commandKey) {
        this.commandKey = commandKey;
    }

    @Override
    public CommandData<JsonObject, String> readData(Buffer data) {
        JsonObject json = new JsonObject(data);
        return new CommandData<JsonObject, String>(json.getString(commandKey), json);
    }
}
