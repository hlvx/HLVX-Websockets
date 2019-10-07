package com.github.hlvx.websocket.models;

import com.github.hlvx.websocket.annotations.Context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Command {
    private final Object parent;
    private final Method method;
    private final Set<String> permissions;
    private final Class<?> returnType;
    private final Map<Integer, Class<?>> contextParams = new HashMap<>();
    private final int paramsCount;

    public Command(Object parent, Method method, Set<String> permissions) {
        this.parent = parent;
        this.method = method;
        this.permissions = permissions;
        returnType = method.getReturnType();

        paramsCount = method.getParameters().length;
        for (int i = 0; i < paramsCount; ++i) {
            Parameter param = method.getParameters()[i];
            if (param.isAnnotationPresent(Context.class))
                contextParams.put(i, param.getType());
        }
    }

    public Class<?> getReturnType() {
        return returnType;
    }

    public Object getParent() {
        return parent;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public Object invoke(RequestContext context, Object[] args)
            throws InvocationTargetException, IllegalAccessException {
        if (args.length + contextParams.size() != paramsCount)
            throw new RuntimeException("Invalid number of parameters for method " + method);
        Object[] params = new Object[paramsCount];
        int j = 0;
        for (int i = 0; i < paramsCount; ++i) {
            Class<?> contextParam = this.contextParams.get(i);
            if (contextParam != null) params[i] = context.getRegisteredObject(contextParam);
            else params[i] = args[j++];
        }
        return method.invoke(parent, params);
    }
}
