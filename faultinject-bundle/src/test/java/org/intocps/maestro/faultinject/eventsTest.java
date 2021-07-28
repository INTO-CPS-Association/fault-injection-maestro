package org.intocps.maestro.faultinject;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Ignore;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class eventsTest {
    static final Logger logger = LoggerFactory.getLogger(FaultInjectRuntimeModule.class);
    @Test
    @Ignore("Not needed now")
    public void testReadXml() throws Exception {

        String xmlPath = SimpleTest.class.getClassLoader().getResource("Catalog.xml").getPath();

        Event[] simuEvents = {};
        Event[] simuEventswithDuration = {};

        try {
            boolean verbose = true;
            simuEvents = Event.getEvents(xmlPath, verbose);
            Event.printEvent(simuEvents);
            
            simuEventswithDuration = Event.getEventswithDuration(xmlPath, verbose);
            Event.printEvent(simuEventswithDuration);
        } catch (NumberFormatException | NullPointerException | SAXException | IOException
                | ParserConfigurationException e) {
            logger.error("Something went terribly wrong when creating the events");
            e.printStackTrace();
        }


    }

    @Test
    public void testfunceval() throws Exception {

        String xmlPath = SimpleTest.class.getClassLoader().getResource("funceval.xml").getPath();

        Event[] simuEvents = {};
        Event[] simuEventswithDuration = {};

        try {
            boolean verbose = true;
            simuEvents = Event.getEvents(xmlPath, verbose);
            Event.printEvent(simuEvents);
            
            simuEventswithDuration = Event.getEventswithDuration(xmlPath, verbose);
            Event.printEvent(simuEventswithDuration);
        } catch (NumberFormatException | NullPointerException | SAXException | IOException
                | ParserConfigurationException e) {
            logger.error("Something went terribly wrong when creating the events");
            e.printStackTrace();
        }


    }

}
