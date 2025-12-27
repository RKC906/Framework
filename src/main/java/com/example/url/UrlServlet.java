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
    private Map<String, List<ScannerController.RouteData>> routes;
    private Map<String, Map<String, String>> pathVarsCache = new HashMap<>();

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
            list.forEach(route -> System.out.println(route.httpMethod + " " + url));
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
        ScannerController.RouteData route = findRoute(path, req.getMethod());
        if (route == null) {
            resp.sendError(404, "Not Found: " + path);
            return;
        }

        // Exécuter
        try {
            Object result = executeRoute(route, req, resp, path);
            handleResult(result, req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.sendError(500, "Server Error: " + e.getMessage());
        }
    }

    private ScannerController.RouteData findRoute(String path, String method) {
        // Route exacte
        if (routes.containsKey(path)) {
            return routes.get(path).stream()
                    .filter(r -> r.httpMethod.equalsIgnoreCase(method))
                    .findFirst()
                    .orElse(null);
        }

        // Route dynamique
        for (String pattern : routes.keySet()) {
            if (matchPattern(pattern, path)) {
                for (ScannerController.RouteData route : routes.get(pattern)) {
                    if (route.httpMethod.equalsIgnoreCase(method)) {
                        // Extraire et stocker les variables d'URL
                        extractAndCachePathVars(pattern, path);
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

    private void extractAndCachePathVars(String pattern, String path) {
        String[] pParts = pattern.split("/");
        String[] rParts = path.split("/");
        Map<String, String> vars = new HashMap<>();

        for (int i = 0; i < pParts.length; i++) {
            if (pParts[i].startsWith("{")) {
                String name = pParts[i].substring(1, pParts[i].length() - 1);
                vars.put(name, rParts[i]);
            }
        }

        pathVarsCache.put(path, vars);
    }

    private Object executeRoute(ScannerController.RouteData route,
            HttpServletRequest req,
            HttpServletResponse resp,
            String path) throws Exception {

        // Préparer paramètres
        Object[] args = prepareArgs(route, req, path);

        // Exécuter méthode
        return route.method.invoke(route.controller, args);
    }

    private Object[] prepareArgs(ScannerController.RouteData route,
            HttpServletRequest req,
            String path) {
        Parameter[] params = route.method.getParameters();
        Object[] args = new Object[params.length];

        // Récupérer toutes les valeurs disponibles
        Map<String, String> pathVars = pathVarsCache.get(path);
        Map<String, String[]> reqParams = req.getParameterMap();
        Map<String, Object> allValues = new HashMap<>();

        // 1. Ajouter les variables d'URL
        if (pathVars != null) {
            pathVars.forEach((key, value) -> allValues.put(key, value));
        }

        // 2. Ajouter les paramètres de requête
        reqParams.forEach((key, values) -> {
            if (values != null && values.length > 0) {
                allValues.put(key, values[0]); // Prendre première valeur
            }
        });

        // 3. Vérifier si un paramètre est Map<String, Object>
        boolean hasMapParam = false;
        for (Parameter param : params) {
            if (isMapStringObject(param)) {
                hasMapParam = true;
                break;
            }
        }

        if (hasMapParam) {
            // Mode Map: créer une Map avec TOUTES les valeurs converties
            Map<String, Object> allParamsMap = new HashMap<>();

            // Convertir toutes les valeurs selon leur type
            for (Map.Entry<String, Object> entry : allValues.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                // Trouver le type attendu pour ce paramètre
                Class<?> expectedType = findExpectedType(key, params);

                if (value instanceof String) {
                    allParamsMap.put(key, convertValue((String) value, expectedType));
                } else {
                    allParamsMap.put(key, value);
                }
            }

            // Ajouter les attributs de requête
            Enumeration<String> attrNames = req.getAttributeNames();
            while (attrNames.hasMoreElements()) {
                String name = attrNames.nextElement();
                allParamsMap.put(name, req.getAttribute(name));
            }

            // Assigner les arguments
            for (int i = 0; i < params.length; i++) {
                Parameter param = params[i];

                if (isMapStringObject(param)) {
                    // C'est le paramètre Map, lui passer tout
                    args[i] = allParamsMap;
                } else {
                    // Paramètre individuel
                    String name = getParamName(param);
                    Object value = allParamsMap.get(name);

                    if (value != null) {
                        args[i] = value;
                    } else {
                        args[i] = getDefaultValue(param.getType());
                    }
                }
            }
        } else {
            // Mode normal: paramètres individuels
            for (int i = 0; i < params.length; i++) {
                Parameter param = params[i];
                String name = getParamName(param);
                Class<?> type = param.getType();

                // Chercher valeur
                Object value = null;

                // 1. Variables d'URL
                if (pathVars != null && pathVars.containsKey(name)) {
                    value = pathVars.get(name);
                }
                // 2. Paramètres de requête
                else if (reqParams.containsKey(name)) {
                    String[] values = reqParams.get(name);
                    if (values != null && values.length > 0) {
                        value = values[0];
                    }
                }

                if (value instanceof String) {
                    args[i] = convertValue((String) value, type);
                } else {
                    args[i] = getDefaultValue(type);
                }
            }
        }

        return args;
    }

    private boolean isMapStringObject(Parameter param) {
        Class<?> type = param.getType();
        if (Map.class.isAssignableFrom(type)) {
            // Vérifier si c'est Map<String, Object> ou Map<?, ?>
            String typeName = param.getParameterizedType().getTypeName();
            return typeName.contains("Map<") &&
                    (typeName.contains("String, Object") ||
                            typeName.contains("String,Object") ||
                            typeName.equals("java.util.Map"));
        }
        return false;
    }

    private Class<?> findExpectedType(String paramName, Parameter[] params) {
        for (Parameter param : params) {
            String name = getParamName(param);
            if (name.equals(paramName)) {
                return param.getType();
            }
        }
        return String.class; // Par défaut
    }

    private String getParamName(Parameter param) {
        if (param.isAnnotationPresent(Request.class)) {
            return param.getAnnotation(Request.class).value();
        }
        return param.getName();
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
        if (type == byte.class)
            return (byte) 0;
        if (type == short.class)
            return (short) 0;
        if (type == char.class)
            return '\0';
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
                resp.setContentType("text/html");
                resp.getWriter().print(str);
            }
        } else if (result instanceof Map) {
            resp.setContentType("application/json");
            resp.getWriter().print(toJson((Map<?, ?>) result));
        } else if (result instanceof Iterable || result instanceof Object[]) {
            resp.setContentType("application/json");
            resp.getWriter().print(toJsonArray(result));
        } else if (result != null) {
            resp.setContentType("text/html");
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

            sb.append("\"").append(escapeJson(entry.getKey().toString())).append("\":");
            sb.append(toJsonValue(entry.getValue()));
        }

        return sb.append("}").toString();
    }

    private String toJsonArray(Object obj) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        if (obj instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) obj) {
                if (!first)
                    sb.append(",");
                first = false;
                sb.append(toJsonValue(item));
            }
        } else if (obj instanceof Object[]) {
            for (Object item : (Object[]) obj) {
                if (!first)
                    sb.append(",");
                first = false;
                sb.append(toJsonValue(item));
            }
        }

        return sb.append("]").toString();
    }

    private String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeJson((String) value) + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Map) {
            return toJson((Map<?, ?>) value);
        } else if (value instanceof Iterable || value instanceof Object[]) {
            return toJsonArray(value);
        } else {
            return "\"" + escapeJson(value.toString()) + "\"";
        }
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}