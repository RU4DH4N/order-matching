package ru4dh4n.ordermatching.components;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru4dh4n.ordermatching.annotations.PublicEndpoint;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PublicMethodRegistry implements InitializingBean, ApplicationContextAware {

    private final Set<String> publicMethods = ConcurrentHashMap.newKeySet();
    private ApplicationContext applicationContext;

    @Value("${grpc.service.scan.packages:ru4dh4n.ordermatching}")
    private String basePackage;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() {
        scanForPublicMethods();
    }

    private void scanForPublicMethods() {
        DefaultListableBeanFactory beanFactory =
                (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();

        String[] beanNames = beanFactory.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            var beanDef = beanFactory.getBeanDefinition(beanName);
            String beanClassName = beanDef.getBeanClassName();
            if (beanClassName == null) continue;

            try {
                Class<?> beanClass = Class.forName(beanClassName);
                String packageName = beanClass.getPackageName();

                if (!packageName.startsWith(basePackage)) continue;
                if (!isGrpcService(beanClass)) continue;

                for (Method method : beanClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(PublicEndpoint.class)) {
                        String grpcMethodName = toGrpcMethodName(method.getName());
                        String serviceName = inferServiceName(beanClass);
                        String fullName = serviceName + "/" + grpcMethodName;
                        publicMethods.add(fullName);
                    }
                }
            } catch (ClassNotFoundException ignored) { }
        }
    }

    public boolean isPublicMethod(String fullMethodName) {
        return publicMethods.contains(fullMethodName);
    }

    private boolean isGrpcService(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null) {
            if (current.getSimpleName().contains("ImplBase")) return true;
            for (Class<?> iface : current.getInterfaces()) {
                if (iface.getName().contains("BindableService")) return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private String inferServiceName(Class<?> implClass) {
        String className = implClass.getSimpleName();
        return basePackage + "." + className;
    }

    // I do not like this :( TODO: make betterer
    private String toGrpcMethodName(String javaMethodName) {
        return Character.toUpperCase(javaMethodName.charAt(0)) + javaMethodName.substring(1);
    }
}
