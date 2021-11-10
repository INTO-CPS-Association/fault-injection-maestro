
package org.intocps.maestro.faultinject;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.intocps.maestro.typechecker.TypeChecker;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

public class maestroTest {
    @Test
    //test for maestro compatibility
    //@Ignore("Not needed now")
    public void basicCompatibilityTest() throws Exception {

        final File faultInjectSpec = Paths.get("target", "watertanksimpletest", "FaultInject.mabl").toFile();
        faultInjectSpec.getParentFile().mkdirs();
        try (final FileWriter writer = new FileWriter(faultInjectSpec)) {
            IOUtils.copy(FaultInjectRuntimeModule.class.getResourceAsStream("FaultInject.mabl"), writer, StandardCharsets.UTF_8);
        }

        final File spec = Paths.get("target", "watertanksimpletest", "watertank_simple_test.mabl").toFile();
        try (final FileWriter writer = new FileWriter(spec)) {
            IOUtils.copy(this.getClass().getResourceAsStream("/watertank_simple_test.mabl"), writer, StandardCharsets.UTF_8);
        }

        //TODO: the FMI2 copying and addition to the parse path can be skipped once mable is updated - next release
        final File fmi2 = Paths.get("target", "watertanksimpletest", "FMI2.mabl").toFile();
        try (final FileWriter writer = new FileWriter(fmi2)) {
            IOUtils.copy(TypeChecker.class.getResourceAsStream("FMI2.mabl"), writer, StandardCharsets.UTF_8);
        }

        //we just want to call main but that doesnt work with surfire as main calls .exit which is not allowed
        org.intocps.maestro.Main.argumentHandler(
                new String[]{"interpret", "--verbose", fmi2.getAbsolutePath(), faultInjectSpec.getAbsolutePath(), spec.getAbsolutePath()});

    }

    @Test
    //@Ignore("Not needed now")
    public void testWithConfig() throws Exception {
        String initializePath = maestroTest.class.getClassLoader().getResource("maestro_test/initialize.json").getPath();
        String simulateJson = maestroTest.class.getClassLoader().getResource("maestro_test/simulate.json").getPath();
        String dumpPath = "target/maestro_test/dump";
        final File faultInjectSpec = Paths.get("target", "maestro_test", "FaultInject.mabl").toFile();
        faultInjectSpec.getParentFile().mkdirs();
        try (final FileWriter writer = new FileWriter(faultInjectSpec)) {
            IOUtils.copy(FaultInjectRuntimeModule.class.getResourceAsStream("FaultInject.mabl"), writer, StandardCharsets.UTF_8);
        }
//        org.intocps.maestro.Main.argumentHandler(new String[]{"import sg1 --interpret", "--verbose",initializePath, simulateJson,"-output="+dumpPath,faultInjectSpec.getPath()} );
        org.intocps.maestro.Main.argumentHandler(new String[]{"import","sg1",initializePath, simulateJson,"-output",dumpPath,faultInjectSpec.getPath(),"--interpret"});
        
        //csv file containing data
        BufferedReader br = new BufferedReader(new FileReader(new File(dumpPath,"outputs.csv")));
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
        br.close();
        br2.close();
        
    }
    
}