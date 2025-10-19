package com.example.annotation;

public class ExampleController {

    @Get("/etudiant")
    public void listEtudiants() {
        System.out.println("Liste des étudiants");
    }

    @Post("/etudiant")
    public void addEtudiant() {
        System.out.println("Ajout d’un étudiant");
    }
}