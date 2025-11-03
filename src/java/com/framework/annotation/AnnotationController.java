package java.com.framework.annotation;

public class AnnotationController 
{
    @Get("/etudiant")
    public void listEtudiants() 
    {
        System.out.println("Liste des étudiants");
    }

    @Post("/etudiant")
    public void addEtudiant() 
    {
        System.out.println("Ajout d’un étudiant");
    }
}
