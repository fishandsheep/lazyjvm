module dev.lazyjvm.jvm {
    requires dev.lazyjvm.domain;
    requires java.management;
    requires java.management.rmi;
    requires jdk.attach;
    requires jdk.management;

    exports dev.lazyjvm.jvm;
}
