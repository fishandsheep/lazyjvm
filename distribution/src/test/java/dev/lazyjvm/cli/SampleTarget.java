package dev.lazyjvm.cli;

public final class SampleTarget {
    private SampleTarget() {}

    public static void main(String[] args) throws InterruptedException {
        while (true) Thread.sleep(1_000L);
    }
}
