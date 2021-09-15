package org.intocps.maestro.faultinject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.print.event.PrintEvent;
import javax.swing.text.AbstractDocument.ElementEdit;
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
    public Expression when;
    public Expression otherWhenConditions;

    public Event(int id, double timePoint, double[] doubleValues, long[] doubleValuesRefs, int[] intValues,
            long[] intValuesRefs, boolean[] boolValues, long[] boolValuesRefs, String[] stringValues,
            long[] stringValuesRefs, double duration, boolean durationToggle, List<Expression> expressionForDoubles,
            List<Expression> expressionForInts, List<Expression> expressionForBools, Expression when, Expression otherWhenConditions) {
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
        this.when = when;
        this.otherWhenConditions = otherWhenConditions;
        
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

    public static Event[] getEventswithDuration(String specificationFileName, boolean verbose)throws SAXException, IOException, ParserConfigurationException, 
    NullPointerException, NumberFormatException {
        //Parse document
        NodeList eventsList = parseXMLDom(specificationFileName);

        //find out how many events
        int nrEvents = eventsList.getLength();
        logger.info(String.format("Nr of events %d", nrEvents));

        Event.verbose = verbose;


        //create events one by one manually
        Event[] events = new Event[nrEvents];
        //Loop through events
        //loop all events and keep only those that are duration events
        for(int i = 0; i < nrEvents; i++){
            Node node = eventsList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE)
            {
                Element eElement = (Element) node;

                //if(eElement.hasAttribute("duration") || eElement.hasAttribute("durationToggle"))
                if(eElement.hasAttribute("when"))
                {
                    int id = Integer.parseInt(eElement.getAttribute("id"));
                    double time = 0;

                    CustomOperators operators = new CustomOperators();
                    List<String> whenExpressionVars =  new ArrayList<>();
                    
                    if(eElement.hasAttribute("vars")){
                        whenExpressionVars.addAll(Arrays.asList(eElement.getAttribute("vars").split(",")));
                    }
                    whenExpressionVars.add("t");

                    Expression when = new ExpressionBuilder(eElement.getAttribute("when")).operator(operators.not, operators.or, operators.and, operators.gt, 
                                                                                                    operators.gteq, operators.lt, operators.lteq, operators.eq)
                                                                                          .variables(whenExpressionVars.stream().toArray(String[]::new)).build();
                    Expression otherWhen;
                    if(eElement.hasAttribute("other"))
                    {
                        //for (String model : whenExpressionVars ) {
                        //    System.out.println(model);
                        //}
                        otherWhen = new ExpressionBuilder(eElement.getAttribute("other")).operator(operators.not, operators.or, operators.and, operators.gt, 
                    operators.gteq, operators.lt, operators.lteq, operators.eq).variables(whenExpressionVars.stream().toArray(String[]::new)).build();

                    }
                    else{
                        otherWhen = new ExpressionBuilder("1").build();
                    }

                    double duration = 1;
                    Boolean durationToggle = false;
                    List<Expression> ed = new ArrayList<>();
                    List<Expression> ei = new ArrayList<>();
                    List<Expression> eb = new ArrayList<>();

                    List<Double> dValues = new ArrayList<>();
                    List<Long> dValuesRefs = new ArrayList<>();

                    List<Boolean> bValues = new ArrayList<>();
                    List<Long> bValuesRefs = new ArrayList<>();

                    List<Integer> iValues = new ArrayList<>();
                    List<Long> iValuesRefs = new ArrayList<>();

                    List<String> sValues = new ArrayList<>();
                    List<Long> sValuesRefs = new ArrayList<>();

                    NodeList variables = eElement.getElementsByTagName("variable");
                    //Loop through variables within one event
                    for(int j = 0; j < variables.getLength(); j++){
                        Node var = variables.item(j);
                        //prepare the values which are passed to the event constructor.
                        if(var.getNodeType() == Node.ELEMENT_NODE){
                            Element v = (Element) var;
                            if (!v.hasAttribute("type") || !v.hasAttribute("newVal") || !v.hasAttribute("valRef"))
                            {
                                throw new WrapperException(String.format("Attributes type, newVal, valRef are mandatory for the definition of events. Please make sure they're all specified for each event."));
                            }

                            if(v.getAttribute("type").equals("real")){
                                List<String> expressionVars = new ArrayList<>(Arrays.asList(v.getAttribute("vars").split(",")));
                                //System.out.println(expressionVars);
                                expressionVars.add("t");
                                ed.add(new ExpressionBuilder(v.getAttribute("newVal")).variables(expressionVars.stream().toArray(String[]::new)).build());
                                //dValues.add(Double.parseDouble(v.getAttribute("newVal")));
                                dValuesRefs.add(Long.parseLong(v.getAttribute("valRef")));
                            }
                            else if(v.getAttribute("type").equals("bool")){
                                //bValues.add(Boolean.parseBoolean(v.getAttribute("newVal")));
                                List<String> expressionVars = new ArrayList<>(Arrays.asList(v.getAttribute("vars").split(",")));
                                expressionVars.add("t");
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
                    events[i] = new Event(id, time, 
                                            dValues.stream().mapToDouble(Double::doubleValue).toArray(), 
                                            dValuesRefs.stream().mapToLong(Long::longValue).toArray(), 
                                            iValues.stream().mapToInt(Integer::intValue).toArray(), 
                                            iValuesRefs.stream().mapToLong(Long::longValue).toArray(), 
                                            bbValues, 
                                            bValuesRefs.stream().mapToLong(Long::longValue).toArray(), 
                                            ssValues, 
                                            sValuesRefs.stream().mapToLong(Long::longValue).toArray(),
                                            duration, durationToggle, ed, ei, eb, when, otherWhen
                                        );
                }
                else
                {
                    throw new WrapperException(String.format("No when condition defined for at least one event. Please specify this attribute"));
                }
            }
        }
        logger.info(String.format("after creation %d", events.length));
        return events;
    }

    public static Event[] cutArrayOfEvents(Event[] events, double currentStep){
        logger.warn(String.format("This method is empty now. Needs to be implemented"));
        for(Event e: events){
            String printText = "Event with id: " + e.id + ", at time: " + e.timePoint
                                + " with doubles: " + Arrays.toString(e.doubleValues) + " with vrefs: " + Arrays.toString(e.doubleValuesRefs)
                                + "; with ints: " + Arrays.toString(e.intValues) + " with vrefs: " + Arrays.toString(e.intValuesRefs)
                                + "; with bools: " + Arrays.toString(e.boolValues) + " with vrefs: " + Arrays.toString(e.boolValuesRefs)
                                + "; with strings: " + Arrays.toString(e.stringValues) + " with vrefs: " + Arrays.toString(e.stringValuesRefs)
                                + "; when " + e.when + "; other when conditions: " + e.otherWhenConditions;
            logger.warn(printText);
        }
        return events;
    }

    //Print all events
    public static void printEvents(Event[] events){
        logger.debug(String.format("events %d", events.length));
        for(Event e: events){
            String printText = "Event with id: " + e.id + ", at time: " + e.timePoint
                                + " with doubles: " + Arrays.toString(e.doubleValues) + " with vrefs: " + Arrays.toString(e.doubleValuesRefs)
                                + "; with ints: " + Arrays.toString(e.intValues) + " with vrefs: " + Arrays.toString(e.intValuesRefs)
                                + "; with bools: " + Arrays.toString(e.boolValues) + " with vrefs: " + Arrays.toString(e.boolValuesRefs)
                                + "; with strings: " + Arrays.toString(e.stringValues) + " with vrefs: " + Arrays.toString(e.stringValuesRefs)
                                + "; when " + e.when + "; other when conditions: " + e.otherWhenConditions;
            logger.info(printText);
        }
    }

    //Print event at given index
    public static void printEvent(Event[] events, int eventIndex){
        String printText = "Event with id: " + events[eventIndex].id + ", at time: " + events[eventIndex].timePoint
                            + " with doubles: " + Arrays.toString(events[eventIndex].doubleValues) + " with vrefs: " + Arrays.toString(events[eventIndex].doubleValuesRefs)
                            + "; with ints: " + Arrays.toString(events[eventIndex].intValues) + " with vrefs: " + Arrays.toString(events[eventIndex].intValuesRefs)
                            + "; with bools: " + Arrays.toString(events[eventIndex].boolValues) + " with vrefs: " + Arrays.toString(events[eventIndex].boolValuesRefs)
                            + "; with strings: " + Arrays.toString(events[eventIndex].stringValues) + " with vrefs: " + Arrays.toString(events[eventIndex].stringValuesRefs)
                            + "; when " + events[eventIndex].when + "; other when conditions: " + events[eventIndex].otherWhenConditions;
        logger.info(printText);
    }

}
