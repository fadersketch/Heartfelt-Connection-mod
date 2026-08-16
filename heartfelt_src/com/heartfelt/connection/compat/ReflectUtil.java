package com.heartfelt.connection.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反射工具(v1.1.0 全面重构)——全模组反射调用收敛点。
 *
 * 设计:方法句柄与类探活全部【只缓存成功】——失败下次重试(修 A9:
 * 早期调用时依赖类尚未加载 → 不永久降级,加载完成后自动恢复)。
 * 所有 invoke 均安全降级,不抛异常。
 */
public final class ReflectUtil {
    /** 方法句柄缓存:声明类名#方法名 */
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    /** 已成功加载的类缓存 */
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    private ReflectUtil() {
    }

    /** 类探活(成功才缓存;未加载返回 null) */
    public static Class<?> load(String className) {
        Class<?> cached = CLASS_CACHE.get(className);
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> cls = Class.forName(className);
            CLASS_CACHE.putIfAbsent(className, cls);
            return cls;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 方法句柄(成功才缓存;解析失败返回 null,下次调用重试)。
     * v1.5.114:缓存键加入参数类型——旧键只有"类#方法名",同名重载会缓存碰撞,
     * 后来的调用拿到前一个签名的 Method,invoke 参数不匹配抛 IllegalArgumentException
     * (它不是 ReflectiveOperationException,invokeStatic 的 catch 接不住,直接崩)。
     * v1.5.114:owner 为 null(类未加载)时返回 null,不再 NPE。
     */
    public static Method method(Class<?> owner, String name, Class<?>... params) {
        if (owner == null) {
            return null;
        }
        String key = owner.getName() + "#" + name + java.util.Arrays.toString(params);
        Method m = METHOD_CACHE.get(key);
        if (m != null) {
            return m;
        }
        try {
            m = owner.getMethod(name, params);
        } catch (ReflectiveOperationException ignored) {
            // 审计：私有/非 public 方法走 getDeclaredMethod 并放开访问（releaseCarry 等场景）
            try {
                m = owner.getDeclaredMethod(name, params);
                m.setAccessible(true);
            } catch (ReflectiveOperationException ignored2) {
            }
        }
        if (m != null) {
            METHOD_CACHE.putIfAbsent(key, m);
        }
        return m;
    }

    /** 按类名取静态方法(类不存在/方法不存在返回 null) */
    public static Method staticMethod(String className, String name, Class<?>... params) {
        Class<?> cls = load(className);
        return cls == null ? null : method(cls, name, params);
    }

    /** 安全静态调用;方法为 null 或调用异常均返回 null */
    public static Object invokeStatic(Method m, Object... args) {
        if (m == null) {
            return null;
        }
        try {
            return m.invoke(null, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 安全实例调用;返回 null 表示失败 */
    public static Object invoke(Method m, Object target, Object... args) {
        if (m == null) {
            return null;
        }
        try {
            return m.invoke(target, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 按参数匹配调用构造器(v1.4.3,调整器写 record 用)。
     * 遍历声明构造器找参数数量与类型兼容者(原始类型自动装箱比较);失败返回 null。
     */
    public static Object newInstance(Class<?> cls, Object... args) {
        if (cls == null) {
            return null;
        }
        try {
            for (Constructor<?> c : cls.getDeclaredConstructors()) {
                if (c.getParameterCount() != args.length) {
                    continue;
                }
                boolean match = true;
                Class<?>[] params = c.getParameterTypes();
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == null
                            || box(params[i]).isAssignableFrom(box(args[i].getClass()))) {
                        continue;
                    }
                    match = false;
                    break;
                }
                if (match) {
                    c.setAccessible(true);
                    return c.newInstance(args);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 读静态字段值(如 MarriageData.EMPTY);类/字段不存在或失败返回 null */
    public static Object staticField(String className, String fieldName) {
        Class<?> cls = load(className);
        if (cls == null) {
            return null;
        }
        try {
            Field f = cls.getField(fieldName);
            return f.get(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 原始类型装箱(参数匹配用) */
    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) {
            return c;
        }
        if (c == int.class) {
            return Integer.class;
        }
        if (c == long.class) {
            return Long.class;
        }
        if (c == boolean.class) {
            return Boolean.class;
        }
        if (c == float.class) {
            return Float.class;
        }
        if (c == double.class) {
            return Double.class;
        }
        if (c == short.class) {
            return Short.class;
        }
        if (c == byte.class) {
            return Byte.class;
        }
        if (c == char.class) {
            return Character.class;
        }
        return c;
    }
}
