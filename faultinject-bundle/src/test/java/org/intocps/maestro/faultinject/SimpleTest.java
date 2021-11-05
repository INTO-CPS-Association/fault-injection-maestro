package org.intocps.maestro.faultinject;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.logging.log4j.Level;
import org.intocps.maestro.typechecker.TypeChecker;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import org.junit.Before;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import org.apache.logging.log4j.core.config.Configurator;

public class SimpleTest {
    @Test
    @Before
    public void setup(){
        Configurator.setLevel(LogManager.getLogger(FaultInjectRuntimeModule.class).getName(), Level.DEBUG);
        Configurator.setLevel(LogManager.getLogger(Event.class).getName(), Level.DEBUG);

    }
    //@Ignore("Not needed now")
    public void test() throws Exception {

        final File faultInjectSpec = Paths.get("target", "simpletest", "FaultInject.mabl").toFile();
        faultInjectSpec.getParentFile().mkdirs();
        try (final FileWriter writer = new FileWriter(faultInjectSpec)) {
            IOUtils.copy(FaultInjectRuntimeModule.class.getResourceAsStream("FaultInject.mabl"), writer, StandardCharsets.UTF_8);
        }

        final File spec = Paths.get("target", "simpletest", "SmallFaultInjectTest.mabl").toFile();
        try (final FileWriter writer = new FileWriter(spec)) {
            IOUtils.copy(this.getClass().getResourceAsStream("/SmallFaultInjectTest.mabl"), writer, StandardCharsets.UTF_8);
        }

        //TODO: the FMI2 copying and addition to the parse path can be skipped once mable is updated - next release
        final File fmi2 = Paths.get("target", "simpletest", "FMI2.mabl").toFile();
        try (final FileWriter writer = new FileWriter(fmi2)) {
            IOUtils.copy(TypeChecker.class.getResourceAsStream("FMI2.mabl"), writer, StandardCharsets.UTF_8);
        }

        //we just want to call main but that doesnt work with surfire as main calls .exit which is not allowed
        org.intocps.maestro.Main.argumentHandler(
                new String[]{"--verbose", "--interpret", fmi2.getAbsolutePath(), faultInjectSpec.getAbsolutePath(), spec.getAbsolutePath()});

    }

    @Test
    //@Ignore("Not needed now")
    public void testWithConfig() throws Exception {
        String initializePath = SimpleTest.class.getClassLoader().getResource("config_example/initialize.json").getPath();
        String simulateJson = SimpleTest.class.getClassLoader().getResource("config_example/simulate.json").getPath();
        String dumpPath = "target/test-classes/config_example/dump";
        final File faultInjectSpec = Paths.get("target", "simpletest", "FaultInject.mabl").toFile();
        faultInjectSpec.getParentFile().mkdirs();
        try (final FileWriter writer = new FileWriter(faultInjectSpec)) {
            IOUtils.copy(FaultInjectRuntimeModule.class.getResourceAsStream("FaultInject.mabl"), writer, StandardCharsets.UTF_8);
        }
        org.intocps.maestro.Main.argumentHandler(new String[]{"-i","-sg1",initializePath, simulateJson,"-d",dumpPath,faultInjectSpec.getPath()} );
    }
}

