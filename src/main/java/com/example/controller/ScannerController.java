package com.example.controller;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

import com.example.annotation.*;

public class ScannerController {

    public static class RouteData {
        public Object controller;
        public Method method;
        public String url;
        public String httpMethod;

        public RouteData(Object controller, Method method, String url, String httpMethod) {
            this.controller = controller;
            this.method = method;
            this.url = url;
            this.httpMethod = httpMethod;
        }
    }

    public static Map<String, List<RouteData>> scan(String basePackage) throws Exception {
        Map<String, List<RouteData>> routes = new HashMap<>();

        for (Class<?> clazz : findControllers(basePackage)) {
            Object controller = clazz.getDeclaredConstructor().newInstance();
            String baseUrl = clazz.getAnnotation(Controller.class).value();

            for (Method method : clazz.getDeclaredMethods()) {
                RouteData route = createRoute(controller, method, baseUrl);
                if (route != null) {
                    String url = route.url;
                    routes.computeIfAbsent(url, k -> new ArrayList<>()).add(route);
                }
            }
        }

        return routes;
    }

    private static RouteData createRoute(Object controller, Method method, String baseUrl) {
        String httpMethod = null;
        String path = null;

        if (method.isAnnotationPresent(Get.class)) {
            httpMethod = "GET";
            path = method.getAnnotation(Get.class).value();
        } else if (method.isAnnotationPresent(Post.class)) {
            httpMethod = "POST";
            path = method.getAnnotation(Post.class).value();
        } else if (method.isAnnotationPresent(Route.class)) {
            httpMethod = "GET"; // Par défaut
            path = method.getAnnotation(Route.class).value();
        }

        if (path != null) {
            return new RouteData(controller, method, baseUrl + path, httpMethod);
        }

        return null;
    }

    private static List<Class<?>> findControllers(String basePackage) throws Exception {
        List<Class<?>> controllers = new ArrayList<>();
        String path = basePackage.replace('.', '/');
        URL url = Thread.currentThread().getContextClassLoader().getResource(path);

        if (url != null) {
            findClasses(new File(url.getFile()), basePackage, controllers);
        }

        return controllers;
    }

    private static void findClasses(File dir, String pkg, List<Class<?>> list) throws Exception {
        if (!dir.exists())
            return;

        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                findClasses(file, pkg + "." + file.getName(), list);
            } else if (file.getName().endsWith(".class")) {
                String className = pkg + "." + file.getName().replace(".class", "");
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Controller.class)) {
                    list.add(clazz);
                }
            }
        }
    }
}