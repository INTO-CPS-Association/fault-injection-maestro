package org.intocps.maestro.faultinject;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.lang.Math.*;

import javax.xml.parsers.ParserConfigurationException;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.apache.commons.compress.utils.Lists;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

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

        String xmlPath = eventsTest.class.getClassLoader().getResource("testClean.xml").getPath();

        Event[] simuEventswithDuration = {};

        try {
            boolean verbose = true;
            simuEventswithDuration = Event.getEventswithDuration(xmlPath, verbose);
            Event.printEvents(simuEventswithDuration);
            double simtime = 0.0;
            double endtime = 3.0;
            double step = 0.1;
            ArrayList<Event> eventsLeft = new ArrayList<Event>(Arrays.asList(simuEventswithDuration));;
            while(simtime < endtime){

                logger.error(String.format("Simtime %f", simtime));
                eventsLeft = new ArrayList<Event>(Arrays.asList(Event.cutArrayOfEvents(eventsLeft.toArray(Event[]::new), simtime)));

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
}
