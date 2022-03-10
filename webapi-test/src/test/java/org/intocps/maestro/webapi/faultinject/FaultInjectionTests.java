package org.intocps.maestro.webapi.faultinject;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.intocps.maestro.webapi.maestro2.dto.InitializeStatusModel;
import org.intocps.maestro.webapi.maestro2.dto.StatusModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("main")
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@SpringBootTest(classes = org.intocps.maestro.webapi.Application.class, value={"loader.path=target/test-classes/faultinject-1.0.0.jar"})
public class FaultInjectionTests {
    @Autowired
    private WebApplicationContext webApplicationContext;
    private MockMvc mockMvc;


    @BeforeEach
    public void before() {

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }


    @Test
    public void test() throws Exception {
        final String res = mockMvc.perform(get("/version")).andReturn().getResponse().getContentAsString();
        System.out.println(res);
        File initializePath = new File(FaultInjectionTests.class.getClassLoader().getResource("initialize.json").getPath());
        File simulatePath = new File(FaultInjectionTests.class.getClassLoader().getResource("simulate.json").getPath());

        ObjectMapper om = new ObjectMapper();

        StatusModel statusModel = om.readValue(
                mockMvc.perform(get("/createSession")).andExpect(status().is(HttpStatus.OK.value())).andReturn().getResponse().getContentAsString(),
                StatusModel.class);

        InitializeStatusModel initializeResponse = om.readValue(mockMvc.perform(
                        post("/initialize/" + statusModel.sessionId).content(FileUtils.readFileToByteArray(initializePath))
                                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().is(HttpStatus.OK.value())).andReturn().getResponse()
                .getContentAsString(), InitializeStatusModel.class);

        byte[] simulateMessageContent = FileUtils.readFileToByteArray(simulatePath);

        InitializeStatusModel simulateResponse = om.readValue(
                mockMvc.perform(post("/simulate/" + statusModel.sessionId).content(simulateMessageContent).contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().is(HttpStatus.OK.value())).andReturn().getResponse().getContentAsString(), InitializeStatusModel.class);

        byte[] zippedResult =
                mockMvc.perform(get("/result/" + statusModel.sessionId + "/zip")).andExpect(status().is(HttpStatus.OK.value())).andReturn()
                        .getResponse().getContentAsByteArray();

        ZipInputStream istream = new ZipInputStream(new ByteArrayInputStream(zippedResult));
        List<ZipEntry> entries = new ArrayList<>();
        ZipEntry entry = istream.getNextEntry();
        String mablSpec = null;
        String outputs = null;
        String spec_runtime_json = null;
        String alltypesB_1_log = null;
        String alltypesA_log = null;
        while (entry != null) {
            entries.add(entry);
            if (entry.getName().equals("spec.mabl")) {
                mablSpec = IOUtils.toString(istream, StandardCharsets.UTF_8);
                FileWriter myWriter = new FileWriter("target/test-classes/spec.mabl");
                myWriter.write(mablSpec);
                myWriter.close();
            }
            if (entry.getName().equals("outputs.csv")) {
                outputs = IOUtils.toString(istream, StandardCharsets.UTF_8);
                FileWriter myWriter = new FileWriter("target/test-classes/outputs.csv");
                myWriter.write(outputs);
                myWriter.close();
            }
            if (entry.getName().equals("spec.runtime.json")) {
                spec_runtime_json = IOUtils.toString(istream, StandardCharsets.UTF_8);
                FileWriter myWriter = new FileWriter("target/test-classes/spec.runtime.json");
                myWriter.write(spec_runtime_json);
                myWriter.close();
            }
            if (entry.getName().equals("alltypesA.log")) {
                alltypesA_log = IOUtils.toString(istream, StandardCharsets.UTF_8);
                FileWriter myWriter = new FileWriter("target/test-classes/alltypesA.log");
                myWriter.write(alltypesA_log);
                myWriter.close();
            }
            if (entry.getName().equals("alltypesB_1.log")) {
                alltypesB_1_log = IOUtils.toString(istream, StandardCharsets.UTF_8);
                FileWriter myWriter = new FileWriter("target/test-classes/alltypesB_1.log");
                myWriter.write(alltypesB_1_log);
                myWriter.close();
            }
            System.out.println(entry.getName());
            entry = istream.getNextEntry();

        }
        istream.closeEntry();
        istream.close();

        mockMvc.perform(get("/destroy/" + statusModel.sessionId)).andExpect(status().is(HttpStatus.OK.value())).andReturn().getResponse()
                .getContentAsString();


        //Check that the output of the csv is correct
        //csv file containing data
        BufferedReader br = new BufferedReader(new FileReader(new File("target/test-classes/outputs.csv")));
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
