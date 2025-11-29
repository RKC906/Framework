package com.example.url;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

import com.example.classe.Caste;
import com.example.classe.ModelVue;
import com.example.controller.ScannerController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/*")
public class UrlServlet extends HttpServlet {

    private RequestDispatcher defaultDispatcher;

    // routes exactes
    private Map<String, Method> routeMapping;

    // routes dynamiques /user/{id}
    private Map<String, Method> dynamicRoutes;

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");

        // récupérer package depuis web.xml
        String basePackage = getServletConfig().getInitParameter("base-package");

        try {
            routeMapping = ScannerController.mapperRoutes(basePackage);
        } catch (Exception e) {
            e.printStackTrace();
        }

        dynamicRoutes = new HashMap<>();

        for (String url : routeMapping.keySet()) {
            if (url.contains("{")) {
                dynamicRoutes.put(url, routeMapping.get(url));
            }
        }

        System.out.println("=== ROUTES DETECTEES ===");
        routeMapping.forEach((u, m) ->
            System.out.println("➡ " + u + "  ->  " + m.getDeclaringClass().getName())
        );
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        String path = req.getRequestURI().substring(req.getContextPath().length());
        boolean resourceExists = getServletContext().getResource(path) != null;

        // si statique => images, css, html
        if (resourceExists) {
            defaultDispatcher.forward(req, res);
            return;
        }

        // sinon => controller
        handleRoute(req, res, path);
    }

    private void handleRoute(HttpServletRequest req, HttpServletResponse res, String path)
            throws IOException, ServletException {

        Method method = routeMapping.get(path);

        // si URL exacte non trouvée → test des URL dynamiques
        if (method == null) {
            method = matchDynamicRoute(path, req);
        }

        if (method == null) {
            sendError(res, 404, "URL inconnue : " + path);
            return;
        }

        try {
            Object controller = method.getDeclaringClass().getDeclaredConstructor().newInstance();

            Object[] params = injectParams(req, method);

            Object result = method.invoke(controller, params);

            // ---- ModelVue ----
            if (result instanceof ModelVue mv) {
                if (mv.getData() != null) {
                    mv.getData().forEach(req::setAttribute);
                }
                req.getRequestDispatcher(mv.getView()).forward(req, res);
                return;
            }

            // ---- JSON ----
            if (result instanceof Map || result instanceof Iterable || result instanceof Object[]) {
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().println(toJson(result));
                return;
            }

            // ---- VOID ----
            if (method.getReturnType() == void.class) {
                res.getWriter().println("<h3>Action effectuée</h3>");
                return;
            }

            // ---- String → HTML ----
            res.setContentType("text/html;charset=UTF-8");
            res.getWriter().println(result != null ? result.toString() : "Aucune réponse");

        } catch (Exception e) {
            e.printStackTrace();
            sendError(res, 500, e.getMessage());
        }
    }

    // MATCHING URL DYNAMIQUE : /user/{id}
    private Method matchDynamicRoute(String path, HttpServletRequest req) {

        for (String pattern : dynamicRoutes.keySet()) {

            // ajouter ^ et $ pour un match exact
            String regex = "^" + pattern.replaceAll("\\{[^/]+}", "([^/]+)") + "$";

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

    // INJECTION AUTOMATIQUE DES PARAMETRES
    private Object[] injectParams(HttpServletRequest req, Method method) {

        Parameter[] parameters = method.getParameters();
        Object[] values = new Object[parameters.length];

        Map<String, String> pathParams = (Map<String, String>) req.getAttribute("pathParams");

        for (int i = 0; i < parameters.length; i++) {

            String name = parameters[i].getName();
            Class<?> type = parameters[i].getType();

            // ---- paramètres dynamiques {id}
            if (pathParams != null && pathParams.containsKey(name)) {
                values[i] = new Caste(pathParams.get(name), type).getTypedValue();
                continue;
            }

            // ---- paramètres du formulaire (automatique)
            Map<String, String[]> form = req.getParameterMap();

            if (form.containsKey(name)) {
                values[i] = new Caste(form.get(name)[0], type).getTypedValue();
                continue;
            }

            // ---- valeur par défaut (null ou primitive)
            values[i] = null;
        }

        return values;
    }

    
    // MINI JSON UTIL
    
    private String toJson(Object obj) {

        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (var e : map.entrySet()) {
                if (i++ > 0)
                    sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":\"")
                        .append(e.getValue()).append("\"");
            }
            return sb.append("}").toString();
        }

        if (obj instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (var e : it) {
                if (i++ > 0)
                    sb.append(",");
                sb.append("\"").append(e).append("\"");
            }
            return sb.append("]").toString();
        }

        return "\"" + obj.toString() + "\"";
    }

    private void sendError(HttpServletResponse res, int code, String message)
            throws IOException {
        res.setStatus(code);
        res.setContentType("text/html;charset=UTF-8");
        res.getWriter().println("<h1>Erreur " + code + "</h1><p>" + message + "</p>");
    }
}
