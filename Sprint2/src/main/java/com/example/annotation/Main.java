package com.example.annotation;

import java.lang.reflect.Method;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== Démarrage du scanner d’annotations ===");

        // Scan via la classe existante
        RouteScanner.scanRoutes(ExampleController.class);

        System.out.println("\n=== Méthodes annotées trouvées ===");

        // Scan manuel pour afficher toutes les méthodes annotées
        for (Method method : ExampleController.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Get.class) ||
                    method.isAnnotationPresent(Post.class)
                ) {
                System.out.println("- " + method.getName());
            }
        }

        System.out.println("=== Fin du scan ===");
    }
}
