package com.example.url;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;

import com.example.controller.ScannerController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/*")
public class UrlServlet extends HttpServlet 
{
    private RequestDispatcher defaultDispatcher;
    private Map<String, Method> routeMapping;

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");

        // 🔹 On récupère le package à scanner depuis web.xml
        String basePackage = getServletConfig().getInitParameter("base-package");

        if (basePackage == null || basePackage.isEmpty()) {
            System.err.println("⚠ Aucun package spécifié dans le web.xml pour le paramètre 'base-package'");
            return;
        }

        // 🔹 Scan du package spécifié
        routeMapping = ScannerController.mapperRoutes(basePackage);

        System.out.println("=== Routes détectées ===");
        for (String url : routeMapping.keySet()) {
            System.out.println("➡ " + url + " -> " + routeMapping.get(url).getDeclaringClass().getName());
        }
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
            throws IOException {
        Method method = routeMapping.get(path);

        if (method != null) {
            try {
                Object controllerInstance = method.getDeclaringClass().getDeclaredConstructor().newInstance();
                Object result = method.invoke(controllerInstance);

                res.setContentType("text/html;charset=UTF-8");
                try (PrintWriter out = res.getWriter()) {
                    out.println(result != null ? result.toString() : "<h1>Aucune réponse</h1>");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendError(res, 500, "Erreur interne : " + e.getMessage());
            }
        } else {
            sendError(res, 404, "L’URL demandée n’existe pas : " + path);
        }
    }

    private void sendError(HttpServletResponse res, int statusCode, String message) throws IOException {
        res.setStatus(statusCode);
        res.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            out.println("""
                    <html>
                        <head><title>Erreur %d</title></head>
                        <body>
                            <h1>Erreur %d</h1>
                            <p>%s</p>
                        </body>
                    </html>
                    """.formatted(statusCode, statusCode, message));
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}
