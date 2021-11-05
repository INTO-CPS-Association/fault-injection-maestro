package org.intocps.maestro.faultinject;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.log4j.LogManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

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

        String xmlPath = eventsTest.class.getClassLoader().getResource("check_when.xml").getPath();

        Event[] simuEvents = {};
        Event[] simuEventswithDuration = {};

        try {
            boolean verbose = true;
            
            String a = "(t>=0.2) & (t<0.4)";
            String b = "(t>=0.7) | (t<0.6)";
            String c = "((t>=0.2) & (t<0.4)) | (t>=0.7) | (t<0.6)";

            Event.isEventRemovable(a);
            Event.isEventRemovable(b);
            assertTrue("Expected to be true", Event.isEventRemovable(a));
            assertTrue("Expected to be false",!Event.isEventRemovable(b));
            assertTrue("Expected to be false",!Event.isEventRemovable(c));
            //assertTrue("Expected to be true",Event.isEventRemovable("(t==0.2) | (t<0.4)"));
            assertTrue("Expected to be false",!Event.isEventRemovable("(t==0.2) | (t>0.4)"));

        } catch (NumberFormatException | NullPointerException e) {
            logger.error("Something went terribly wrong when creating the events");
            e.printStackTrace();
        }
    }
}
