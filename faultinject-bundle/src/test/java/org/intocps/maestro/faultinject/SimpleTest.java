package org.intocps.maestro.faultinject;

import org.apache.commons.io.IOUtils;

import org.intocps.maestro.typechecker.TypeChecker;
import org.junit.Test;

import java.io.File;
import org.junit.Before;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;



public class SimpleTest {
    @Test
    //@Ignore("Not needed now")
    public void test() throws Exception {
        String dumpPath = "target/simpletest/test/dump";
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
                new String[]{"--verbose", "interpret", "-output",dumpPath, fmi2.getAbsolutePath(), faultInjectSpec.getAbsolutePath(), spec.getAbsolutePath()});

    }

    @Test
    //@Ignore("Not needed now")
    public void testWithConfig() throws Exception {
        Path resourcesFolder = Path.of("src", "test", "resources");
        Path output = Paths.get("target",this.getClass().getSimpleName(),"testWithConfig2");
        output.toFile().mkdirs();

        Path initialize = output.resolve("initialize.json");
        Files.copy(resourcesFolder.resolve("config_example2").resolve("initialize.json"), initialize, StandardCopyOption.REPLACE_EXISTING);
        Path simulate = output.resolve("simulate.json");
        Files.copy(resourcesFolder.resolve("config_example2").resolve("simulate.json"), simulate, StandardCopyOption.REPLACE_EXISTING);
        Path events = output.resolve("faultInjectSpecificationWaterTank.xml");
        Files.copy(resourcesFolder.resolve("config_example2").resolve("faultInjectSpecificationWaterTank.xml"), events, StandardCopyOption.REPLACE_EXISTING);
        Path faultInjectSpec = output.resolve("FaultInject.mabl");
        Files.copy(FaultInjectRuntimeModule.class.getResourceAsStream("FaultInject.mabl"), faultInjectSpec, StandardCopyOption.REPLACE_EXISTING);


        String initializeJson = IOUtils.toString(resourcesFolder.resolve("config_example2").resolve("initialize.json").toUri(), StandardCharsets.UTF_8);
        initializeJson =initializeJson.replace("faultInjectSpecificationWaterTank.xml", events.toAbsolutePath().toString());
       IOUtils.write(initializeJson,new FileOutputStream(initialize.toFile()),StandardCharsets.UTF_8);


        org.intocps.maestro.Main.argumentHandler("import","sg1", initialize.toString(), simulate.toString(),"-output",output.toString(), faultInjectSpec.toString(),"--interpret");


    }
}

