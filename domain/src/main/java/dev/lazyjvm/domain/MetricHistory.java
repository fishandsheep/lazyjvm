package dev.lazyjvm.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class MetricHistory {
    private final MetricSnapshot[] samples;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private int head;
    private int size;

    public MetricHistory(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.samples = new MetricSnapshot[capacity];
    }

    public void add(MetricSnapshot sample) {
        lock.writeLock().lock();
        try {
            samples[head] = sample;
            head = (head + 1) % samples.length;
            size = Math.min(size + 1, samples.length);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<MetricSnapshot> snapshot() {
        lock.readLock().lock();
        try {
            List<MetricSnapshot> copy = new ArrayList<>(size);
            int start = (head - size + samples.length) % samples.length;
            for (int i = 0; i < size; i++) copy.add(samples[(start + i) % samples.length]);
            return Collections.unmodifiableList(copy);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return size;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int capacity() {
        return samples.length;
    }
}
