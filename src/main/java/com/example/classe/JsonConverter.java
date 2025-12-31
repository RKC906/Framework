package com.example.classe;

import java.lang.reflect.Field;
import java.util.*;

public class JsonConverter {

    /**
     * Convertit un objet en JSON
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }

        if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        }

        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }

        if (obj instanceof Map) {
            return mapToJson((Map<?, ?>) obj);
        }

        if (obj instanceof Iterable) {
            return iterableToJson((Iterable<?>) obj);
        }

        if (obj instanceof Object[]) {
            return arrayToJson((Object[]) obj);
        }

        if (obj.getClass().isArray()) {
            // Pour les tableaux primitifs
            return primitiveArrayToJson(obj);
        }

        // Pour les objets Java POJO
        return objectToJson(obj);
    }

    private static String mapToJson(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;

            sb.append("\"").append(escapeJson(entry.getKey().toString())).append("\":");
            sb.append(toJson(entry.getValue()));
        }

        return sb.append("}").toString();
    }

    private static String iterableToJson(Iterable<?> iterable) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (Object item : iterable) {
            if (!first) {
                sb.append(",");
            }
            first = false;

            sb.append(toJson(item));
        }

        return sb.append("]").toString();
    }

    private static String arrayToJson(Object[] array) {
        StringBuilder sb = new StringBuilder("[");

        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(toJson(array[i]));
        }

        return sb.append("]").toString();
    }

    private static String primitiveArrayToJson(Object array) {
        if (array instanceof int[]) {
            return Arrays.toString((int[]) array).replace(" ", "");
        } else if (array instanceof long[]) {
            return Arrays.toString((long[]) array).replace(" ", "");
        } else if (array instanceof double[]) {
            return Arrays.toString((double[]) array).replace(" ", "");
        } else if (array instanceof float[]) {
            return Arrays.toString((float[]) array).replace(" ", "");
        } else if (array instanceof boolean[]) {
            return Arrays.toString((boolean[]) array).replace(" ", "");
        } else if (array instanceof byte[]) {
            return Arrays.toString((byte[]) array).replace(" ", "");
        } else if (array instanceof short[]) {
            return Arrays.toString((short[]) array).replace(" ", "");
        } else if (array instanceof char[]) {
            // Pour char[], on veut les caractères entre guillemets
            char[] chars = (char[]) array;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < chars.length; i++) {
                if (i > 0)
                    sb.append(",");
                sb.append("\"").append(chars[i]).append("\"");
            }
            return sb.append("]").toString();
        }
        return "[]";
    }

    private static String objectToJson(Object obj) {
        try {
            StringBuilder sb = new StringBuilder("{");
            Class<?> clazz = obj.getClass();
            Field[] fields = clazz.getDeclaredFields();
            boolean first = true;

            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(obj);

                // Ne pas inclure les champs null si on veut
                if (value != null) {
                    if (!first) {
                        sb.append(",");
                    }
                    first = false;

                    sb.append("\"").append(field.getName()).append("\":");
                    sb.append(toJson(value));
                }
            }

            return sb.append("}").toString();
        } catch (Exception e) {
            return "\"" + escapeJson(obj.toString()) + "\"";
        }
    }

    private static String escapeJson(String str) {
        if (str == null)
            return "";

        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }
}