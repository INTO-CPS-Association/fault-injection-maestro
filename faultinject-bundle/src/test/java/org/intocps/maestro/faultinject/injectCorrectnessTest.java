package org.intocps.maestro.faultinject;

import org.apache.commons.io.IOUtils;
import org.intocps.maestro.typechecker.TypeChecker;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import java.io.*;  
import java.util.Scanner;  

public class injectCorrectnessTest {
    @Test
    //@Ignore("Not needed now")
    //tests with the alltypes.fmu, with inputs and outputs of each type.
    public void testOutputs() throws Exception {

        final File faultInjectSpec = Paths.get("target", "funcevaltest", "FaultInject.mabl").toFile();
        faultInjectSpec.getParentFile().mkdirs();
        try (final FileWriter writer = new FileWriter(faultInjectSpec)) {
            IOUtils.copy(FaultInjectRuntimeModule.class.getResourceAsStream("FaultInject.mabl"), writer, StandardCharsets.UTF_8);
        }

        final File spec = Paths.get("target", "funcevaltest", "funceval_output_correctness_test.mabl").toFile();
        try (final FileWriter writer = new FileWriter(spec)) {
            IOUtils.copy(this.getClass().getResourceAsStream("/funceval_output_correctness_test.mabl"), writer, StandardCharsets.UTF_8);
        }

        //TODO: the FMI2 copying and addition to the parse path can be skipped once mable is updated - next release
        final File fmi2 = Paths.get("target", "funcevaltest", "FMI2.mabl").toFile();
        try (final FileWriter writer = new FileWriter(fmi2)) {
            IOUtils.copy(TypeChecker.class.getResourceAsStream("FMI2.mabl"), writer, StandardCharsets.UTF_8);
        }

        //we just want to call main but that doesnt work with surfire as main calls .exit which is not allowed
        org.intocps.maestro.Main.argumentHandler(
                new String[]{"interpret", "--verbose", fmi2.getAbsolutePath(), faultInjectSpec.getAbsolutePath(), spec.getAbsolutePath()});

        //parsing a CSV file into Scanner class constructor  
        // time boolout realout intout stringout boolin realin intin stringin
        Scanner sc = new Scanner(new File("outputs.csv"));  
        sc.useDelimiter(",");   //sets the delimiter pattern  

        while (sc.hasNext())  //returns a boolean value  
        {  
            System.out.print(sc.next());  //find and returns the next complete token from this scanner  
        }   
        sc.close();  //closes the scanner  
    }
}
