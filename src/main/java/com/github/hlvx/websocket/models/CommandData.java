package com.github.hlvx.websocket.models;

public class CommandData<D, C> {
    private final D data;
    private final C command;

    public CommandData(C command, D data) {
        this.command = command;
        this.data = data;
    }

    public C getCommand() {
        return command;
    }

    public D getData() {
        return data;
    }
}
