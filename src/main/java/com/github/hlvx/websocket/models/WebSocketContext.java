package com.github.hlvx.websocket.models;

import com.github.hlvx.websocket.servers.WebSocketServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.auth.User;

import java.util.HashMap;
import java.util.Map;

public class WebSocketContext {
    private User user;
    private final WebSocketServer server;
    private final ServerWebSocket client;

    public WebSocketContext(WebSocketServer webSocketServer, ServerWebSocket client) {
        this.server = webSocketServer;
        this.client = client;
    }

    public ServerWebSocket getClient() {
        return client;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public WebSocketServer getServer() {
        return server;
    }
}
