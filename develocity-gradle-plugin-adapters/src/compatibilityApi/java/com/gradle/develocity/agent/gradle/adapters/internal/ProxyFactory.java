package com.gradle.develocity.agent.gradle.adapters.internal;

import org.gradle.api.Action;
import org.gradle.api.specs.Spec;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

public final class ProxyFactory {

    public static <T> T createProxy(Object target, Class<T> targetInterface) {
        return createProxy(target, targetInterface, target);
    }

    private static <T> T createProxy(Object target, Class<T> targetInterface, Object topLevelDevelocityTarget) {
        return newProxyInstance(targetInterface, new ProxyingInvocationHandler(target, topLevelDevelocityTarget));
    }

    @SuppressWarnings("unchecked")
    private static <T> T newProxyInstance(Class<T> targetInterface, InvocationHandler invocationHandler) {
        return (T) Proxy.newProxyInstance(targetInterface.getClassLoader(), new Class[]{targetInterface}, invocationHandler);
    }

    private static final class ProxyingInvocationHandler implements InvocationHandler {

        private final Object target;
        // this is the top-level Develocity extension instance that we proxy to.
        // We pass it around to be able to access a classloader for Develocity types in a configuration cache compatible manner
        private final Object topLevelDevelocityTarget;

        private ProxyingInvocationHandler(Object target, Object topLevelDevelocityTarget) {
            this.target = target;
            this.topLevelDevelocityTarget = topLevelDevelocityTarget;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            try {
                ClassLoader develocityTypesClassLoader = topLevelDevelocityTarget.getClass().getClassLoader();
                Method targetMethod = target.getClass().getMethod(method.getName(), convertTypes(method.getParameterTypes(), develocityTypesClassLoader));
                Object[] targetArgs = toTargetArgs(args, develocityTypesClassLoader);

                // we always invoke public methods, but we need to make it accessible when it is implemented in anonymous classes
                targetMethod.setAccessible(true);

                Object result = targetMethod.invoke(target, targetArgs);
                if (result == null || isJdkTypeOrThrowable(result.getClass())) {
                    return result;
                } else if (result instanceof Enum) {
                    return adaptEnumArg((Enum<?>) result, develocityTypesClassLoader);
                }
                return createProxy(result, method.getReturnType(), topLevelDevelocityTarget);
            } catch (Throwable e) {
                throw new RuntimeException("Failed to invoke " + method + " on " + target + " with args " + Arrays.toString(args), e);
            }
        }

        private Object[] toTargetArgs(Object[] args, ClassLoader classLoader) throws ClassNotFoundException {
            if (args == null || args.length == 0) {
                return args;
            }
            if (args.length == 1 && args[0] instanceof Action) {
                return new Object[]{adaptActionArg((Action<?>) args[0])};
            }
            if (args.length == 1 && args[0] instanceof Spec) {
                return new Object[]{adaptSpecArg((Spec<?>) args[0])};
            }
            if (args.length == 1 && args[0] instanceof Function) {
                return new Object[]{adaptFunctionArg((Function<?, ?>) args[0])};
            }
            if (args.length == 1 && args[0] instanceof Enum) {
                return new Object[]{adaptEnumArg((Enum<?>) args[0], classLoader)};
            }
            if (Arrays.stream(args).allMatch(it -> isJdkTypeOrThrowable(it.getClass()))) {
                return args;
            }
            throw new RuntimeException("Unsupported argument types in " + Arrays.toString(args));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Enum<?> adaptEnumArg(Enum<?> arg, ClassLoader classLoader) throws ClassNotFoundException {
            return Enum.valueOf((Class<Enum>) classLoader.loadClass(arg.getClass().getName()), arg.name());
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Action<Object> adaptActionArg(Action action) {
            return arg -> action.execute(createLocalProxy(arg));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Spec<Object> adaptSpecArg(Spec spec) {
            return arg -> spec.isSatisfiedBy(createLocalProxy(arg));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Function<Object, Object> adaptFunctionArg(Function func) {
            return arg -> func.apply(createLocalProxy(arg));
        }

        private Object createLocalProxy(Object target) {
            if (isJdkTypeOrThrowable(target.getClass())) {
                return target;
            }

            ClassLoader localClassLoader = ProxyFactory.class.getClassLoader();
            return Proxy.newProxyInstance(
                localClassLoader,
                convertTypes(collectInterfaces(target.getClass()), localClassLoader),
                new ProxyingInvocationHandler(target, topLevelDevelocityTarget)
            );
        }

        public static Class<?>[] collectInterfaces(final Class<?> type) {
            Set<Class<?>> result = new LinkedHashSet<>();
            collectInterfaces(type, result);
            return result.toArray(new Class<?>[0]);
        }

        private static void collectInterfaces(Class<?> type, Set<Class<?>> result) {
            for (Class<?> candidate = type; candidate != null; candidate = candidate.getSuperclass()) {
                for (Class<?> i : candidate.getInterfaces()) {
                    if (result.add(i)) {
                        collectInterfaces(i, result);
                    }
                }
            }
        }

        private static Class<?>[] convertTypes(Class<?>[] parameterTypes, ClassLoader classLoader) {
            if (parameterTypes.length == 0) {
                return parameterTypes;
            }
            return Arrays.stream(parameterTypes)
                .map(type -> {
                    if (isJdkTypeOrThrowable(type)) {
                        return type;
                    }

                    try {
                        return classLoader.loadClass(type.getName());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load class: " + type.getName(), e);
                    }
                })
                .toArray(Class<?>[]::new);
        }

        private static boolean isJdkTypeOrThrowable(Class<?> type) {
            ClassLoader typeClassLoader = type.getClassLoader();
            // JDK types are present across classloaders, so there is no need to create proxies for those
            // we can't create proxies for instances of Throwable as there is no shared interface
            return typeClassLoader == null || typeClassLoader.equals(Object.class.getClassLoader()) || Throwable.class.isAssignableFrom(type);
        }

    }

}
