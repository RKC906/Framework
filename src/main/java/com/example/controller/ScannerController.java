package com.example.controller;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.annotation.Controller;
import com.example.annotation.Route;

public class ScannerController {

    /**
     * Retourne toutes les classes annotées @Controller dans le package de base
     */
    public static List<Class<?>> trouverControllers(String basePackage) {
        List<Class<?>> classesControllers = new ArrayList<>();

        try {
            String chemin = basePackage.replace('.', '/');
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL resource = classLoader.getResource(chemin);

            if (resource != null) {
                File repertoire = new File(resource.getFile());
                scannerRepertoire(repertoire, basePackage, classesControllers);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return classesControllers;
    }

    private static void scannerRepertoire(File repertoire, String packageName, List<Class<?>> controllers) {
        if (!repertoire.exists() || !repertoire.isDirectory())
            return;

        File[] fichiers = repertoire.listFiles();
        if (fichiers == null)
            return;

        for (File fichier : fichiers) {
            if (fichier.isDirectory()) {
                String nouveauPackage = packageName + "." + fichier.getName();
                scannerRepertoire(fichier, nouveauPackage, controllers);
            } else if (fichier.getName().endsWith(".class")) {
                verifierAnnotationController(fichier, packageName, controllers);
            }
        }
    }

    private static void verifierAnnotationController(File fichier, String packageName, List<Class<?>> controllers) {
        try {
            String nomClasse = fichier.getName().substring(0, fichier.getName().length() - 6);
            String nomCompletClasse = packageName + "." + nomClasse;

            Class<?> classe = Class.forName(nomCompletClasse);

            if (classe.isAnnotationPresent(Controller.class)) {
                controllers.add(classe);
            }

        } catch (Exception e) {
            System.err.println("Erreur pour " + fichier.getName() + " : " + e.getMessage());
        }
    }

    /**
     * Retourne toutes les méthodes annotées @Route pour une classe donnée
     */
    public static List<Method> trouverMethodesRoute(Class<?> controllerClass) {
        List<Method> routes = new ArrayList<>();
        for (Method methode : controllerClass.getDeclaredMethods()) {
            if (methode.isAnnotationPresent(Route.class)) {
                routes.add(methode);
            }
        }
        return routes;
    }

    /**
     * Retourne une map associant chaque URL complète (ex: "/etudiant/list") à sa
     * méthode
     */
    public static Map<String, Method> mapperRoutes(String basePackage) {
        Map<String, Method> mapping = new HashMap<>();

        List<Class<?>> controllers = trouverControllers(basePackage);
        for (Class<?> controller : controllers) {
            Controller ctrlAnnotation = controller.getAnnotation(Controller.class);
            String baseUrl = ctrlAnnotation.value();

            for (Method methode : controller.getDeclaredMethods()) {
                if (methode.isAnnotationPresent(Route.class)) {
                    Route routeAnnotation = methode.getAnnotation(Route.class);
                    String fullUrl = baseUrl + routeAnnotation.value();
                    mapping.put(fullUrl, methode);
                }
            }
        }
        return mapping;
    }

    // exécuter méthode annotée @Route
    public static String executerRoute(Class<?> controllerClass, String routePath) {
        try {
            Object instance = controllerClass.getDeclaredConstructor().newInstance();

            for (Method method : controllerClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Route.class)) {

                    Route r = method.getAnnotation(Route.class);

                    if (r.value().equals(routePath)) {
                        Object retour = method.invoke(instance);

                        // Vérifier que c'est une chaîne
                        if (retour instanceof String) {
                            return (String) retour;
                        } else {
                            return "Erreur : la méthode " + method.getName() +
                                    " ne retourne pas une chaîne.";
                        }
                    }
                }
            }

            return "Aucune méthode trouvée pour la route : " + routePath;

        } catch (Exception e) {
            e.printStackTrace();
            return "Erreur lors de l'exécution : " + e.getMessage();
        }
    }
}
