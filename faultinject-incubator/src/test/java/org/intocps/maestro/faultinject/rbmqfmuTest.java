package org.intocps.maestro.faultinject;

import org.apache.commons.io.IOUtils;
import org.intocps.maestro.faultinject.incubator.Main;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public class rbmqfmuTest {
    @Test
    public void testWithConfig() throws Exception {
        String initializePath = rbmqfmuTest.class.getClassLoader().getResource("multimodel.json").getPath();
        String simulateJson = rbmqfmuTest.class.getClassLoader().getResource("coe.json").getPath();
        String dumpPath = "target/test-classes/rbmq_example/dump";
        final File faultInjectSpec = Paths.get("target", "rbmqfmutest", "FaultInject.mabl").toFile();
        faultInjectSpec.getParentFile().mkdirs();
        try (final FileWriter writer = new FileWriter(faultInjectSpec)) {
            IOUtils.copy(FaultInjectRuntimeModule.class.getResourceAsStream("FaultInject.mabl"), writer, StandardCharsets.UTF_8);
        }
        org.intocps.maestro.Main.argumentHandler(new String[]{"import","-output",dumpPath, "-i","Sg1",initializePath, simulateJson,faultInjectSpec.getPath()} );
    }

    @Test
    public void testMainWithConfig() throws IOException {
        String initializePath = rbmqfmuTest.class.getClassLoader().getResource("multimodel.json").getPath();
        String simulateJson = rbmqfmuTest.class.getClassLoader().getResource("coe.json").getPath();
        String dumpPath = "target/test-classes/rbmq_example/dump";
        Main.main(new String[]{initializePath, simulateJson, dumpPath});

    }
}
