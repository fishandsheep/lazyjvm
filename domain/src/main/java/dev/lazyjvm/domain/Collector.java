package dev.lazyjvm.domain;

public interface Collector extends AutoCloseable {
    CapabilitySet capabilities();
    MetricSnapshot sample() throws Exception;
    @Override void close() throws Exception;
}
