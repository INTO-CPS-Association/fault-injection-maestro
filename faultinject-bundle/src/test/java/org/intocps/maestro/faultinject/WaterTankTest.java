package org.intocps.maestro.faultinject;

import org.apache.commons.io.IOUtils;
import org.intocps.maestro.typechecker.TypeChecker;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public class WaterTankTest {
    @Test
    @Ignore("Not needed now")
    public void test() throws Exception {

        final File faultInjectSpec = Paths.get("target", "watertanktest", "FaultInject.mabl").toFile();
        faultInjectSpec.getParentFile().mkdirs();
        try (final FileWriter writer = new FileWriter(faultInjectSpec)) {
            IOUtils.copy(FaultInjectRuntimeModule.class.getResourceAsStream("FaultInject.mabl"), writer, StandardCharsets.UTF_8);
        }

        final File spec = Paths.get("target", "watertanktest", "watertank-casestudy.mabl").toFile();
        try (final FileWriter writer = new FileWriter(spec)) {
            IOUtils.copy(this.getClass().getResourceAsStream("/watertank-casestudy.mabl"), writer, StandardCharsets.UTF_8);
        }

        //TODO: the FMI2 copying and addition to the parse path can be skipped once mable is updated - next release
        final File fmi2 = Paths.get("target", "watertanktest", "FMI2.mabl").toFile();
        try (final FileWriter writer = new FileWriter(fmi2)) {
            IOUtils.copy(TypeChecker.class.getResourceAsStream("FMI2.mabl"), writer, StandardCharsets.UTF_8);
        }

        //we just want to call main but that doesnt work with surfire as main calls .exit which is not allowed
        org.intocps.maestro.Main.argumentHandler(
                new String[]{"--verbose", "--interpret", fmi2.getAbsolutePath(), faultInjectSpec.getAbsolutePath(), spec.getAbsolutePath()});

    }
}

