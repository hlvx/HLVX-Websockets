package com.github.hlvx.websocket.models;

import java.lang.reflect.Method;
import java.util.Set;

public class Command {
    private final Object parent;
    private final Method method;
    private final Set<String> permissions;
    private final Class<?> returnType;

    public Command(Object parent, Method method, Set<String> permissions) {
        this.parent = parent;
        this.method = method;
        this.permissions = permissions;
        returnType = method.getReturnType();
    }

    public Class<?> getReturnType() {
        return returnType;
    }

    public Method getMethod() {
        return method;
    }

    public Object getParent() {
        return parent;
    }

    public Set<String> getPermissions() {
        return permissions;
    }
}
