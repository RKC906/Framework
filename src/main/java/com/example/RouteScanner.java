package com.example;

import com.example.annotation.*;

import java.lang.reflect.Method;

public class RouteScanner {

    public static void scanRoutes(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {

            if (method.isAnnotationPresent(Get.class)) {
                Get get = method.getAnnotation(Get.class);
                System.out.println("GET route: " + get.value() + " -> " + method.getName());
            }

            if (method.isAnnotationPresent(Post.class)) {
                Post post = method.getAnnotation(Post.class);
                System.out.println("POST route: " + post.value() + " -> " + method.getName());
            }
        }
    }
}