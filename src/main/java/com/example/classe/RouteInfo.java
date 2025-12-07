package com.example.classe;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RouteInfo {
    private Object controller;
    private Method method;
    private String url;
    private String httpMethod;
    private Map<String, String> pathVars = new HashMap<>();
    
    public RouteInfo(Object controller, Method method, String url, String httpMethod) {
        this.controller = controller;
        this.method = method;
        this.url = url;
        this.httpMethod = httpMethod;
    }
    
    // Getters seulement
    public Object getController() { return controller; }
    public Method getMethod() { return method; }
    public String getUrl() { return url; }
    public String getHttpMethod() { return httpMethod; }
    public Map<String, String> getPathVars() { return pathVars; }
    
    public void addPathVar(String name, String value) {
        pathVars.put(name, value);
    }
    
    public boolean isDynamic() {
        return url.contains("{");
    }
}