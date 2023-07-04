
package org.intocps.maestro.faultinject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
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
        String dumpPath = "target/maestro_test/basic/dump";
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
                new String[]{"interpret", "--verbose", fmi2.getAbsolutePath(), faultInjectSpec.getAbsolutePath(), spec.getAbsolutePath(),"-output",dumpPath});

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
        BufferedReader bufferReader = new BufferedReader(new FileReader(new File(dumpPath,"outputs.csv")));
        String line;
        line = bufferReader.readLine(); // get rid of first header line
        //get index of column that contains result
        int id = ArrayUtils.indexOf(line.split(","), "{alltypes}.alltypesA.doubleOutput");
        int ii = ArrayUtils.indexOf(line.split(","), "{alltypes}.alltypesA.integerOutput");
        int ib = ArrayUtils.indexOf(line.split(","), "{alltypes}.alltypesA.boolOutput");
        int is = ArrayUtils.indexOf(line.split(","), "{alltypes}.alltypesA.stringOutput");


        BufferedReader bufferReaderGroundTruth = new BufferedReader(new FileReader("output_ground_truth.csv"));
        String lineGroundTruth;

        while ((line = bufferReader.readLine()) != null) {
            // use comma as separator
            String[] cols = line.split(",");
            if((lineGroundTruth = bufferReaderGroundTruth.readLine()) != null){

                String[] colsGroundTruth = lineGroundTruth.split(",");

                assertEquals(colsGroundTruth[1], cols[ib]);
                assertEquals(colsGroundTruth[3], cols[ii]);
                assertEquals(colsGroundTruth[4], cols[is]);
                //before comparing the reals, turn 0 to 0.0
                if(colsGroundTruth[2].compareTo("0")==0){
                    colsGroundTruth[2] = "0.0";
                }
                Double val = Double.parseDouble(cols[id]);
                val = Math.round(val * 10.0) / 10.0;
                assertEquals(colsGroundTruth[2], Double.toString(val));
            }
        }
        bufferReader.close();
        bufferReaderGroundTruth.close();
        
    }
    
}