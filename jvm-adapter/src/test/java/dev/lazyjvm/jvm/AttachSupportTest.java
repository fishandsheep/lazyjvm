package dev.lazyjvm.jvm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AttachSupportTest {
    @Test
    void loadsAttachApiOnCurrentJdk() throws Exception {
        List<AttachSupport.Descriptor> descriptors = AttachSupport.list();

        assertNotNull(descriptors);
    }
}
