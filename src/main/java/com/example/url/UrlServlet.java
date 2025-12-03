package com.example.url;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

import com.example.annotation.Request;
import com.example.classe.Caste;
import com.example.classe.ModelVue;
import com.example.controller.ScannerController;
import com.example.controller.ScannerController.RouteInfo;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

@WebServlet("/*")
public class UrlServlet extends HttpServlet {

    private RequestDispatcher defaultDispatcher;

    private List<RouteInfo> routes;

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");

        String basePackage = getServletConfig().getInitParameter("base-package");

        routes = ScannerController.scanRoutes(basePackage);

        System.out.println("=== ROUTES DETECTEES ===");
        for (RouteInfo r : routes) {
            System.out.println(r.url + "  -->  " + r.method.getDeclaringClass().getName()
                    + "." + r.method.getName());
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        String path = req.getRequestURI().substring(req.getContextPath().length());

        if (getServletContext().getResource(path) != null) {
            defaultDispatcher.forward(req, res);
            return;
        }

        handleRoute(req, res, path);
    }

    private void handleRoute(HttpServletRequest req, HttpServletResponse res, String path)
            throws IOException, ServletException {

        RouteInfo info = findRoute(path, req);

        if (info == null) {
            sendError(res, 404, "Route inconnue : " + path);
            return;
        }

        try {
            Object controller = info.controllerClass.getDeclaredConstructor().newInstance();

            Object[] args = injectParams(req, info.method);

            Object result = info.method.invoke(controller, args);

            // === ModelVue ===
            if (result instanceof ModelVue mv) {
                if (mv.getData() != null)
                    mv.getData().forEach(req::setAttribute);

                req.getRequestDispatcher(mv.getView()).forward(req, res);
                return;
            }

            // === JSON ===
            if (result instanceof Map || result instanceof Iterable || result instanceof Object[]) {
                res.setContentType("application/json");
                res.getWriter().println(toJson(result));
                return;
            }

            // === Void ===
            if (info.method.getReturnType() == void.class) {
                res.getWriter().println("Action exécutée.");
                return;
            }

            // === String ===
            res.setContentType("text/html");
            res.getWriter().println(result);

        } catch (Exception e) {
            e.printStackTrace();
            sendError(res, 500, e.getMessage());
        }
    }

    private RouteInfo findRoute(String path, HttpServletRequest req) {

        // 1) Chercher route exacte
        for (RouteInfo r : routes) {
            if (!r.dynamic && r.url.equals(path)) {
                return r;
            }
        }

        // 2) Chercher route dynamique
        for (RouteInfo r : routes) {
            if (!r.dynamic)
                continue;

            String regex = "^" + r.url.replaceAll("\\{[^/]+}", "([^/]+)") + "$";

            if (path.matches(regex)) {

                Map<String, String> params = new HashMap<>();

                String[] urlParts = r.url.split("/");
                String[] pathParts = path.split("/");

                for (int i = 0; i < urlParts.length; i++) {
                    if (urlParts[i].startsWith("{")) {
                        String name = urlParts[i].substring(1, urlParts[i].length() - 1);
                        params.put(name, pathParts[i]);
                    }
                }

                req.setAttribute("pathParams", params);
                return r;
            }
        }

        return null;
    }

   // injection automatique compatible Caste avec support @Request
private Object[] injectParams(HttpServletRequest req, Method method) {
    
    Parameter[] params = method.getParameters();
    Object[] values = new Object[params.length];
    
    Map<String, String> pathVars = (Map<String, String>) req.getAttribute("pathParams");
    Map<String, String[]> form = req.getParameterMap();
    
    for (int i = 0; i < params.length; i++) {
        
        Parameter param = params[i];
        String paramName = param.getName();
        Class<?> type = param.getType();
        
        // Vérifier si le paramètre a l'annotation @Request
        if (param.isAnnotationPresent(Request.class)) {
            String requestParamName = param.getAnnotation(Request.class).value();
            
            // Chercher dans les paramètres de requête avec le nom spécifié
            if (form.containsKey(requestParamName)) {
                values[i] = new Caste(form.get(requestParamName)[0], type).getTypedValue();
                continue;
            }
            
            // Chercher dans les variables de chemin avec le nom spécifié
            if (pathVars != null && pathVars.containsKey(requestParamName)) {
                values[i] = new Caste(pathVars.get(requestParamName), type).getTypedValue();
                continue;
            }
            
            values[i] = null;
            continue;
        }
        
        // Code original (sans annotation) reste inchangé
        // Paramètre dynamique {id}
        if (pathVars != null && pathVars.containsKey(paramName)) {
            values[i] = new Caste(pathVars.get(paramName), type).getTypedValue();
            continue;
        }
        
        // Paramètres de formulaire
        if (form.containsKey(paramName)) {
            values[i] = new Caste(form.get(paramName)[0], type).getTypedValue();
            continue;
        }
        
        values[i] = null;
    }
    
    return values;
}

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

    private void sendError(HttpServletResponse res, int code, String msg)
            throws IOException {

        res.setStatus(code);
        res.setContentType("text/html");
        res.getWriter().println("<h1>Erreur " + code + "</h1><p>" + msg + "</p>");
    }
}
