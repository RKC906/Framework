package com.example.controller;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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
}
