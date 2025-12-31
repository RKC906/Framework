package com.example.controller;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;

import com.example.annotation.*;

public class ScannerController {

    public static class RouteData {
        public Object controller;
        public Method method;
        public String url;
        public String httpMethod;
        public Map<String, Class<?>> paramTypes = new HashMap<>();
        public boolean hasMapParam = false;
        public boolean hasComplexObject = false;
        public boolean returnsJson = false;

        public RouteData(Object controller, Method method, String url, String httpMethod) {
            this.controller = controller;
            this.method = method;
            this.url = url;
            this.httpMethod = httpMethod;
            analyzeParameters(method);
            analyzeReturnType(method);
        }

        private void analyzeParameters(Method method) {
            Parameter[] params = method.getParameters();
            for (Parameter param : params) {
                String name = param.isAnnotationPresent(Request.class) ? param.getAnnotation(Request.class).value()
                        : param.getName();
                Class<?> type = param.getType();
                paramTypes.put(name, type);

                // Vérifier Map<String, Object>
                if (type.equals(Map.class)) {
                    String typeName = param.getParameterizedType().getTypeName();
                    if (typeName.contains("String, Object") || typeName.equals("java.util.Map")) {
                        hasMapParam = true;
                    }
                }
                // Vérifier objets complexes
                else if (isComplexObject(type)) {
                    hasComplexObject = true;
                }
            }
        }

        private void analyzeReturnType(Method method) {
            // Vérifier si la méthode a l'annotation @Json
            this.returnsJson = method.isAnnotationPresent(Json.class);
        }

        private boolean isComplexObject(Class<?> type) {
            return !type.isPrimitive() &&
                    !type.equals(String.class) &&
                    !type.equals(Integer.class) &&
                    !type.equals(Double.class) &&
                    !type.equals(Boolean.class) &&
                    !type.equals(Long.class) &&
                    !type.equals(Float.class) &&
                    !Map.class.isAssignableFrom(type) &&
                    !List.class.isAssignableFrom(type);
        }
    }

    public static Map<String, List<RouteData>> scan(String basePackage) throws Exception {
        Map<String, List<RouteData>> routes = new HashMap<>();

        for (Class<?> clazz : trouverControllers(basePackage)) {
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
            httpMethod = "GET";
            path = method.getAnnotation(Route.class).value();
        }

        if (path != null) {
            return new RouteData(controller, method, baseUrl + path, httpMethod);
        }

        return null;
    }

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