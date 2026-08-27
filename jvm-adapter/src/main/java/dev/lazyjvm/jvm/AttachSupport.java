package dev.lazyjvm.jvm;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Loads the Attach API on both modular JDKs and JDK 8.
 *
 * <p>JDK 9 and later expose the API from {@code jdk.attach}. JDK 8 keeps it
 * in {@code tools.jar}, which is available to the compiler but is not on the
 * class path of a normal application. Keeping this boundary reflective lets
 * the regular LazyJVM JAR remain executable on JDK 8.</p>
 */
final class AttachSupport {
    private static final String VIRTUAL_MACHINE = "com.sun.tools.attach.VirtualMachine";
    private static volatile Api api;

    private AttachSupport() {
    }

    static List<Descriptor> list() throws Exception {
        return getApi().list();
    }

    static AttachedVirtualMachine attach(long pid) throws Exception {
        return getApi().attach(pid);
    }

    private static Api getApi() throws Exception {
        Api value = api;
        if (value != null) return value;
        synchronized (AttachSupport.class) {
            value = api;
            if (value == null) {
                value = loadApi();
                api = value;
            }
            return value;
        }
    }

    private static Api loadApi() throws Exception {
        Class<?> virtualMachineClass;
        try {
            virtualMachineClass = Class.forName(VIRTUAL_MACHINE);
        } catch (ClassNotFoundException missing) {
            virtualMachineClass = loadFromJdkModule();
            if (virtualMachineClass == null) {
                virtualMachineClass = loadFromToolsJar(missing);
            }
        }
        if (virtualMachineClass == null) {
            throw new IllegalStateException("Attach API is unavailable in this JDK");
        }
        return new Api(virtualMachineClass);
    }

    private static Class<?> loadFromJdkModule() {
        try {
            Class<?> moduleLayerClass = Class.forName("java.lang.ModuleLayer");
            Object bootLayer = moduleLayerClass.getMethod("boot").invoke(null);
            Optional<?> module = (Optional<?>) moduleLayerClass
                    .getMethod("findModule", String.class)
                    .invoke(bootLayer, "jdk.attach");
            if (!module.isPresent()) return null;
            ClassLoader loader = (ClassLoader) module.get().getClass().getMethod("getClassLoader").invoke(module.get());
            return loader == null ? null : loader.loadClass(VIRTUAL_MACHINE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Class<?> loadFromToolsJar(ClassNotFoundException missing) throws Exception {
        Path toolsJar = findToolsJar();
        if (toolsJar == null) {
            throw new IllegalStateException(
                    "JDK 8 tools.jar was not found; local JVM discovery requires a full JDK", missing);
        }
        URLClassLoader loader = new URLClassLoader(
                new URL[] {toolsJar.toUri().toURL()}, AttachSupport.class.getClassLoader());
        return Class.forName(VIRTUAL_MACHINE, true, loader);
    }

    private static Path findToolsJar() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.trim().isEmpty()) return null;
        Path home = Paths.get(javaHome);
        Path[] candidates = new Path[] {
                home.resolve("lib").resolve("tools.jar"),
                home.resolve("..").resolve("lib").resolve("tools.jar")
        };
        for (Path candidate : candidates) {
            try {
                File file = candidate.toFile().getCanonicalFile();
                if (Files.isRegularFile(file.toPath())) return file.toPath();
            } catch (Exception ignored) {
                // Try next candidate.
            }
        }
        return null;
    }

    static final class Descriptor {
        private final String id;
        private final String displayName;

        private Descriptor(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        String id() {
            return id;
        }

        String displayName() {
            return displayName;
        }
    }

    static final class AttachedVirtualMachine {
        private final Api api;
        private final Object delegate;

        private AttachedVirtualMachine(Api api, Object delegate) {
            this.api = api;
            this.delegate = delegate;
        }

        Properties getAgentProperties() throws Exception {
            return (Properties) api.invoke(api.getAgentProperties, delegate);
        }

        String startLocalManagementAgent() throws Exception {
            return (String) api.invoke(api.startLocalManagementAgent, delegate);
        }

        Properties getSystemProperties() throws Exception {
            return (Properties) api.invoke(api.getSystemProperties, delegate);
        }

        void detach() throws Exception {
            api.invoke(api.detach, delegate);
        }
    }

    private static final class Api {
        private final Method list;
        private final Method attach;
        private final Method descriptorId;
        private final Method descriptorDisplayName;
        private final Method getAgentProperties;
        private final Method startLocalManagementAgent;
        private final Method getSystemProperties;
        private final Method detach;

        private Api(Class<?> virtualMachineClass) throws Exception {
            list = virtualMachineClass.getMethod("list");
            attach = virtualMachineClass.getMethod("attach", String.class);
            descriptorId = Class.forName(
                    "com.sun.tools.attach.VirtualMachineDescriptor", true,
                    virtualMachineClass.getClassLoader()).getMethod("id");
            descriptorDisplayName = descriptorId.getDeclaringClass().getMethod("displayName");
            getAgentProperties = virtualMachineClass.getMethod("getAgentProperties");
            startLocalManagementAgent = virtualMachineClass.getMethod("startLocalManagementAgent");
            getSystemProperties = virtualMachineClass.getMethod("getSystemProperties");
            detach = virtualMachineClass.getMethod("detach");
        }

        private List<Descriptor> list() throws Exception {
            List<?> values = (List<?>) invoke(list, null);
            List<Descriptor> descriptors = new ArrayList<>();
            for (Object value : values) {
                descriptors.add(new Descriptor(
                        (String) invoke(descriptorId, value),
                        (String) invoke(descriptorDisplayName, value)));
            }
            return descriptors;
        }

        private AttachedVirtualMachine attach(long pid) throws Exception {
            return new AttachedVirtualMachine(this, invoke(attach, null, Long.toString(pid)));
        }

        private Object invoke(Method method, Object target, Object... arguments) throws Exception {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw new Exception(cause);
            }
        }
    }
}
