package org.intocps.maestro.faultinject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.print.event.PrintEvent;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.apache.commons.lang.ArrayUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Event {
    /**
     * Assumes events are ordered. For now no sanity checks on this, but probably
     * should be added.
     */
    static final Logger logger = LoggerFactory.getLogger(Event.class);
    public static boolean verbose = false;
    public int id;
    public double timePoint;
    public double[] doubleValues;
    public long[] doubleValuesRefs;
    public int[] intValues;
    public long[] intValuesRefs;
    public boolean[] boolValues;
    public long[] boolValuesRefs;
    public String[] stringValues;
    public long[] stringValuesRefs;
    public boolean injected = false;

    public Event(int id, double timePoint, double[] doubleValues, long[] doubleValuesRefs, int[] intValues,
            long[] intValuesRefs, boolean[] boolValues, long[] boolValuesRefs, String[] stringValues,
            long[] stringValuesRefs) {
        this.id = id;
        this.timePoint = timePoint;
        this.doubleValues = doubleValues;
        this.doubleValuesRefs = doubleValuesRefs;
        this.intValues = intValues;
        this.intValuesRefs = intValuesRefs;
        this.boolValues = boolValues;
        this.boolValuesRefs = boolValuesRefs;
        this.stringValues = stringValues;
        this.stringValuesRefs = stringValuesRefs;
    }

    public static void setVerbose(boolean verbose){
        Event.verbose = verbose;
    }

    private static NodeList parseXMLDom(String specificationFileName)
            throws SAXException, IOException, ParserConfigurationException {
        //Get Document Builder
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
 
        //Build Document
        Document document = builder.parse(new File(specificationFileName));

        //Normalize the XML Structure; It's just too important !!
        document.getDocumentElement().normalize();

        //Return nodelist with the event tagged elements
        return document.getElementsByTagName("event");
    }

    public static Event[] getEvents(String specificationFileName, boolean verbose)
            throws SAXException, IOException, ParserConfigurationException, 
                NullPointerException, NumberFormatException {

        //Parse document
        NodeList eventsList = parseXMLDom(specificationFileName);

        //find out how many events
        int nrEvents = eventsList.getLength();
        logger.warn(String.format("Nr of events %d", nrEvents));

        Event.verbose = verbose;

        //create events one by one manually
        Event[] events = new Event[nrEvents];
        //Loop through events
        for(int i = 0; i < nrEvents; i++){
            Node node = eventsList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE)
            {
                Element eElement = (Element) node;

                int id = Integer.parseInt(eElement.getAttribute("id"));
                double time = Double.parseDouble(eElement.getAttribute("timeStep"));
                List<Double> dValues = new ArrayList<Double>();
                List<Long> dValuesRefs = new ArrayList<Long>();

                List<Boolean> bValues = new ArrayList<Boolean>();
                List<Long> bValuesRefs = new ArrayList<Long>();

                List<Integer> iValues = new ArrayList<Integer>();
                List<Long> iValuesRefs = new ArrayList<Long>();

                List<String> sValues = new ArrayList<String>();
                List<Long> sValuesRefs = new ArrayList<Long>();

                NodeList variables = eElement.getElementsByTagName("variable");
                //Loop through variables within one event
                for(int j = 0; j < variables.getLength(); j++){
                    Node var = variables.item(j);
                    //prepare the values which are passed to the event constructor.
                    if(var.getNodeType() == Node.ELEMENT_NODE){
                        Element v = (Element) var;
                        if(v.getAttribute("type").equals("real")){
                            dValues.add(Double.parseDouble(v.getAttribute("newVal")));
                            dValuesRefs.add(Long.parseLong(v.getAttribute("valRef")));
                        }
                        else if(v.getAttribute("type").equals("bool")){
                            bValues.add(Boolean.parseBoolean(v.getAttribute("newVal")));
                            bValuesRefs.add(Long.parseLong(v.getAttribute("valRef")));
                        }
                        else if(v.getAttribute("type").equals("int")){
                            iValues.add(Integer.parseInt(v.getAttribute("newVal")));
                            iValuesRefs.add(Long.parseLong(v.getAttribute("valRef")));
                        }
                        else if(v.getAttribute("type").equals("string")){
                            sValues.add(v.getAttribute("newVal"));
                            sValuesRefs.add(Long.parseLong(v.getAttribute("valRef")));
                        }
                        else{
                            throw new WrapperException(String.format("Unrecognized type: %s, when parsing faultInjectSpecification xml", v.getAttribute("type")));
                        }
                    }
                }

                //Convert List<Boolean> to boolean[]
                boolean[] bbValues = new boolean[bValues.size()];
                for(int k = 0; k< bValues.size(); k++){
                    bbValues[k] = bValues.get(k);
                }
                String[] ssValues = new String[sValues.size()];
                //Convert List<String> to string[]
                for(int k = 0; k< sValues.size(); k++){
                    ssValues[k] = sValues.get(k);
                }
                
                //Call event i constructor
                events[i] = new Event(id, time, 
                                        dValues.stream().mapToDouble(Double::doubleValue).toArray(), 
                                        dValuesRefs.stream().mapToLong(Long::longValue).toArray(), 
                                        iValues.stream().mapToInt(Integer::intValue).toArray(), 
                                        iValuesRefs.stream().mapToLong(Long::longValue).toArray(), 
                                        bbValues, 
                                        bValuesRefs.stream().mapToLong(Long::longValue).toArray(), 
                                        ssValues, 
                                        sValuesRefs.stream().mapToLong(Long::longValue).toArray()
                                    );
            }
        }

        return events;
    }

    public static Event[] cutArrayOfEvents(Event[] events, double currentStep){
        if(events.length !=0 && Math.abs(currentStep - events[0].timePoint) <= 0.0000001){
            events = (Event[]) ArrayUtils.remove(events, 0);
            if(verbose){
                //printEvent(events);
                if(events.length == 0){
                    logger.warn("No more events");
                }
            }
        }
        
        return events;
    }

    //Print all events
    public static void printEvent(Event[] events){
        for(Event e: events){
            String printText = "Event with id: " + e.id + ", at time: " + e.timePoint
                                + " with doubles: " + Arrays.toString(e.doubleValues) + " with vrefs: " + Arrays.toString(e.doubleValuesRefs)
                                + "; with ints: " + Arrays.toString(e.intValues) + " with vrefs: " + Arrays.toString(e.intValuesRefs)
                                + "; with bools: " + Arrays.toString(e.boolValues) + " with vrefs: " + Arrays.toString(e.boolValuesRefs)
                                + "; with strings: " + Arrays.toString(e.stringValues) + " with vrefs: " + Arrays.toString(e.stringValuesRefs);
            logger.warn(printText);
        }
    }

    //Print event at given index
    public static void PrintEvent(Event[] events, int eventIndex){
        String printText = "Event with id: " + events[eventIndex].id + ", at time: " + events[eventIndex].timePoint
                            + " with doubles: " + Arrays.toString(events[eventIndex].doubleValues) + " with vrefs: " + Arrays.toString(events[eventIndex].doubleValuesRefs)
                            + "; with ints: " + Arrays.toString(events[eventIndex].intValues) + " with vrefs: " + Arrays.toString(events[eventIndex].intValuesRefs)
                            + "; with bools: " + Arrays.toString(events[eventIndex].boolValues) + " with vrefs: " + Arrays.toString(events[eventIndex].boolValuesRefs)
                            + "; with strings: " + Arrays.toString(events[eventIndex].stringValues) + " with vrefs: " + Arrays.toString(events[eventIndex].stringValuesRefs);
        logger.warn(printText);
    }

}
