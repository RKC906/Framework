package com.example.classe;

public class Caste {
    String value;
    Class<?> type;

    public Caste(String value, Class<?> type) {
        this.setValue(value);
        this.setType(type);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Class<?> getType() {
        return type;
    }

    public void setType(Class<?> type) {
        this.type = type;
    }

    /**
     * Retourne la valeur typée selon le type spécifié.
     * 
     * @return l'objet de type type, converti depuis la valeur String.
     */
    public Object getTypedValue() {
        if (type == String.class) {
            return value;
        } else if (type == Integer.class || type == int.class) {
            return Integer.valueOf(value);
        } else if (type == Double.class || type == double.class) {
            return Double.valueOf(value);
        } else if (type == Boolean.class || type == boolean.class) {
            return Boolean.valueOf(value);
        } else if (type == Long.class || type == long.class) {
            return Long.valueOf(value);
        } else if (type == Float.class || type == float.class) {
            return Float.valueOf(value);
        }
        // Pour les autres types, retourner la valeur String par défaut
        return value;
    }
}