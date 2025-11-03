package com.example.controller;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.example.annotation.Controller;

public class ScannerController {

    public static List<String> trouverControllers(String basePackage) {
        List<String> nomsControllers = new ArrayList<>();

        try 
        {
            String chemin = basePackage.replace('.', '/');
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL resource = classLoader.getResource(chemin);

            if (resource != null) {
                File repertoire = new File(resource.getFile());
                scannerRepertoire(repertoire, basePackage, nomsControllers);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return nomsControllers;
    }

    private static void scannerRepertoire(File repertoire, String packageName, List<String> controllers) {
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

    private static void verifierAnnotationController(File fichier, String packageName, List<String> controllers) {
        try {
            String nomClasse = fichier.getName().substring(0, fichier.getName().length() - 6);
            String nomCompletClasse = packageName + "." + nomClasse;

            Class<?> classe = Class.forName(nomCompletClasse);

            if (classe.isAnnotationPresent(Controller.class)) {
                controllers.add(nomCompletClasse);
            }

        } catch (Exception e) {
            System.err.println("Erreur pour " + fichier.getName() + " : " + e.getMessage());
        }
    }
}
