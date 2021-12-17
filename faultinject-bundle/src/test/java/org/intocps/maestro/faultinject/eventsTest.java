package org.intocps.maestro.faultinject;
import static java.util.Arrays.asList;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import com.fasterxml.jackson.databind.DatabindContext;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.NodeList;


public class eventsTest {
    static final Logger logger = LoggerFactory.getLogger(eventsTest.class);
    @Test
    //@Ignore("Not needed now")
    public void testReadXml() throws Exception {

        String xmlPath = eventsTest.class.getClassLoader().getResource("Catalog.xml").getPath();

        Event[] simuEvents = {};
        Event[] simuEventswithDuration = {};

        try {
            boolean verbose = true;
            simuEventswithDuration = Event.getEventswithDuration(xmlPath, verbose);
            Event.printEvents(simuEventswithDuration);
        } catch (NumberFormatException | NullPointerException | SAXException | IOException
                | ParserConfigurationException e) {
            logger.error("Something went terribly wrong when creating the events");
            e.printStackTrace();
        }


    }

    @Test
    //@Ignore("Not needed now")
    public void testfunceval() throws Exception {

        String xmlPath = eventsTest.class.getClassLoader().getResource("funceval_when.xml").getPath();

        Event[] simuEvents = {};
        Event[] simuEventswithDuration = {};

        try {
            boolean verbose = true;
            
            simuEventswithDuration = Event.getEventswithDuration(xmlPath, verbose);
            Event.printEvents(simuEventswithDuration);
        } catch (NumberFormatException | NullPointerException | SAXException | IOException
                | ParserConfigurationException e) {
            logger.error("Something went terribly wrong when creating the events");
            e.printStackTrace();
        }


    }

    @Test 
    public void expressionEvaluation() throws Exception {



        try {
            String a = "(t>=0.2) & (t<0.4)";
            String b = "(t>=1.7) | (t<1.6)";
            String c = "((t>=0.2) & (t<0.4)) | (t>=0.7) | (t<0.6)";
            String d = "(t=0.2) | (t<0.4)";
            String e = "(t=0.2) | (t>0.4)";

            CustomOperators operators = new CustomOperators();

            List<String> whenExpressionVars =  new ArrayList<>();
            whenExpressionVars.add("t");
            Expression when = new ExpressionBuilder(a).operator(operators.not, operators.or, operators.and, operators.gt,
                            operators.gteq, operators.lt, operators.lteq, operators.eq).variables(whenExpressionVars.stream().toArray(String[]::new)).build();
            assertTrue("Expected to be true", !(Event.isEventRemovable(a, when)<0));

            when = new ExpressionBuilder(b).operator(operators.not, operators.or, operators.and, operators.gt,
                    operators.gteq, operators.lt, operators.lteq, operators.eq).variables(whenExpressionVars.stream().toArray(String[]::new)).build();
            assertTrue("Expected to be false",(Event.isEventRemovable(b, when)<0));

            when = new ExpressionBuilder(c).operator(operators.not, operators.or, operators.and, operators.gt,
                    operators.gteq, operators.lt, operators.lteq, operators.eq).variables(whenExpressionVars.stream().toArray(String[]::new)).build();
            assertTrue("Expected to be false",(Event.isEventRemovable(c, when)<0));

            when = new ExpressionBuilder(d).operator(operators.not, operators.or, operators.and, operators.gt,
                    operators.gteq, operators.lt, operators.lteq, operators.eq).variables(whenExpressionVars.stream().toArray(String[]::new)).build();
            assertTrue("Expected to be true",!(Event.isEventRemovable(d, when)<0));

            when = new ExpressionBuilder(e).operator(operators.not, operators.or, operators.and, operators.gt,
                    operators.gteq, operators.lt, operators.lteq, operators.eq).variables(whenExpressionVars.stream().toArray(String[]::new)).build();
            assertTrue("Expected to be false",(Event.isEventRemovable(e, when)<0));

        } catch (NumberFormatException | NullPointerException e) {
            logger.error("Something went terribly wrong when creating the events");
            e.printStackTrace();
        }
    }

    @Test
    public void testCleanArray() throws Exception{

        String xmlPath = eventsTest.class.getClassLoader().getResource("test_clean/testClean.xml").getPath();

        Event[] simuEventswithDuration = {};

        try {
            boolean verbose = true;
            simuEventswithDuration = Event.getEventswithDuration(xmlPath, verbose);
            Event.printEvents(simuEventswithDuration);
            double simtime = 0.0;
            double endtime = 3.0;
            double step = 0.1;
            ArrayList<Event> eventsLeft = new ArrayList<Event>(asList(simuEventswithDuration));;
            while(simtime < endtime){

                logger.error(String.format("Simtime %f", simtime));
                eventsLeft = new ArrayList<Event>(asList(Event.cutArrayOfEvents(eventsLeft.toArray(Event[]::new), simtime)));

                if (Math.abs(simtime-1.4) < 0.0000000001){
                    assertTrue(eventsLeft.size() == 4);
                }
                else if ((Math.abs(simtime-1.8) < 0.0000000001)){
                    assertTrue(eventsLeft.size() == 3);

                }

                simtime += step;

            }
        } catch (NumberFormatException | NullPointerException | SAXException | IOException
                | ParserConfigurationException e) {
            logger.error("Something went terribly wrong when creating the events");
            e.printStackTrace();
        }

    }

    @Test
    @Ignore("Not working on actions")
    public void testCleanArrayIntegration() throws Exception{
        String initializePath = eventsTest.class.getClassLoader().getResource("test_clean/initialize.json").getPath();
        String simulateJson = eventsTest.class.getClassLoader().getResource("test_clean/simulate.json").getPath();
        String dumpPath = "target/test_clean/dump";
        final File faultInjectSpec = Paths.get("target", "maestro_test", "FaultInject.mabl").toFile();
        faultInjectSpec.getParentFile().mkdirs();
        try (final FileWriter writer = new FileWriter(faultInjectSpec)) {
            IOUtils.copy(FaultInjectRuntimeModule.class.getResourceAsStream("FaultInject.mabl"), writer, StandardCharsets.UTF_8);
        }
//        org.intocps.maestro.Main.argumentHandler(new String[]{"import sg1 --interpret", "--verbose",initializePath, simulateJson,"-output="+dumpPath,faultInjectSpec.getPath()} );
        org.intocps.maestro.Main.argumentHandler(new String[]{"import","sg1",initializePath, simulateJson,"-output",dumpPath,faultInjectSpec.getPath(),"--interpret"});

        int[] remaining_events_expected = {2, 3, 5};
        int[] remaining_events_outputted = new int[3];

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        //Build Document
        Document document = builder.parse(new File("events_xml_log.xml"));

        //Normalize the XML Structure; It's just too important !!
        document.getDocumentElement().normalize();

        NodeList nodes = document.getElementsByTagName("event");

        assertEquals(remaining_events_expected.length, nodes.getLength());

        for(int i = 0; i < nodes.getLength(); i++){
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE)
            {
                Element eElement = (Element) node;
                remaining_events_outputted[i] = Integer.parseInt(eElement.getAttribute("id"));
                logger.error(String.format("Id: %d", remaining_events_outputted[i]));
            }
        }

        assertArrayEquals(remaining_events_expected, remaining_events_outputted);
    }
}
