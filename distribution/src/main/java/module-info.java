module dev.lazyjvm.cli {
    requires dev.lazyjvm.domain;
    requires dev.lazyjvm.jvm;
    requires dev.lazyjvm.tui;
    requires info.picocli;

    opens dev.lazyjvm.cli to info.picocli;
}
