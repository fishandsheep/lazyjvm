package dev.lazyjvm.domain;

public record GcSnapshot(String name, long collectionCount, long collectionTimeMillis, String pools) {}
