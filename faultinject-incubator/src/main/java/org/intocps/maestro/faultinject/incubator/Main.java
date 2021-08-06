package org.intocps.maestro.faultinject.incubator;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Main {

    static final String faultInjectMablFileName = "FaultInject.mabl";
    static final String faultInjectMablResourcePath = "org/intocps/maestro/faultinject/" + faultInjectMablFileName;
    /**
     * Accepts 2 arguments: multimodel and simulation json
     * @param args
     */
    public static void main(String[] args) throws IOException {
        argumentHandler(args);
    }

    public static boolean argumentHandler(String[] args) throws IOException {
        if(args.length == 3) {
            String initializePath = args[0];
            String simulationJsonPath = args[1];
            String dumpPath = args[2];

            //Find the FaultInject.mabl
            var faultInjectMabl = Main.class.getClassLoader().getResourceAsStream(faultInjectMablResourcePath);
            // Copy it to the temporary folder on the laptop
            var tempDir = Files.createTempDirectory("faultInjectIncubatorExample");
            File faultInjectMablTargetFile = new File(tempDir.toFile(), faultInjectMablFileName);
            FileUtils.copyInputStreamToFile(faultInjectMabl, faultInjectMablTargetFile);
            System.out.println("Copied " + faultInjectMablFileName + " to: " + faultInjectMablTargetFile);
            org.intocps.maestro.Main.argumentHandler(new String[]{"import","-output",dumpPath, "-i","Sg1",initializePath, simulationJsonPath,faultInjectMablTargetFile.getPath()} );

        }
        else
        {
            System.out.println("Expected 3 arguments, found " + args.length);
            return false;
        }
        return true;
    }
}
