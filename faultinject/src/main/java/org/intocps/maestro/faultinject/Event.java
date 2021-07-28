package org.intocps.maestro.faultinject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;


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
    public double duration;
    public boolean durationToggle;
    public List<Expression> expressionForDoubles;
    public List<Expression> expressionForInts;
    public List<Expression> expressionForBools;

    public Event(int id, double timePoint, double[] doubleValues, long[] doubleValuesRefs, int[] intValues,
            long[] intValuesRefs, boolean[] boolValues, long[] boolValuesRefs, String[] stringValues,
            long[] stringValuesRefs, double duration, boolean durationToggle, List<Expression> expressionForDoubles,
            List<Expression> expressionForInts, List<Expression> expressionForBools) {
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
        this.duration = duration;
        this.durationToggle = durationToggle; // if set the event is applied to all timesteps, and overrides the effect of duration
        this.expressionForDoubles = expressionForDoubles;
        this.expressionForBools = expressionForBools;
        this.expressionForInts = expressionForInts;
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
        
        //find how many one time events
        int nrOneOffEvents = 0;
        for(int i = 0; i < nrEvents; i++){
            Node node = eventsList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE)
            {
                Element eElement = (Element) node;
                if(!eElement.hasAttribute("duration") && !eElement.hasAttribute("durationToggle")){
                    nrOneOffEvents++;
                }
            }
        }

        //create events one by one manually
        Event[] events = new Event[nrOneOffEvents];
        //Loop through events
        //loop all events and keep only those that are one off events
        int eIndex = 0; // use eIndex to index the events array for one time events.
        for(int i = 0; i < nrEvents; i++){
            Node node = eventsList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE)
            {
                Element eElement = (Element) node;

                if(!eElement.hasAttribute("duration") && !eElement.hasAttribute("durationToggle")){
                    int id = Integer.parseInt(eElement.getAttribute("id"));
                    double time = Double.parseDouble(eElement.getAttribute("timeStep"));
                    double duration = 1;
                    Boolean durationToggle = false;
                    List<Expression> ed = new ArrayList<Expression>();
                    List<Expression> ei = new ArrayList<Expression>();
                    List<Expression> eb = new ArrayList<Expression>();
                    
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
                                //Get the variables used by this expression
                                List<String> expressionVars = new ArrayList<>(Arrays.asList(v.getAttribute("vars").split(",")));
                                //System.out.println(expressionVars);
                                expressionVars.add("t");
                                //System.out.println(expressionVars);
                                ed.add(new ExpressionBuilder(v.getAttribute("newVal")).variables(expressionVars.stream().toArray(String[]::new)).build());
                                
                                dValuesRefs.add(Long.parseLong(v.getAttribute("valRef")));
                            }
                            else if(v.getAttribute("type").equals("bool")){
                                //bValues.add(Boolean.parseBoolean(v.getAttribute("newVal")));

                                List<String> expressionVars = new ArrayList<>(Arrays.asList(v.getAttribute("vars").split(",")));
                                expressionVars.add("t");
                                CustomOperators operators = new CustomOperators();
                                eb.add(new ExpressionBuilder(v.getAttribute("newVal")).operator(operators.not, operators.or, operators.and).variables(expressionVars.stream().toArray(String[]::new)).build());
                                bValuesRefs.add(Long.parseLong(v.getAttribute("valRef")));
                            }
                            else if(v.getAttribute("type").equals("int")){
                                //iValues.add(Integer.parseInt(v.getAttribute("newVal")));
                                //Get the variables used by this expression
                                List<String> expressionVars = new ArrayList<>(Arrays.asList(v.getAttribute("vars").split(",")));
                                //System.out.println(expressionVars);
                                expressionVars.add("t");
                                //System.out.println(expressionVars);
                                ei.add(new ExpressionBuilder(v.getAttribute("newVal")).variables(expressionVars.stream().toArray(String[]::new)).build());
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
                    events[eIndex] = new Event(id, time, 
                                            dValues.stream().mapToDouble(Double::doubleValue).toArray(), 
                                            dValuesRefs.stream().mapToLong(Long::longValue).toArray(), 
                                            iValues.stream().mapToInt(Integer::intValue).toArray(), 
                                            iValuesRefs.stream().mapToLong(Long::longValue).toArray(), 
                                            bbValues, 
                                            bValuesRefs.stream().mapToLong(Long::longValue).toArray(), 
                                            ssValues, 
                                            sValuesRefs.stream().mapToLong(Long::longValue).toArray(),
                                            duration, durationToggle, ed, ei, eb
                                        );
                    eIndex++;
                }

                
            }
        }

        return events;
    }

    public static Event[] getEventswithDuration(String specificationFileName, boolean verbose)throws SAXException, IOException, ParserConfigurationException, 
    NullPointerException, NumberFormatException {
        //Parse document
        NodeList eventsList = parseXMLDom(specificationFileName);

        //find out how many events
        int nrEvents = eventsList.getLength();
        logger.warn(String.format("Nr of events %d", nrEvents));

        Event.verbose = verbose;

        //find how many one time events
        int nrDurationEvents = 0;
        for(int i = 0; i < nrEvents; i++){
            Node node = eventsList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE)
            {
                Element eElement = (Element) node;
                if(eElement.hasAttribute("duration") || eElement.hasAttribute("durationToggle")){
                    nrDurationEvents++;
                }
            }
        }

        //create events one by one manually
        Event[] events = new Event[nrDurationEvents];
        //Loop through events
        //loop all events and keep only those that are duration events
        int eIndex = 0; // use eIndex to index the events array for duration events.
        for(int i = 0; i < nrEvents; i++){
            Node node = eventsList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE)
            {
                Element eElement = (Element) node;

                if(eElement.hasAttribute("duration") || eElement.hasAttribute("durationToggle"))
                {
                    int id = Integer.parseInt(eElement.getAttribute("id"));
                    double time = Double.parseDouble(eElement.getAttribute("timeStep"));
                    double duration = 1;
                    Boolean durationToggle = false;
                    List<Expression> ed = new ArrayList<Expression>();
                    List<Expression> ei = new ArrayList<Expression>();
                    List<Expression> eb = new ArrayList<Expression>();

                    if(eElement.hasAttribute("duration")){
                        duration = Double.parseDouble(eElement.getAttribute("duration"));
                    }
                    if(eElement.hasAttribute("durationToggle")){
                        durationToggle = Boolean.parseBoolean(eElement.getAttribute("durationToggle"));
                    }
                    
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
                                List<String> expressionVars = new ArrayList<>(Arrays.asList(v.getAttribute("vars").split(",")));
                                //System.out.println(expressionVars);
                                expressionVars.add("t");
                                ed.add(new ExpressionBuilder(v.getAttribute("newVal")).variables("t").build());
                                //dValues.add(Double.parseDouble(v.getAttribute("newVal")));
                                dValuesRefs.add(Long.parseLong(v.getAttribute("valRef")));
                            }
                            else if(v.getAttribute("type").equals("bool")){
                                //bValues.add(Boolean.parseBoolean(v.getAttribute("newVal")));
                                List<String> expressionVars = new ArrayList<>(Arrays.asList(v.getAttribute("vars").split(",")));
                                expressionVars.add("t");
                                CustomOperators operators = new CustomOperators();
                                eb.add(new ExpressionBuilder(v.getAttribute("newVal")).operator(operators.not, operators.or, operators.and).variables(expressionVars.stream().toArray(String[]::new)).build());
                                
                                bValuesRefs.add(Long.parseLong(v.getAttribute("valRef")));
                            }
                            else if(v.getAttribute("type").equals("int")){
                                //iValues.add(Integer.parseInt(v.getAttribute("newVal")));
                                //Get the variables used by this expression
                                List<String> expressionVars = new ArrayList<>(Arrays.asList(v.getAttribute("vars").split(",")));
                                //System.out.println(expressionVars);
                                expressionVars.add("t");
                                //System.out.println(expressionVars);
                                ei.add(new ExpressionBuilder(v.getAttribute("newVal")).variables(expressionVars.stream().toArray(String[]::new)).build());
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
                    events[eIndex] = new Event(id, time, 
                                            dValues.stream().mapToDouble(Double::doubleValue).toArray(), 
                                            dValuesRefs.stream().mapToLong(Long::longValue).toArray(), 
                                            iValues.stream().mapToInt(Integer::intValue).toArray(), 
                                            iValuesRefs.stream().mapToLong(Long::longValue).toArray(), 
                                            bbValues, 
                                            bValuesRefs.stream().mapToLong(Long::longValue).toArray(), 
                                            ssValues, 
                                            sValuesRefs.stream().mapToLong(Long::longValue).toArray(),
                                            duration, durationToggle, ed, ei, eb
                                        );
                    eIndex++;
                }

            }
        }

        return events;
    }

    public static Event[] cutArrayOfEvents(Event[] events, double currentStep, int eventType){
        if(eventType == 1){
            if(events.length !=0 && Math.abs(currentStep - events[0].timePoint) <= 0.0000001){
                
                events = (Event[]) ArrayUtils.remove(events, 0);
                if(verbose){
                    printEvent(events);
                    if(events.length == 0){
                        logger.warn("No more on-time events");
                    }
                }
            }
        }
        else{
            for(var i=events.length-1; i >= 0; i--){
                if(!events[i].durationToggle && Math.abs(currentStep - events[i].timePoint - events[i].duration)<= 0.0000001){
                    events = (Event[]) ArrayUtils.remove(events, i); //remove elements from the end, not to mess up indexes if multiple events are to be removed.
                    if(verbose){
                        printEvent(events);
                        if(events.length == 0){
                            logger.warn("No more duration events");
                        }
                    }
                }
            }
        }
            
        return events;
    }

    //Print all events
    public static void printEvent(Event[] events){
        logger.warn(String.format("events %d", events.length));
        for(Event e: events){
            String printText = "Event with id: " + e.id + ", at time: " + e.timePoint
                                + " with doubles: " + Arrays.toString(e.doubleValues) + " with vrefs: " + Arrays.toString(e.doubleValuesRefs)
                                + "; with ints: " + Arrays.toString(e.intValues) + " with vrefs: " + Arrays.toString(e.intValuesRefs)
                                + "; with bools: " + Arrays.toString(e.boolValues) + " with vrefs: " + Arrays.toString(e.boolValuesRefs)
                                + "; with strings: " + Arrays.toString(e.stringValues) + " with vrefs: " + Arrays.toString(e.stringValuesRefs)
                                + "; duration: " + e.duration + "; durationToggle: " + e.durationToggle;
            logger.warn(printText);
        }
    }

    //Print event at given index
    public static void printEvents(Event[] events, int eventIndex){
        String printText = "Event with id: " + events[eventIndex].id + ", at time: " + events[eventIndex].timePoint
                            + " with doubles: " + Arrays.toString(events[eventIndex].doubleValues) + " with vrefs: " + Arrays.toString(events[eventIndex].doubleValuesRefs)
                            + "; with ints: " + Arrays.toString(events[eventIndex].intValues) + " with vrefs: " + Arrays.toString(events[eventIndex].intValuesRefs)
                            + "; with bools: " + Arrays.toString(events[eventIndex].boolValues) + " with vrefs: " + Arrays.toString(events[eventIndex].boolValuesRefs)
                            + "; with strings: " + Arrays.toString(events[eventIndex].stringValues) + " with vrefs: " + Arrays.toString(events[eventIndex].stringValuesRefs)
                            + "; duration: " + events[eventIndex].duration + "; durationToggle: " + events[eventIndex].durationToggle;
        logger.warn(printText);
    }

}
