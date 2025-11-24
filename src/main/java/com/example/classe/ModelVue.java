package com.example.classe;

import java.util.Map;

public class ModelVue {
    String view;
    Map<String, Object> data;

    public ModelVue(String view) {
        this.setView(view);
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}