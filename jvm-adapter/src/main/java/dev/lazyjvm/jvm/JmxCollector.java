package dev.lazyjvm.jvm;

import dev.lazyjvm.domain.Capability;
import dev.lazyjvm.domain.CapabilitySet;
import dev.lazyjvm.domain.Collector;
import dev.lazyjvm.domain.GcSnapshot;
import dev.lazyjvm.domain.MemoryPoolSnapshot;
import dev.lazyjvm.domain.MetricKey;
import dev.lazyjvm.domain.MetricPoint;
import dev.lazyjvm.domain.MetricQuality;
import dev.lazyjvm.domain.MetricSnapshot;
import dev.lazyjvm.domain.ThreadSnapshot;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class JmxCollector implements Collector {
    private final LocalJmxSession session;
    private final MBeanServerConnection connection;
    private final MemoryMXBean memory;
    private final RuntimeMXBean runtime;
    private final ThreadMXBean threads;
    private final ClassLoadingMXBean classes;
    private final com.sun.management.OperatingSystemMXBean operatingSystem;
    private final List<MemoryPoolMXBean> pools;
    private final List<GarbageCollectorMXBean> collectors;
    private final CapabilitySet capabilities;

    public JmxCollector(LocalJmxSession session) throws Exception {
        this.session = session;
        this.connection = session.connection();
        this.memory = ManagementFactory.newPlatformMXBeanProxy(connection,
                ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class);
        this.runtime = ManagementFactory.newPlatformMXBeanProxy(connection,
                ManagementFactory.RUNTIME_MXBEAN_NAME, RuntimeMXBean.class);
        this.threads = ManagementFactory.newPlatformMXBeanProxy(connection,
                ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);
        this.classes = ManagementFactory.newPlatformMXBeanProxy(connection,
                ManagementFactory.CLASS_LOADING_MXBEAN_NAME, ClassLoadingMXBean.class);
        this.operatingSystem = ManagementFactory.getPlatformMXBean(connection,
                com.sun.management.OperatingSystemMXBean.class);
        this.pools = ManagementFactory.getPlatformMXBeans(connection, MemoryPoolMXBean.class);
        this.collectors = ManagementFactory.getPlatformMXBeans(connection, GarbageCollectorMXBean.class);

        EnumSet<Capability> found = EnumSet.of(Capability.JMX, Capability.MEMORY_POOLS,
                Capability.GARBAGE_COLLECTION, Capability.THREADS);
        if (operatingSystem != null) found.add(Capability.PROCESS_CPU);
        if (threads.isThreadCpuTimeSupported()) found.add(Capability.THREAD_CPU);
        if (threads.isObjectMonitorUsageSupported() || threads.isSynchronizerUsageSupported()) {
            found.add(Capability.DEADLOCK_DETECTION);
        }
        if (hasFlightRecorder(connection)) found.add(Capability.JFR);
        this.capabilities = new CapabilitySet(found);
    }

    public LocalJmxSession session() {
        return session;
    }

    @Override
    public CapabilitySet capabilities() {
        return capabilities;
    }

    @Override
    public MetricSnapshot sample() throws Exception {
        long started = System.nanoTime();
        Instant now = Instant.now();
        Map<MetricKey, MetricPoint> values = new HashMap<>();
        add(values, now, MetricKey.HEAP_USED, memory.getHeapMemoryUsage().getUsed());
        add(values, now, MetricKey.HEAP_COMMITTED, memory.getHeapMemoryUsage().getCommitted());
        add(values, now, MetricKey.HEAP_MAX, memory.getHeapMemoryUsage().getMax());
        add(values, now, MetricKey.NON_HEAP_USED, memory.getNonHeapMemoryUsage().getUsed());
        add(values, now, MetricKey.THREADS_LIVE, threads.getThreadCount());
        add(values, now, MetricKey.THREADS_PEAK, threads.getPeakThreadCount());
        add(values, now, MetricKey.CLASSES_LOADED, classes.getLoadedClassCount());
        add(values, now, MetricKey.UPTIME, runtime.getUptime());

        if (operatingSystem != null) {
            add(values, now, MetricKey.PROCESS_CPU, percent(safeCpu(operatingSystem::getProcessCpuLoad)));
            add(values, now, MetricKey.SYSTEM_CPU, percent(systemCpuLoad()));
        }

        List<MemoryPoolSnapshot> poolValues = pools.stream().filter(pool -> pool.getUsage() != null)
                .map(pool -> new MemoryPoolSnapshot(
                        pool.getName(), pool.getType() == MemoryType.HEAP ? "heap" : "non-heap",
                        pool.getUsage().getUsed(), pool.getUsage().getCommitted(), pool.getUsage().getMax()))
                .collect(Collectors.toList());

        long totalCount = 0;
        long totalTime = 0;
        List<GcSnapshot> gcValues = new ArrayList<>();
        for (GarbageCollectorMXBean collector : collectors) {
            long count = Math.max(0, collector.getCollectionCount());
            long time = Math.max(0, collector.getCollectionTime());
            totalCount += count;
            totalTime += time;
            gcValues.add(new GcSnapshot(collector.getName(), count, time, String.join(", ", collector.getMemoryPoolNames())));
        }
        add(values, now, MetricKey.GC_COUNT, totalCount);
        add(values, now, MetricKey.GC_TIME, totalTime);

        ThreadSnapshot threadValues = threadSnapshot();
        return new MetricSnapshot(now, values, poolValues, gcValues, threadValues, capabilities,
                Duration.ofNanos(System.nanoTime() - started), Collections.<String>emptyList());
    }

    private ThreadSnapshot threadSnapshot() throws Exception {
        EnumMap<Thread.State, Integer> states = new EnumMap<>(Thread.State.class);
        long[] ids = threads.getAllThreadIds();
        ThreadInfo[] info = threads.getThreadInfo(ids, 0);
        for (ThreadInfo thread : info) {
            if (thread != null) states.merge(thread.getThreadState(), 1, Integer::sum);
        }
        long[] deadlocked = capabilities.has(Capability.DEADLOCK_DETECTION)
                ? threads.findDeadlockedThreads()
                : null;
        return new ThreadSnapshot(threads.getThreadCount(), threads.getDaemonThreadCount(),
                threads.getPeakThreadCount(), states, deadlocked);
    }

    private static void add(Map<MetricKey, MetricPoint> target, Instant now, MetricKey key, double value) {
        MetricQuality quality = Double.isFinite(value) && value >= 0 ? MetricQuality.EXACT : MetricQuality.UNAVAILABLE;
        target.put(key, new MetricPoint(now, key, value, quality, "JMX"));
    }

    private static double percent(double fraction) {
        return fraction < 0 ? Double.NaN : Math.min(100.0, fraction * 100.0);
    }

    private double systemCpuLoad() {
        return safeCpu(operatingSystem::getSystemCpuLoad);
    }

    private static double safeCpu(CpuSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception | LinkageError ignored) {
            return -1;
        }
    }

    @FunctionalInterface
    private interface CpuSupplier {
        double get();
    }

    private static boolean hasFlightRecorder(MBeanServerConnection connection) {
        try {
            Set<ObjectName> names = connection.queryNames(new ObjectName("jdk.management.jfr:type=FlightRecorder"), null);
            return !names.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void close() throws Exception {
        session.close();
    }
}
