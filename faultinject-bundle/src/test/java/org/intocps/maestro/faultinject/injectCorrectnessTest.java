package org.intocps.maestro.faultinject;

import org.apache.commons.io.IOUtils;
import org.intocps.maestro.typechecker.TypeChecker;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

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

        //csv file containing data
        BufferedReader br = new BufferedReader(new FileReader("outputs.csv"));
        String line;

        BufferedReader br2 = new BufferedReader(new FileReader("output_ground_truth.csv"));
        String line2;
        line = br.readLine(); // get rid of first header line
        while ((line = br.readLine()) != null) {
            // use comma as separator
            String[] cols = line.split(",");
            if((line2 = br2.readLine()) != null){

                String[] cols2 = line2.split(",");

                assertEquals(cols2[1], cols[1]);
                assertEquals(cols2[3], cols[3]);
                assertEquals(cols2[4], cols[4]);
                //before comparing the reals, turn 0 to 0.0
                if(cols2[2].compareTo("0")==0){
                    cols2[2] = "0.0";
                }
                Double val = Double.parseDouble(cols[2]);
                val = Math.round(val * 10.0) / 10.0;
                assertEquals(cols2[2], Double.toString(val));
            }
        }
    }
}
