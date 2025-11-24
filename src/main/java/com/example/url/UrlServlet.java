package com.example.url;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

import com.example.controller.ScannerController;
import com.example.classe.ModelVue;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/*")
public class UrlServlet extends HttpServlet {

    private RequestDispatcher defaultDispatcher;

    // mapping exact + dynamiques
    private Map<String, Method> routeMapping;
    private Map<String, Method> dynamicRoutes;

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");

        String basePackage = getServletConfig().getInitParameter("base-package");
        if (basePackage == null) {
            System.err.println("⚠ Aucun package spécifié dans web.xml");
            return;
        }

        // routes exactes
        routeMapping = ScannerController.mapperRoutes(basePackage);

        // routes dynamiques : /user/{id}
        dynamicRoutes = new HashMap<>();

        for (String url : routeMapping.keySet()) {
            if (url.contains("{")) {
                dynamicRoutes.put(url, routeMapping.get(url));
            }
        }

        System.out.println("=== Routes détectées ===");
        routeMapping.forEach((url, m) -> System.out.println("➡ " + url + " -> " + m.getDeclaringClass().getName()));
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        String path = req.getRequestURI().substring(req.getContextPath().length());
        boolean resourceExists = getServletContext().getResource(path) != null;

        if (resourceExists) {
            defaultServe(req, res);
        } else {
            handleDynamicRoute(req, res, path);
        }
    }

    private void handleDynamicRoute(HttpServletRequest req, HttpServletResponse res, String path)
            throws IOException, ServletException {

        Method method = routeMapping.get(path);

        // essayer URL dynamiques
        if (method == null) {
            method = matchDynamicRoute(path, req);
        }

        if (method == null) {
            sendError(res, 404, "URL inconnue : " + path);
            return;
        }

        try {
            Object controllerInstance = method.getDeclaringClass().getDeclaredConstructor().newInstance();

            // injection des paramètres
            Object[] params = injectParams(req, method);

            Object result = method.invoke(controllerInstance, params);

            // === gestion ModelView ===
            if (result instanceof ModelVue mv) {
                if (mv.getData() != null) {
                    mv.getData().forEach(req::setAttribute);
                }
                req.getRequestDispatcher(mv.getView()).forward(req, res);
                return;
            }

            // === gestion JSON ===
            if (result instanceof Map || result instanceof Iterable || result instanceof Object[]) {
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().println(toJson(result));
                return;
            }

            // === gestion void ===
            if (method.getReturnType() == void.class) {
                res.getWriter().println("<h3>Action effectuée</h3>");
                return;
            }

            // === gestion String (HTML) ===
            res.setContentType("text/html;charset=UTF-8");
            res.getWriter().println(result != null ? result.toString() : "Aucune réponse");

        } catch (Exception e) {
            e.printStackTrace();
            sendError(res, 500, e.getMessage());
        }
    }

   
    //  Injection automatique des paramètres
    private Object[] injectParams(HttpServletRequest req, Method method) {

        Parameter[] parameters = method.getParameters();
        Object[] values = new Object[parameters.length];

        Map<String, String> pathParams = (Map<String, String>) req.getAttribute("pathParams");

        for (int i = 0; i < parameters.length; i++) {
            String name = parameters[i].getName();

            // paramètres dynamiques (ex: /user/{id})
            if (pathParams != null && pathParams.containsKey(name)) {
                values[i] = convertType(pathParams.get(name), parameters[i].getType());
                continue;
            }

            // query string et forms
            String value = req.getParameter(name);
            values[i] = convertType(value, parameters[i].getType());
        }

        return values;
    }

    // conversion String → type primitif
    private Object convertType(String value, Class<?> type) {
        if (value == null)
            return null;
        if (type == int.class || type == Integer.class)
            return Integer.parseInt(value);
        if (type == double.class || type == Double.class)
            return Double.parseDouble(value);
        if (type == long.class || type == Long.class)
            return Long.parseLong(value);
        return value; // String
    }

    // Matching des URLs dynamiques : /user/{id}
    private Method matchDynamicRoute(String path, HttpServletRequest req) {

        for (String pattern : dynamicRoutes.keySet()) {

            String regex = pattern.replaceAll("\\{[^/]+}", "([^/]+)");
            if (path.matches(regex)) {

                Map<String, String> params = new HashMap<>();

                String[] patternParts = pattern.split("/");
                String[] urlParts = path.split("/");

                for (int i = 0; i < patternParts.length; i++) {
                    if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                        String paramName = patternParts[i].substring(1, patternParts[i].length() - 1);
                        params.put(paramName, urlParts[i]);
                    }
                }

                req.setAttribute("pathParams", params);
                return dynamicRoutes.get(pattern);
            }
        }
        return null;
    }

    // mini conversion Java → JSON
    private String toJson(Object obj) {
        return "{\"json\":\"" + obj.toString().replace("\"", "\\\"") + "\"}";
    }

    private void sendError(HttpServletResponse res, int code, String message) throws IOException {
        res.setStatus(code);
        res.setContentType("text/html;charset=UTF-8");
        res.getWriter().println("<h1>Erreur " + code + "</h1><p>" + message + "</p>");
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}
