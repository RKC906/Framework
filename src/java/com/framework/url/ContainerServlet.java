package java.com.framework.url;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet(urlPatterns = "/*")
public class ContainerServlet extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String path = req.getPathInfo();
        if (path == null)
            path = "/";
        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().println("<h1>Spring Boot Servlet → " + path + "</h1>");
    }
}
