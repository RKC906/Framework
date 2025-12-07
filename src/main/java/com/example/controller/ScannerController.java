package com.example.controller;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

import com.example.annotation.Controller;
import com.example.annotation.Route;
import com.example.classe.RouteInfo;

public class ScannerController {

    /** Retourne une liste structurée de toutes les routes */
    public static List<RouteInfo> scanRoutes(String basePackage) {
        List<RouteInfo> routes = new ArrayList<>();

        for (Class<?> controllerClass : trouverControllers(basePackage)) {
            try {
                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
                String baseUrl = controllerClass.getAnnotation(Controller.class).value();

                for (Method method : controllerClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Route.class)) {
                        String methodUrl = method.getAnnotation(Route.class).value();
                        String fullUrl = baseUrl + methodUrl;

                        RouteInfo route = new RouteInfo(controllerInstance, method, fullUrl);
                        routes.add(route);
                    }
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de l'instanciation du contrôleur " +
                        controllerClass.getName() + ": " + e.getMessage());
            }
        }

        return routes;
    }

    // === Trouver les classes contrôleurs (inchangé) ===
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