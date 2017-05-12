package org.jetbrains.test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class Util {
    private static final Map<Class<?>, Class<?>> PRIMITIVES_TO_WRAPPERS;
    private static final Map<String, Class<?>> PRIMITIVES_BY_NAME;

    static {
        // usually Apache Commons Lang will do this for me :(
        PRIMITIVES_TO_WRAPPERS = new HashMap<Class<?>, Class<?>>() {{
            put(boolean.class, Boolean.class);
            put(byte.class, Byte.class);
            put(char.class, Character.class);
            put(double.class, Double.class);
            put(float.class, Float.class);
            put(int.class, Integer.class);
            put(long.class, Long.class);
            put(short.class, Short.class);
            put(void.class, Void.class);
        }};
        // and that could be done by Spring's ClassUtils...
        PRIMITIVES_BY_NAME = new HashMap<>();
        PRIMITIVES_TO_WRAPPERS.keySet().forEach(token ->
                PRIMITIVES_BY_NAME.put(token.getName(), token));
    }

    public static Class<?> unwrap(Class<?> source) {
        return PRIMITIVES_TO_WRAPPERS.getOrDefault(source, source);
    }

    public static Class<?> forName(String name) throws ClassNotFoundException {
        if (PRIMITIVES_BY_NAME.containsKey(name)) {
            return PRIMITIVES_BY_NAME.get(name);
        } else {
            return Class.forName(name);
        }
    }

    public static Method getMethod(Class<?> caller, String name, Class<?>[] params) throws NoSuchMethodException {
        return caller == null ? null : caller.getDeclaredMethod(name, params);
    }
}
