package com.example.url;

import java.io.*;
import java.lang.reflect.Parameter;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import com.example.classe.*;
import com.example.controller.ScannerController;
import com.example.annotation.Request;

@WebServlet("/*")
public class UrlServlet extends HttpServlet {
    private Map<String, List<RouteInfo>> routes;

    @Override
    public void init() throws ServletException {
        try {
            String pkg = getServletConfig().getInitParameter("base-package");
            routes = ScannerController.scan(pkg);
            logRoutes();
        } catch (Exception e) {
            throw new ServletException("Erreur scan routes", e);
        }
    }

    private void logRoutes() {
        System.out.println("=== ROUTES ===");
        routes.forEach((url, list) -> {
            list.forEach(route -> System.out.println(route.getHttpMethod() + " " + url));
        });
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String path = req.getRequestURI().substring(req.getContextPath().length());

        // Ressources statiques
        if (getServletContext().getResource(path) != null) {
            getServletContext().getNamedDispatcher("default").forward(req, resp);
            return;
        }

        // Trouver route
        RouteInfo route = findRoute(path, req.getMethod());
        if (route == null) {
            resp.sendError(404, "Not Found: " + path);
            return;
        }

        // Exécuter
        try {
            Object result = executeRoute(route, req, resp);
            handleResult(result, req, resp);
        } catch (Exception e) {
            resp.sendError(500, "Server Error: " + e.getMessage());
        }
    }

    private RouteInfo findRoute(String path, String method) {
        // Route exacte
        if (routes.containsKey(path)) {
            return routes.get(path).stream()
                    .filter(r -> r.getHttpMethod().equalsIgnoreCase(method))
                    .findFirst()
                    .orElse(null);
        }

        // Route dynamique
        for (String pattern : routes.keySet()) {
            if (matchPattern(pattern, path)) {
                for (RouteInfo route : routes.get(pattern)) {
                    if (route.getHttpMethod().equalsIgnoreCase(method)) {
                        extractPathVars(route, pattern, path);
                        return route;
                    }
                }
            }
        }

        return null;
    }

    private boolean matchPattern(String pattern, String path) {
        String[] pParts = pattern.split("/");
        String[] rParts = path.split("/");

        if (pParts.length != rParts.length)
            return false;

        for (int i = 0; i < pParts.length; i++) {
            if (!pParts[i].startsWith("{") && !pParts[i].equals(rParts[i])) {
                return false;
            }
        }

        return true;
    }

    private void extractPathVars(RouteInfo route, String pattern, String path) {
        String[] pParts = pattern.split("/");
        String[] rParts = path.split("/");

        for (int i = 0; i < pParts.length; i++) {
            if (pParts[i].startsWith("{")) {
                String name = pParts[i].substring(1, pParts[i].length() - 1);
                route.addPathVar(name, rParts[i]);
            }
        }
    }

    private Object executeRoute(RouteInfo route, HttpServletRequest req, HttpServletResponse resp)
            throws Exception {

        // Préparer paramètres
        Object[] args = prepareArgs(route, req);

        // Exécuter méthode
        return route.getMethod().invoke(route.getController(), args);
    }

    private Object[] prepareArgs(RouteInfo route, HttpServletRequest req) {
        Parameter[] params = route.getMethod().getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            String name = getParamName(param);
            Class<?> type = param.getType();

            // Chercher valeur
            String value = findValue(name, route, req);
            args[i] = convertValue(value, type);
        }

        return args;
    }

    private String getParamName(Parameter param) {
        if (param.isAnnotationPresent(Request.class)) {
            return param.getAnnotation(Request.class).value();
        }
        return param.getName();
    }

    private String findValue(String name, RouteInfo route, HttpServletRequest req) {
        // 1. Variables de chemin
        if (route.getPathVars().containsKey(name)) {
            return route.getPathVars().get(name);
        }

        // 2. Paramètres de requête
        String param = req.getParameter(name);
        if (param != null) {
            return param;
        }

        return null;
    }

    private Object convertValue(String value, Class<?> type) {
        if (value == null) {
            return getDefaultValue(type);
        }

        return new Caste(value, type).getTypedValue();
    }

    private Object getDefaultValue(Class<?> type) {
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == boolean.class)
            return false;
        if (type == double.class)
            return 0.0;
        if (type == float.class)
            return 0.0f;
        return null;
    }

    private void handleResult(Object result, HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        if (result instanceof ModelVue) {
            ModelVue mv = (ModelVue) result;
            if (mv.getData() != null) {
                mv.getData().forEach(req::setAttribute);
            }
            req.getRequestDispatcher(mv.getView()).forward(req, resp);
        } else if (result instanceof String) {
            String str = (String) result;
            if (str.startsWith("redirect:")) {
                resp.sendRedirect(str.substring(9));
            } else {
                resp.getWriter().print(str);
            }
        } else if (result instanceof Map) {
            resp.setContentType("application/json");
            resp.getWriter().print(toJson((Map<?, ?>) result));
        } else if (result != null) {
            resp.getWriter().print(result.toString());
        }
    }

    private String toJson(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first)
                sb.append(",");
            first = false;

            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();

            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }
        }

        return sb.append("}").toString();
    }
}