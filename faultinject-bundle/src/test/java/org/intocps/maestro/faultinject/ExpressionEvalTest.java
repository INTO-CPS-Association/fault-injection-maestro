package org.intocps.maestro.faultinject;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.intocps.maestro.typechecker.TypeChecker;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public class ExpressionEvalTest {
    @Test
    //@Ignore("Not needed now")
    //tests with the alltypes.fmu, with inputs and outputs of each type.
    public void testFuncEval() throws Exception {
        String dumpPath = "target/funcevaltest/dump";
        final File faultInjectSpec = Paths.get("target", "funcevaltest", "FaultInject.mabl").toFile();
        faultInjectSpec.getParentFile().mkdirs();
        try (final FileWriter writer = new FileWriter(faultInjectSpec)) {
            IOUtils.copy(FaultInjectRuntimeModule.class.getResourceAsStream("FaultInject.mabl"), writer, StandardCharsets.UTF_8);
        }

        final File spec = Paths.get("target", "funcevaltest", "funceval_test.mabl").toFile();
        try (final FileWriter writer = new FileWriter(spec)) {
            IOUtils.copy(this.getClass().getResourceAsStream("/funceval_test.mabl"), writer, StandardCharsets.UTF_8);
        }

        //TODO: the FMI2 copying and addition to the parse path can be skipped once mable is updated - next release
        final File fmi2 = Paths.get("target", "funcevaltest", "FMI2.mabl").toFile();
        try (final FileWriter writer = new FileWriter(fmi2)) {
            IOUtils.copy(TypeChecker.class.getResourceAsStream("FMI2.mabl"), writer, StandardCharsets.UTF_8);
        }

        //we just want to call main but that doesnt work with surfire as main calls .exit which is not allowed
        org.intocps.maestro.Main.argumentHandler(
                new String[]{"interpret", "--verbose","-output",dumpPath, fmi2.getAbsolutePath(), faultInjectSpec.getAbsolutePath(), spec.getAbsolutePath()});

    }
}
