package com.example.url;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import com.example.classe.*;
import com.example.controller.ScannerController;
import com.example.annotation.Request;
import com.example.annotation.Json;

@WebServlet("/*")
public class UrlServlet extends HttpServlet {
    private Map<String, List<ScannerController.RouteData>> routes;
    private Map<String, Map<String, String>> pathVarsCache = new HashMap<>();

    @Override
    public void init() throws ServletException {
        try {
            String pkg = getServletConfig().getInitParameter("base-package");
            routes = ScannerController.scan(pkg);
            logRoutes();
        } catch (Exception e) {
            throw new ServletException("Erreur scan routes", e);
        }
    }

    private void logRoutes() {
        System.out.println("=== ROUTES DETECTEES ===");
        routes.forEach((url, list) -> {
            System.out.println("URL: " + url);
            list.forEach(route -> {
                System.out.print("  " + route.httpMethod + " -> " +
                        route.method.getDeclaringClass().getSimpleName() + "." +
                        route.method.getName());
                if (route.returnsJson) {
                    System.out.print(" [@Json]");
                }
                if (route.hasUploadParam) {
                    System.out.print(" [Upload]");
                }
                System.out.println();
            });
        });
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String path = req.getRequestURI().substring(req.getContextPath().length());
        String method = req.getMethod();

        if (getServletContext().getResource(path) != null) {
            getServletContext().getNamedDispatcher("default").forward(req, resp);
            return;
        }

        ScannerController.RouteData route = findRoute(path, method, req);

        if (route == null) {
            if (hasRouteForUrl(path)) {
                sendError(resp, 405, "Méthode " + method + " non autorisée pour " + path);
                return;
            }
            sendError(resp, 404, "Route inconnue : " + path);
            return;
        }

        try {
            Object result = executeRoute(route, req, path);

            if (route.returnsJson) {
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");

                if (result instanceof ModelVue || result instanceof String) {
                    Map<String, Object> jsonResult = new HashMap<>();
                    if (result instanceof ModelVue) {
                        ModelVue mv = (ModelVue) result;
                        jsonResult.put("view", mv.getView());
                        if (mv.getData() != null) {
                            jsonResult.put("data", mv.getData());
                        }
                    } else {
                        jsonResult.put("result", result);
                    }
                    resp.getWriter().print(toJson(jsonResult));
                } else {
                    resp.getWriter().print(convertToJson(result));
                }
            } else {
                handleResult(result, req, resp);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendError(resp, 500, "Erreur interne: " + e.getMessage());
        } finally {
            cleanupUploadedFiles(req);
        }
    }

    // Nettoyage des fichiers uploadés
    private void cleanupUploadedFiles(HttpServletRequest req) {
        @SuppressWarnings("unchecked")
        List<UploadedFile> filesToCleanup = (List<UploadedFile>) req.getAttribute("filesToCleanup");

        if (filesToCleanup != null) {
            for (UploadedFile file : filesToCleanup) {
                file.cleanup();
            }
        }
    }

    // Stocker un fichier pour nettoyage
    private void storeForCleanup(HttpServletRequest req, UploadedFile uploadedFile) {
        @SuppressWarnings("unchecked")
        List<UploadedFile> filesToCleanup = (List<UploadedFile>) req.getAttribute("filesToCleanup");
        if (filesToCleanup == null) {
            filesToCleanup = new ArrayList<>();
            req.setAttribute("filesToCleanup", filesToCleanup);
        }
        filesToCleanup.add(uploadedFile);
    }

    private String convertToJson(Object obj) {
        if (obj instanceof Map) {
            return toJson((Map<?, ?>) obj);
        } else if (obj instanceof Iterable) {
            return toJsonArray(obj);
        } else if (obj instanceof Object[]) {
            return toJsonArray(obj);
        } else {
            return toJsonValue(obj);
        }
    }

    private boolean hasRouteForUrl(String path) {
        if (routes.containsKey(path)) {
            return true;
        }

        for (String pattern : routes.keySet()) {
            if (pattern.contains("{") && matchPattern(pattern, path)) {
                return true;
            }
        }

        return false;
    }

    private String getAllowedMethods(String path) {
        List<String> methods = new ArrayList<>();

        if (routes.containsKey(path)) {
            for (ScannerController.RouteData route : routes.get(path)) {
                methods.add(route.httpMethod);
            }
        } else {
            for (String pattern : routes.keySet()) {
                if (pattern.contains("{") && matchPattern(pattern, path)) {
                    for (ScannerController.RouteData route : routes.get(pattern)) {
                        methods.add(route.httpMethod);
                    }
                    break;
                }
            }
        }

        return String.join(", ", methods);
    }

    private ScannerController.RouteData findRoute(String path, String method, HttpServletRequest req) {
        if (routes.containsKey(path)) {
            ScannerController.RouteData route = routes.get(path).stream()
                    .filter(r -> r.httpMethod.equalsIgnoreCase(method))
                    .findFirst()
                    .orElse(null);
            if (route != null) {
                prepareRoute(route, req);
                return route;
            }
        }

        for (String pattern : routes.keySet()) {
            if (pattern.contains("{") && matchPattern(pattern, path)) {
                for (ScannerController.RouteData route : routes.get(pattern)) {
                    if (route.httpMethod.equalsIgnoreCase(method)) {
                        extractAndCachePathVars(pattern, path);
                        prepareRoute(route, req);
                        return route;
                    }
                }
            }
        }

        return null;
    }

    private void prepareRoute(ScannerController.RouteData route, HttpServletRequest req) {
        // Stocker les paramètres normaux
        Map<String, String[]> params = new HashMap<>();
        Enumeration<String> paramNames = req.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            params.put(name, req.getParameterValues(name));
        }
        req.setAttribute("requestParams", params);
    }

    private boolean matchPattern(String pattern, String path) {
        String[] pParts = pattern.split("/");
        String[] rParts = path.split("/");

        if (pParts.length != rParts.length)
            return false;

        for (int i = 0; i < pParts.length; i++) {
            if (!pParts[i].startsWith("{") && !pParts[i].equals(rParts[i])) {
                return false;
            }
        }

        return true;
    }

    private void extractAndCachePathVars(String pattern, String path) {
        String[] pParts = pattern.split("/");
        String[] rParts = path.split("/");
        Map<String, String> vars = new HashMap<>();

        for (int i = 0; i < pParts.length; i++) {
            if (pParts[i].startsWith("{")) {
                String name = pParts[i].substring(1, pParts[i].length() - 1);
                vars.put(name, rParts[i]);
            }
        }

        pathVarsCache.put(path, vars);
    }

    private Object executeRoute(ScannerController.RouteData route,
            HttpServletRequest req,
            String path) throws Exception {

        Object[] args = prepareArgs(route, req, path);
        return route.method.invoke(route.controller, args);
    }

    private Object[] prepareArgs(ScannerController.RouteData route,
            HttpServletRequest req,
            String path) throws IOException, ServletException {
        Parameter[] params = route.method.getParameters();
        Object[] args = new Object[params.length];

        Map<String, String> pathVars = pathVarsCache.get(path);
        @SuppressWarnings("unchecked")
        Map<String, String[]> reqParams = (Map<String, String[]>) req.getAttribute("requestParams");
        if (reqParams == null) {
            reqParams = new HashMap<>();
        }

        // Si la route a des paramètres upload, on doit parser multipart
        if (route.hasUploadParam && isMultipartRequest(req)) {
            parseMultipartRequest(req);
        }

        // Récupérer les fichiers uploadés
        @SuppressWarnings("unchecked")
        Map<String, UploadedFile> uploadedFiles = (Map<String, UploadedFile>) req.getAttribute("uploadedFiles");
        if (uploadedFiles == null) {
            uploadedFiles = new HashMap<>();
        }

        Map<String, Object> allValues = collectAllValues(route, pathVars, reqParams, uploadedFiles, req);

        if (route.hasMapParam) {
            for (int i = 0; i < params.length; i++) {
                Parameter param = params[i];

                if (param.getType().equals(Map.class)) {
                    args[i] = allValues;
                } else {
                    String name = getParamName(param);
                    args[i] = getParameterValue(name, param.getType(), allValues, req);
                }
            }
        } else {
            for (int i = 0; i < params.length; i++) {
                Parameter param = params[i];
                String name = getParamName(param);
                Class<?> type = param.getType();

                args[i] = getParameterValue(name, type, allValues, req, uploadedFiles);
            }
        }

        return args;
    }

    // Vérifier si c'est une requête multipart
    private boolean isMultipartRequest(HttpServletRequest req) {
        String contentType = req.getContentType();
        return contentType != null &&
                contentType.toLowerCase().startsWith("multipart/form-data");
    }

    // Parser une requête multipart - CORRIGÉ les exceptions
    private void parseMultipartRequest(HttpServletRequest req) {
        Map<String, String[]> textParams = new HashMap<>();
        Map<String, UploadedFile> uploadedFiles = new HashMap<>();

        try {
            // Utiliser try-with-resources pour lire l'input stream
            Collection<Part> parts = req.getParts();

            for (Part part : parts) {
                String name = part.getName();

                if (part.getSubmittedFileName() != null && part.getSize() > 0) {
                    // C'est un fichier
                    UploadedFile uploadedFile = new UploadedFile(
                            part.getSubmittedFileName(),
                            part.getContentType(),
                            part.getSize(),
                            part.getInputStream());
                    uploadedFiles.put(name, uploadedFile);
                    storeForCleanup(req, uploadedFile);
                } else {
                    // C'est un paramètre texte - CORRIGÉ sans Collectors
                    StringBuilder valueBuilder = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(part.getInputStream()))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (valueBuilder.length() > 0) {
                                valueBuilder.append("\n");
                            }
                            valueBuilder.append(line);
                        }
                    }
                    String value = valueBuilder.toString();

                    // Gérer les paramètres multiples avec même nom
                    if (textParams.containsKey(name)) {
                        String[] existing = textParams.get(name);
                        String[] newArray = new String[existing.length + 1];
                        System.arraycopy(existing, 0, newArray, 0, existing.length);
                        newArray[existing.length] = value;
                        textParams.put(name, newArray);
                    } else {
                        textParams.put(name, new String[] { value });
                    }
                }
            }

            // Fusionner avec les paramètres existants
            @SuppressWarnings("unchecked")
            Map<String, String[]> existingParams = (Map<String, String[]>) req.getAttribute("requestParams");
            if (existingParams != null) {
                // Fusion manuelle sans Stream API
                for (Map.Entry<String, String[]> entry : existingParams.entrySet()) {
                    textParams.put(entry.getKey(), entry.getValue());
                }
            }

            req.setAttribute("requestParams", textParams);
            req.setAttribute("uploadedFiles", uploadedFiles);

        } catch (Exception e) {
            // Si ce n'est pas multipart ou erreur, on ignore
            System.err.println("Erreur lors du parsing multipart: " + e.getMessage());
        }
    }

    private Object getParameterValue(String name, Class<?> type,
            Map<String, Object> allValues,
            HttpServletRequest req,
            Map<String, UploadedFile> uploadedFiles) {

        // 1. Vérifier les fichiers uploadés
        if (type.getName().equals("com.example.classe.UploadedFile")) {
            return uploadedFiles.get(name);
        }

        // 2. Vérifier List<UploadedFile>
        if (type.equals(List.class)) {
            // Chercher tous les fichiers avec ce nom (pour les tableaux)
            List<UploadedFile> files = new ArrayList<>();
            for (Map.Entry<String, UploadedFile> entry : uploadedFiles.entrySet()) {
                if (entry.getKey().startsWith(name + "[") || entry.getKey().equals(name)) {
                    files.add(entry.getValue());
                }
            }
            return files;
        }

        // 3. Valeur déjà convertie
        if (allValues.containsKey(name)) {
            Object value = allValues.get(name);
            if (value != null && type.isInstance(value)) {
                return value;
            }
        }

        // 4. Attribut de requête
        Object attrValue = req.getAttribute(name);
        if (attrValue != null && type.isInstance(attrValue)) {
            return type.cast(attrValue);
        }

        // 5. Valeur par défaut
        return getDefaultValue(type);
    }

    private Object getParameterValue(String name, Class<?> type,
            Map<String, Object> allValues,
            HttpServletRequest req) {
        return getParameterValue(name, type, allValues, req, new HashMap<>());
    }

    private Map<String, Object> collectAllValues(ScannerController.RouteData route,
            Map<String, String> pathVars,
            Map<String, String[]> reqParams,
            Map<String, UploadedFile> uploadedFiles,
            HttpServletRequest req) {
        Map<String, Object> allValues = new HashMap<>();

        // 1. Variables de chemin
        if (pathVars != null) {
            for (Map.Entry<String, String> entry : pathVars.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                Class<?> expectedType = route.paramTypes.getOrDefault(key, String.class);
                allValues.put(key, convertValue(value, expectedType));
            }
        }

        // 2. Paramètres de requête texte
        if (reqParams != null) {
            for (Map.Entry<String, String[]> entry : reqParams.entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();

                if (values != null && values.length > 0) {
                    Class<?> expectedType = route.paramTypes.getOrDefault(key, String.class);

                    if (isComplexObject(expectedType)) {
                        Object obj = createObjectFromParams(expectedType, reqParams);
                        allValues.put(key, obj);
                    } else if (values.length == 1) {
                        allValues.put(key, convertValue(values[0], expectedType));
                    } else {
                        // Convertir le tableau sans Stream API
                        Object[] arrayValues = new Object[values.length];
                        for (int i = 0; i < values.length; i++) {
                            arrayValues[i] = convertValue(values[i], String.class);
                        }
                        allValues.put(key, arrayValues);
                    }
                }
            }
        }

        return allValues;
    }

    private boolean isComplexObject(Class<?> type) {
        return !type.isPrimitive() &&
                !type.equals(String.class) &&
                !type.equals(Integer.class) &&
                !type.equals(Double.class) &&
                !type.equals(Boolean.class) &&
                !type.equals(Long.class) &&
                !type.equals(Float.class) &&
                !type.getName().equals("com.example.classe.UploadedFile") &&
                !Map.class.isAssignableFrom(type) &&
                !List.class.isAssignableFrom(type);
    }

    private Object createObjectFromParams(Class<?> type, Map<String, String[]> params) {
        try {
            Object instance = type.getDeclaredConstructor().newInstance();

            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                String fieldName = field.getName();

                if (params.containsKey(fieldName)) {
                    String[] values = params.get(fieldName);
                    if (values != null && values.length > 0) {
                        Object fieldValue = convertValue(values[0], field.getType());
                        field.set(instance, fieldValue);
                    }
                }
            }

            return instance;
        } catch (Exception e) {
            return null;
        }
    }

    private String getParamName(Parameter param) {
        if (param.isAnnotationPresent(Request.class)) {
            return param.getAnnotation(Request.class).value();
        }
        return param.getName();
    }

    private Object convertValue(String value, Class<?> type) {
        if (value == null) {
            return getDefaultValue(type);
        }
        return new Caste(value, type).getTypedValue();
    }

    private Object getDefaultValue(Class<?> type) {
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == boolean.class)
            return false;
        if (type == double.class)
            return 0.0;
        if (type == float.class)
            return 0.0f;
        if (type == byte.class)
            return (byte) 0;
        if (type == short.class)
            return (short) 0;
        if (type == char.class)
            return '\0';
        return null;
    }

    // ==================== VOTRE LOGIQUE ORIGINALE ====================
    private void handleResult(Object result, HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        if (result instanceof ModelVue) {
            ModelVue mv = (ModelVue) result;
            if (mv.getData() != null) {
                for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                    req.setAttribute(entry.getKey(), entry.getValue());
                }
            }
            req.getRequestDispatcher(mv.getView()).forward(req, resp);
        } else if (result instanceof String) {
            String str = (String) result;
            if (str.startsWith("redirect:")) {
                resp.sendRedirect(str.substring(9));
            } else {
                resp.setContentType("text/html");
                resp.getWriter().print(str);
            }
        } else if (result instanceof Map) {
            resp.setContentType("application/json");
            resp.getWriter().print(toJson((Map<?, ?>) result));
        } else if (result instanceof Iterable || result instanceof Object[]) {
            resp.setContentType("application/json");
            resp.getWriter().print(toJsonArray(result));
        } else if (result != null) {
            resp.setContentType("text/html");
            resp.getWriter().print(result.toString());
        }
    }

    // VOTRE méthode toJson originale
    private String toJson(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first)
                sb.append(",");
            first = false;

            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();

            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }
        }

        return sb.append("}").toString();
    }

    // VOTRE méthode toJsonArray originale
    private String toJsonArray(Object obj) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        if (obj instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) obj) {
                if (!first)
                    sb.append(",");
                first = false;
                sb.append(toJsonValue(item));
            }
        } else if (obj instanceof Object[]) {
            Object[] array = (Object[]) obj;
            for (Object item : array) {
                if (!first)
                    sb.append(",");
                first = false;
                sb.append(toJsonValue(item));
            }
        }

        return sb.append("]").toString();
    }

    // VOTRE méthode toJsonValue originale
    private String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + escapeJson((String) value) + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Map) {
            return toJson((Map<?, ?>) value);
        } else if (value instanceof Iterable || value instanceof Object[]) {
            return toJsonArray(value);
        } else {
            return "\"" + escapeJson(value.toString()) + "\"";
        }
    }

    // VOTRE méthode escapeJson originale
    private String escapeJson(String str) {
        if (str == null)
            return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void sendError(HttpServletResponse res, int code, String msg)
            throws IOException {
        res.setStatus(code);
        if (code == 405) {
            String url = "";
            int idx = msg.lastIndexOf("pour ");
            if (idx != -1) {
                url = msg.substring(idx + 5);
            }
            res.setHeader("Allow", getAllowedMethods(url));
        }
        res.setContentType("text/html");
        res.getWriter().println(
                "<!DOCTYPE html>" +
                        "<html>" +
                        "<head><title>Erreur " + code + "</title></head>" +
                        "<body>" +
                        "<h1>Erreur " + code + "</h1>" +
                        "<p>" + msg + "</p>" +
                        "</body>" +
                        "</html>");
    }
}