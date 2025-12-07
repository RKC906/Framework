package com.example.classe;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RouteInfo {

    private Object controllerInstance;
    private Method method;
    private String urlPattern;
    private Map<String, String> pathVariables;
    private Map<String, String[]> requestParameters;

    public RouteInfo(Object controllerInstance, Method method, String urlPattern) {
        this.controllerInstance = controllerInstance;
        this.method = method;
        this.urlPattern = urlPattern;
        this.pathVariables = new HashMap<>();
        this.requestParameters = new HashMap<>();
    }

    public Object getControllerInstance() {
        return controllerInstance;
    }

    public Method getMethod() {
        return method;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    // Gestion des variables de chemin
    public void addPathVariable(String name, String value) {
        this.pathVariables.put(name, value);
    }

    public String getPathVariable(String name) {
        return this.pathVariables.get(name);
    }

    public Map<String, String> getPathVariables() {
        return new HashMap<>(this.pathVariables);
    }

    // Gestion des paramètres de requête
    public void addRequestParameter(String name, String[] values) {
        this.requestParameters.put(name, values);
    }

    public String[] getRequestParameter(String name) {
        return this.requestParameters.get(name);
    }

    public Map<String, String[]> getRequestParameters() {
        return new HashMap<>(this.requestParameters);
    }

    // Méthode utilitaire pour récupérer une valeur unique
    public String getParameterValue(String paramName) {
        // Priorité 1: variable de chemin
        if (pathVariables.containsKey(paramName)) {
            return pathVariables.get(paramName);
        }

        // Priorité 2: premier paramètre de requête
        if (requestParameters.containsKey(paramName) &&
                requestParameters.get(paramName) != null &&
                requestParameters.get(paramName).length > 0) {
            return requestParameters.get(paramName)[0];
        }

        return null;
    }

    // Vérifier si l'URL pattern est dynamique
    public boolean isDynamic() {
        return urlPattern.contains("{");
    }

    // Générer une regex à partir du pattern
    public String toRegex() {
        return "^" + urlPattern.replaceAll("\\{[^/]+\\}", "([^/]+)") + "$";
    }
}