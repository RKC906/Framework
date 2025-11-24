package com.example.classe;

import java.lang.reflect.Method;
import java.util.Map;

public class RouteDefinition {
    private String url;
    private Method method;
    private Class<?> controllerClass;
    private Map<String, Class<?>> paramTypes;

    public RouteDefinition(String url, Method method, Class<?> controllerClass, Map<String, Class<?>> paramTypes) {
        this.url = url;
        this.method = method;
        this.controllerClass = controllerClass;
        this.paramTypes = paramTypes;
    }

    public String getUrl() {
        return url;
    }

    public Method getMethod() {
        return method;
    }

    public Class<?> getControllerClass() {
        return controllerClass;
    }

    public Map<String, Class<?>> getParamTypes() {
        return paramTypes;
    }
}
