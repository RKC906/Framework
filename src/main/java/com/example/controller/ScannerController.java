package com.example.controller;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

import com.example.annotation.Controller;
import com.example.annotation.Route;

public class ScannerController {

    public static class RouteInfo {
        public String url;
        public boolean dynamic;
        public Method method;
        public Class<?> controllerClass;
        public List<String> pathVariables;

        public RouteInfo(String url, boolean dynamic, Method m, Class<?> c, List<String> vars) {
            this.url = url;
            this.dynamic = dynamic;
            this.method = m;
            this.controllerClass = c;
            this.pathVariables = vars;
        }
    }

    /** Retourne une liste structurée de toutes les routes */
    public static List<RouteInfo> scanRoutes(String basePackage) {
        List<RouteInfo> list = new ArrayList<>();

        for (Class<?> controller : trouverControllers(basePackage)) {

            String baseUrl = controller.getAnnotation(Controller.class).value();

            for (Method m : controller.getDeclaredMethods()) {
                if (m.isAnnotationPresent(Route.class)) {

                    String u = baseUrl + m.getAnnotation(Route.class).value();

                    boolean dynamic = u.contains("{");

                    List<String> vars = new ArrayList<>();
                    if (dynamic) {
                        for (String part : u.split("/")) {
                            if (part.startsWith("{") && part.endsWith("}")) {
                                vars.add(part.substring(1, part.length() - 1));
                            }
                        }
                    }

                    list.add(new RouteInfo(u, dynamic, m, controller, vars));
                }
            }
        }
        return list;
    }

    // === Trouver les classes contrôleurs ===
    public static List<Class<?>> trouverControllers(String basePackage) {

        List<Class<?>> classesControllers = new ArrayList<>();

        try {
            String chemin = basePackage.replace('.', '/');
            URL resource = Thread.currentThread().getContextClassLoader().getResource(chemin);

            if (resource != null) {
                File repertoire = new File(resource.getFile());
                scannerRepertoire(repertoire, basePackage, classesControllers);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return classesControllers;
    }

    private static void scannerRepertoire(File rep, String pkg, List<Class<?>> list) {
        if (!rep.exists() || !rep.isDirectory())
            return;

        for (File f : rep.listFiles()) {

            if (f.isDirectory()) {
                scannerRepertoire(f, pkg + "." + f.getName(), list);
            } else if (f.getName().endsWith(".class")) {

                String nom = f.getName().replace(".class", "");
                try {
                    Class<?> c = Class.forName(pkg + "." + nom);

                    if (c.isAnnotationPresent(Controller.class)) {
                        list.add(c);
                    }

                } catch (Exception ignored) {
                }
            }
        }
    }
}
