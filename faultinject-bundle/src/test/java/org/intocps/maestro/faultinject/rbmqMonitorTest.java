package org.intocps.maestro.faultinject;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public class rbmqMonitorTest {
    @Test
    @Ignore("This test cannot be run on its own, but needs the scripts that publish to the rabbitmq server. Look into rbmq_example")
    public void testWithConfig() throws Exception {
        String initializePath = SimpleTest.class.getClassLoader().getResource("rbmq_example/multimodel.json").getPath();
        String simulateJson = SimpleTest.class.getClassLoader().getResource("rbmq_example/coe.json").getPath();
        String dumpPath = "target/test-classes/rbmq_example/dump";
        final File faultInjectSpec = Paths.get("target", "rbmqmonitortest", "FaultInject.mabl").toFile();
        faultInjectSpec.getParentFile().mkdirs();
        try (final FileWriter writer = new FileWriter(faultInjectSpec)) {
            IOUtils.copy(FaultInjectRuntimeModule.class.getResourceAsStream("FaultInject.mabl"), writer, StandardCharsets.UTF_8);
        }
        org.intocps.maestro.Main.argumentHandler(new String[]{"import","-output",dumpPath, "-i","Sg1",initializePath, simulateJson,faultInjectSpec.getPath()} );
    }
}
