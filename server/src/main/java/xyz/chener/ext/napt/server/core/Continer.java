package xyz.chener.ext.napt.server.core;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class Continer {

    private static Map<Class, Object> objs = new ConcurrentHashMap<>();

    public static <T> T get(Class<T> clazz) {
        if (!objs.containsKey(clazz)) {
            log.warn("未注册对象:{}", clazz.getName());
            return null;
        }
        return (T) objs.get(clazz);
    }

    public static <T> T put(Class<T> clazz, T obj) {
        if (objs.containsKey(clazz)) {
            log.warn("重复注册对象:{}", clazz.getName());
            throw new RuntimeException("重复注册对象");
        }
        objs.put(clazz, obj);
        return obj;
    }

}
