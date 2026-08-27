package dev.lazyjvm.jvm;

import dev.lazyjvm.domain.JdkIdentity;
import dev.lazyjvm.domain.TargetJvm;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class LocalJmxSession implements AutoCloseable {
    private static final String CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";

    private final AttachSupport.AttachedVirtualMachine virtualMachine;
    private final JMXConnector connector;
    private final TargetJvm target;

    private LocalJmxSession(AttachSupport.AttachedVirtualMachine virtualMachine, JMXConnector connector, TargetJvm target) {
        this.virtualMachine = virtualMachine;
        this.connector = connector;
        this.target = target;
    }

    public static LocalJmxSession attach(TargetJvm target) throws Exception {
        AttachSupport.AttachedVirtualMachine vm = AttachSupport.attach(target.pid());
        try {
            Properties agentProperties = vm.getAgentProperties();
            String address = agentProperties.getProperty(CONNECTOR_ADDRESS);
            if (address == null || address.trim().isEmpty()) address = vm.startLocalManagementAgent();
            if (address == null || address.trim().isEmpty()) {
                throw new IllegalStateException("Target JVM did not publish a local JMX connector");
            }
            Properties system = vm.getSystemProperties();
            Path home = pathOrNull(system.getProperty("java.home"));
            JdkIdentity identity = new JdkIdentity(
                    home,
                    system.getProperty("java.version", "unknown"),
                    system.getProperty("java.vendor", "unknown"),
                    home != null);
            JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(address));
            return new LocalJmxSession(vm, connector, target.withJdk(identity));
        } catch (Exception failure) {
            vm.detach();
            throw failure;
        }
    }

    public MBeanServerConnection connection() throws Exception {
        return connector.getMBeanServerConnection();
    }

    public TargetJvm target() {
        return target;
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        try {
            connector.close();
        } catch (Exception exception) {
            failure = exception;
        }
        try {
            virtualMachine.detach();
        } catch (Exception exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }

    private static Path pathOrNull(String value) {
        try {
            return value == null || value.trim().isEmpty() ? null : Paths.get(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}
