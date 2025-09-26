package java.md.url;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/*")
public class ContainerServlet extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException 
    {
        String path = req.getPathInfo();
        if (path == null)
            path = "/";
        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().println("<h1> Url/ " + path + "</h1>");
    }
}
