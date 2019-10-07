package com.github.hlvx.websocket.servers;

import com.github.hlvx.websocket.annotations.BinaryCommand;
import com.github.hlvx.websocket.annotations.Context;
import com.github.hlvx.websocket.annotations.PermissionsAllowed;
import com.github.hlvx.websocket.annotations.TextCommand;
import com.github.hlvx.websocket.exceptions.BadPermissionsException;
import com.github.hlvx.websocket.exceptions.CommandNotRegisteredException;
import com.github.hlvx.websocket.io.readers.JsonReader;
import com.github.hlvx.websocket.io.readers.Reader;
import com.github.hlvx.websocket.io.readers.SimpleBinaryReader;
import com.github.hlvx.websocket.io.writers.JsonWriter;
import com.github.hlvx.websocket.io.writers.SimpleBinaryWriter;
import com.github.hlvx.websocket.io.writers.Writer;
import com.github.hlvx.websocket.models.Command;
import com.github.hlvx.websocket.models.CommandData;
import com.github.hlvx.websocket.models.RequestContext;
import com.github.hlvx.websocket.models.WebSocketContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.auth.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class WebSocketServer {
    private Logger LOGGER = LoggerFactory.getLogger(WebSocketServer.class);
    private Map<ServerWebSocket, WebSocketContext> contexts = new ConcurrentHashMap<>();
    private Map<String, Command> textCommandHandlers = new HashMap<>();
    private Map<Integer, Command> binaryCommandHandlers = new HashMap<>();
    private BiConsumer<ServerWebSocket, Handler<AsyncResult<Integer>>> handshakeHandler;
    private Handler<ServerWebSocket> connectHandler;
    private Handler<ServerWebSocket> disconnectHandler;
    private Reader<String> textReader = new JsonReader("type");
    private Reader<Buffer> binaryReader = new SimpleBinaryReader();
    private Writer textWriter = new JsonWriter();
    private Writer binaryWriter = new SimpleBinaryWriter();

    public void addServices(Object...services) {
        for (Object service : services) {
            Set<String> servicePermissions;
            PermissionsAllowed permissions = service.getClass().getAnnotation(PermissionsAllowed.class);
            if (permissions != null) servicePermissions = Arrays.stream(permissions.permissions())
                    .collect(Collectors.toSet());
            else servicePermissions = Collections.EMPTY_SET;
            for (Method method : service.getClass().getMethods()) {
                Set<String> methodPermissions;
                permissions = service.getClass().getAnnotation(PermissionsAllowed.class);
                if (permissions != null) methodPermissions = Arrays.stream(permissions.permissions())
                        .collect(Collectors.toSet());
                else methodPermissions = Collections.EMPTY_SET;
                methodPermissions.addAll(servicePermissions);

                BinaryCommand binaryCommand = method.getAnnotation(BinaryCommand.class);
                if (binaryCommand != null) {
                    LOGGER.info("Registered {} as a BinaryCommand", method);
                    binaryCommandHandlers.put(binaryCommand.commandId(),
                            new Command(service, method, methodPermissions));
                } else {
                    LOGGER.info("Registered {} as a TextCommand", method);
                    TextCommand textCommand = method.getAnnotation(TextCommand.class);
                    if (textCommand != null) textCommandHandlers.put(textCommand.command(),
                            new Command(service, method, methodPermissions));
                }
            }
        }
    }

    public void start(int port, Handler<AsyncResult<HttpServer>> handler) {
        Vertx.vertx().createHttpServer()
                .websocketHandler(serverWebSocket -> {
                    if (connectHandler != null) connectHandler.handle(serverWebSocket);
                    Promise<Integer> promise = Promise.promise();
                    serverWebSocket.setHandshake(promise);
                    if (handshakeHandler != null) handshakeHandler.accept(serverWebSocket, promise);
                    else promise.complete(101);
                    contexts.put(serverWebSocket, new WebSocketContext(this, serverWebSocket));

                    serverWebSocket.closeHandler(v -> {
                        contexts.remove(serverWebSocket);
                        if (disconnectHandler != null) disconnectHandler.handle(serverWebSocket);
                    });
                    serverWebSocket.textMessageHandler(txt -> handleTextMessage(serverWebSocket, txt));
                    serverWebSocket.binaryMessageHandler(buffer -> handleBinaryMessage(serverWebSocket, buffer));
                }).listen(port, handler);
    }

    private void handleBinaryMessage(ServerWebSocket client, Buffer buffer) {
        messageHandle(new RequestContext(contexts.get(client), buffer, binaryWriter, binaryReader),
                binaryCommandHandlers,
                ((websocket, data) -> {
            websocket.writeBinaryMessage(data);
        }));
    }

    private void handleTextMessage(ServerWebSocket client, String text) {
        messageHandle(new RequestContext(contexts.get(client), Buffer.buffer(text), textWriter, textReader),
                textCommandHandlers,
                ((websocket, data) -> {
            websocket.writeTextMessage(data.toString());
        }));
    }

    private CompletableFuture<Boolean> checkPermissions(Command command, RequestContext requestContext) {
        User user = requestContext.getWebSocketContext().getUser();
        if (!command.getPermissions().isEmpty() && user == null)
            throw new BadPermissionsException("User not set.");
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        if (!command.getPermissions().isEmpty()) {
            CompletableFuture<Boolean>[] futures = new CompletableFuture[command.getPermissions().size()];
            int i = 0;
            for (String perm : command.getPermissions()) {
                CompletableFuture<Boolean> future = new CompletableFuture();
                futures[i++] = future;
                user.isAuthorized(perm, authorizedResult -> {
                    if (authorizedResult.succeeded()) future.complete(authorizedResult.result());
                    else future.completeExceptionally(authorizedResult.cause());
                });
            }
            CompletableFuture.allOf(futures)
                    .thenAccept(v -> {
                        resultFuture.complete(Arrays.stream(futures)
                                .map(future -> future.join())
                                .allMatch(e -> e));
                    }).exceptionally(ex -> {
                resultFuture.completeExceptionally(ex.getCause());
                return null;
            });
        } else return CompletableFuture.completedFuture(true);
        return resultFuture;
    }

    private void messageHandle(RequestContext requestContext, Map<?, Command> commandMap, ClientWriter clientWriter) {
        CommandData commandData = requestContext.getReader().readData(requestContext.getData());
        requestContext.setCommandData(commandData);
        Command command = commandMap.get(commandData.getCommand());
        if (command == null)
            throw new CommandNotRegisteredException(commandData.getCommand() + " is not a registered command.");
        requestContext.registerObject(requestContext.getWebSocketContext().getUser());
        checkPermissions(command, requestContext).thenAccept(e -> {
            if (!e) {
                Vertx.currentContext().runOnContext(ctx -> {
                    throw new RuntimeException(new BadPermissionsException("User not authorized to do that."));
                });
                return;
            }
            if (command.getReturnType().equals(Future.class)) processAsync(command, requestContext, clientWriter);
            else processBlocking(command, requestContext, clientWriter);
        }).exceptionally(ex -> {
            Vertx.currentContext().runOnContext(e -> {
                throw new RuntimeException(ex);
            });
            return null;
        });
    }

    private void processAsync(Command command, RequestContext requestContext, ClientWriter clientWriter) {
        try {
            ((Future<?>) command.invoke(requestContext,
                    new Object[] { requestContext.getCommandData().getData() })).setHandler(result -> {
                if (result.failed()) throw new RuntimeException(result.cause());
                Buffer buffer = Buffer.buffer();
                requestContext.getWriter().writeData(result.result(), buffer);
                clientWriter.write(requestContext.getWebSocketContext().getClient(), buffer);
            });
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void processBlocking(Command command, RequestContext requestContext, ClientWriter clientWriter) {
        Vertx.vertx().executeBlocking(promise -> {
            try {
                promise.complete(command.invoke(requestContext,
                        new Object[] { requestContext.getCommandData().getData() }));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }, result -> {
            if (result.failed()) throw new RuntimeException(result.cause());
            Buffer buffer = Buffer.buffer();
            requestContext.getWriter().writeData(result.result(), buffer);
            clientWriter.write(requestContext.getWebSocketContext().getClient(), buffer);
        });
    }

    public BiConsumer<ServerWebSocket, Handler<AsyncResult<Integer>>> getHandshakeHandler() {
        return handshakeHandler;
    }

    public void setHandshakeHandler(BiConsumer<ServerWebSocket, Handler<AsyncResult<Integer>>> handshakeHandler) {
        this.handshakeHandler = handshakeHandler;
    }

    private interface ClientWriter {
        void write(ServerWebSocket websocket, Buffer data);
    }

    public Reader<Buffer> getBinaryReader() {
        return binaryReader;
    }

    public void setBinaryReader(Reader<Buffer> binaryReader) {
        this.binaryReader = binaryReader;
    }

    public Writer getBinaryWriter() {
        return binaryWriter;
    }

    public void setBinaryWriter(Writer binaryWriter) {
        this.binaryWriter = binaryWriter;
    }

    public Reader<String> getTextReader() {
        return textReader;
    }

    public void setTextReader(Reader<String> textReader) {
        this.textReader = textReader;
    }

    public Writer getTextWriter() {
        return textWriter;
    }

    public void setTextWriter(Writer textWriter) {
        this.textWriter = textWriter;
    }

    public void setConnectHandler(Handler<ServerWebSocket> connectHandler) {
        this.connectHandler = connectHandler;
    }

    public void setDisconnectHandler(Handler<ServerWebSocket> disconnectHandler) {
        this.disconnectHandler = disconnectHandler;
    }

    public Handler<ServerWebSocket> getConnectHandler() {
        return connectHandler;
    }

    public Handler<ServerWebSocket> getDisconnectHandler() {
        return disconnectHandler;
    }
}
