package com.example.url;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

import com.example.classe.RouteInfo;
import com.example.classe.Caste;
import com.example.classe.ModelVue;
import com.example.controller.ScannerController;
import com.example.annotation.Request;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

@WebServlet("/*")
public class UrlServlet extends HttpServlet {

    private RequestDispatcher defaultDispatcher;
    private List<RouteInfo> routes; // Utilisez RouteInfo

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        String basePackage = getServletConfig().getInitParameter("base-package");
        routes = ScannerController.scanRoutes(basePackage);

        System.out.println("=== ROUTES DETECTEES ===");
        for (RouteInfo r : routes) {
            System.out.println(r.getUrlPattern() + " -> " +
                    r.getMethod().getDeclaringClass().getSimpleName() +
                    "." + r.getMethod().getName());
        }
    }

    private RouteInfo findAndPrepareRoute(String path, HttpServletRequest req) {
        for (RouteInfo route : routes) {
            if (route.isDynamic()) {
                // Route dynamique avec variables
                if (matchDynamicRoute(route, path)) {
                    // Ajouter les paramètres de requête
                    Enumeration<String> paramNames = req.getParameterNames();
                    while (paramNames.hasMoreElements()) {
                        String name = paramNames.nextElement();
                        route.addRequestParameter(name, req.getParameterValues(name));
                    }
                    return route;
                }
            } else {
                // Route statique
                if (route.getUrlPattern().equals(path)) {
                    // Ajouter les paramètres de requête
                    Enumeration<String> paramNames = req.getParameterNames();
                    while (paramNames.hasMoreElements()) {
                        String name = paramNames.nextElement();
                        route.addRequestParameter(name, req.getParameterValues(name));
                    }
                    return route;
                }
            }
        }
        return null;
    }

    private boolean matchDynamicRoute(RouteInfo route, String path) {
        String pattern = route.getUrlPattern();
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");

        if (patternParts.length != pathParts.length) {
            return false;
        }

        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            String pathPart = pathParts[i];

            if (patternPart.startsWith("{") && patternPart.endsWith("}")) {
                // Variable dynamique - extraire le nom et la valeur
                String varName = patternPart.substring(1, patternPart.length() - 1);
                route.addPathVariable(varName, pathPart);
            } else if (!patternPart.equals(pathPart)) {
                // Partie statique ne correspond pas
                return false;
            }
        }

        return true;
    }

    private Object[] injectParameters(RouteInfo route, HttpServletRequest req) {
        Method method = route.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] values = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> type = param.getType();
            String paramName = param.getName();

            // Vérifier si le paramètre a l'annotation @Request
            if (param.isAnnotationPresent(Request.class)) {
                String requestName = param.getAnnotation(Request.class).value();
                values[i] = resolveParameterValue(route, req, requestName, type);
            } else {
                // Sans annotation, utiliser le nom du paramètre
                values[i] = resolveParameterValue(route, req, paramName, type);
            }
        }

        return values;
    }

    private Object resolveParameterValue(RouteInfo route, HttpServletRequest req,
            String paramName, Class<?> type) {
        // 1. Chercher dans les variables de chemin
        String pathValue = route.getPathVariable(paramName);
        if (pathValue != null) {
            return new Caste(pathValue, type).getTypedValue();
        }

        // 2. Chercher dans les paramètres de requête
        String[] requestValues = route.getRequestParameter(paramName);
        if (requestValues != null && requestValues.length > 0) {
            return new Caste(requestValues[0], type).getTypedValue();
        }

        // 3. Chercher dans les attributs de requête
        Object attributeValue = req.getAttribute(paramName);
        if (attributeValue != null && type.isInstance(attributeValue)) {
            return type.cast(attributeValue);
        }

        // 4. Pour les types primitifs, retourner une valeur par défaut
        if (type.isPrimitive()) {
            return getDefaultPrimitiveValue(type);
        }

        // 5. Sinon, null
        return null;
    }

    private Object getDefaultPrimitiveValue(Class<?> type) {
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == double.class)
            return 0.0;
        if (type == float.class)
            return 0.0f;
        if (type == boolean.class)
            return false;
        if (type == byte.class)
            return (byte) 0;
        if (type == short.class)
            return (short) 0;
        if (type == char.class)
            return '\0';
        return null;
    }

    private void processResult(Object result, HttpServletRequest req,
            HttpServletResponse res)
            throws ServletException, IOException {

        // 1. ModelVue -> forward vers une JSP
        if (result instanceof ModelVue) {
            ModelVue mv = (ModelVue) result;
            if (mv.getData() != null) {
                mv.getData().forEach(req::setAttribute);
            }
            req.getRequestDispatcher(mv.getView()).forward(req, res);
            return;
        }

        // 2. Map, List ou Array -> JSON
        if (result instanceof Map || result instanceof Iterable || result instanceof Object[]) {
            res.setContentType("application/json");
            res.getWriter().println(toJson(result));
            return;
        }

        // 3. Void -> message de confirmation
        if (result == null &&
                req.getAttribute("methodReturnType") instanceof Class &&
                ((Class<?>) req.getAttribute("methodReturnType")) == void.class) {
            res.setContentType("text/html");
            res.getWriter().println("Action exécutée avec succès.");
            return;
        }

        // 4. String ou autre -> texte HTML
        res.setContentType("text/html");
        if (result != null) {
            res.getWriter().println(result.toString());
        }
    }

    private String toJson(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (var e : map.entrySet()) {
                if (i++ > 0)
                    sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":");
                Object value = e.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else {
                    sb.append(value);
                }
            }
            return sb.append("}").toString();
        }

        if (obj instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (var e : it) {
                if (i++ > 0)
                    sb.append(",");
                if (e instanceof String) {
                    sb.append("\"").append(e).append("\"");
                } else {
                    sb.append(e);
                }
            }
            return sb.append("]").toString();
        }

        if (obj instanceof Object[] arr) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0)
                    sb.append(",");
                if (arr[i] instanceof String) {
                    sb.append("\"").append(arr[i]).append("\"");
                } else {
                    sb.append(arr[i]);
                }
            }
            return sb.append("]").toString();
        }

        return "\"" + obj.toString() + "\"";
    }

    private void sendError(HttpServletResponse res, int code, String msg)
            throws IOException {
        res.setStatus(code);
        res.setContentType("text/html");
        res.getWriter().println(
                "<!DOCTYPE html>" +
                        "<html>" +
                        "<head><title>Erreur " + code + "</title></head>" +
                        "<body>" +
                        "<h1>Erreur " + code + "</h1>" +
                        "<p>" + msg + "</p>" +
                        "</body>" +
                        "</html>");
    }
}