package org.intocps.maestro.faultinject;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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
    public String id;
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
    public float keepAliveUntilTime;
    public List<Expression> expressionForDoubles;
    public List<Expression> expressionForInts;
    public List<Expression> expressionForBools;
    public Expression when;
    public Expression otherWhenConditions;

    public Event(String id, double timePoint, double[] doubleValues, long[] doubleValuesRefs, int[] intValues,
            long[] intValuesRefs, boolean[] boolValues, long[] boolValuesRefs, String[] stringValues,
            long[] stringValuesRefs, float keepAlive, List<Expression> expressionForDoubles,
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
        this.keepAliveUntilTime = keepAlive; // if positive gives the time when the event can be safely removed. If negative, the event cannot be removed
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

    public static Event[] getEventswithDuration(String specificationFileName, String wrapperID, boolean verbose) throws InvalidFIConfigurationException, IOException, ParserConfigurationException, SAXException {
        //Parse document
        NodeList eventsList = parseXMLDom(specificationFileName);

        //find out how many events
        int nrEvents = eventsList.getLength();
        logger.info(String.format("Nr of events shared among instances %d", nrEvents));

        Event.verbose = verbose;

        //create events one by one manually
        ArrayList<Event> events = new ArrayList<>();
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
                    String id = eElement.getAttribute("id");

                    if(!wrapperID.equals(id)){
                        logger.warn("wrapper id doesn't match event id");
                        continue;
                    }

                    double time = 0;

                    CustomOperators operators = new CustomOperators();
                    List<String> whenExpressionVars =  new ArrayList<>();

                    if(eElement.hasAttribute("vars") ^ eElement.hasAttribute("other") ){
                        throw new InvalidFIConfigurationException("The vars and other fields of the when conditional have to be both or neither defined.");
                    }

                    if(eElement.hasAttribute("vars") ){
                        String place_holder = eElement.getAttribute("vars");
                        if(!place_holder.isEmpty())
                        {
                            whenExpressionVars.addAll(Arrays.asList(place_holder.split(",")));
                        }
                        else{
                            logger.warn(String.format("vars attribute in when conditional is empty"));
                        }
                    }

                    whenExpressionVars.add("t");

                    String testit = eElement.getAttribute("when");
                    

                    Expression when = new ExpressionBuilder(eElement.getAttribute("when")).operator(operators.not, operators.or, operators.and, operators.gt, 
                                                                                                    operators.gteq, operators.lt, operators.lteq, operators.eq)
                                                                                          .variables(whenExpressionVars.stream().toArray(String[]::new)).build();
                    Expression otherWhen;

                    float keepAlive = (float) -1.0;
                    if(eElement.hasAttribute("other"))
                    {
                        //for (String model : whenExpressionVars ) {
                        //    System.out.println(model);
                        //}

                        String place_holder = eElement.getAttribute("other");
                        if(!place_holder.isEmpty()){
                            otherWhen = new ExpressionBuilder(place_holder).operator(operators.not, operators.or, operators.and, operators.gt,
                                    operators.gteq, operators.lt, operators.lteq, operators.eq).variables(whenExpressionVars.stream().toArray(String[]::new)).build();
                        }
                        else{
                            logger.warn(String.format("other attribute in when conditional is empty"));
                            otherWhen = new ExpressionBuilder("1").build();
                            keepAlive = isEventRemovable(testit, when);
                        }

                    }
                    else{
                        otherWhen = new ExpressionBuilder("1").build();
                        //we check here whether after some point in time t this expression can never again evaluate to true
                        //we do this only if the when condition depends only on the time variable
                        //we don't check for the whole simulation time, instead we find the biggest value in the expression and check whether
                        //the expression can be true for that value plus some delta (positive small number)
                        keepAlive = isEventRemovable(testit, when);
                    }

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
                    events.add(new Event(id, time, dValues.stream().mapToDouble(Double::doubleValue).toArray(),
                            dValuesRefs.stream().mapToLong(Long::longValue).toArray(),
                            iValues.stream().mapToInt(Integer::intValue).toArray(),
                            iValuesRefs.stream().mapToLong(Long::longValue).toArray(),
                            bbValues,
                            bValuesRefs.stream().mapToLong(Long::longValue).toArray(),
                            ssValues,
                            sValuesRefs.stream().mapToLong(Long::longValue).toArray(),
                            keepAlive, ed, ei, eb, when, otherWhen));
                }
                else
                {
                    throw new WrapperException(String.format("No when condition defined for at least one event. Please specify this attribute"));
                }
            }
        }
        logger.info(String.format("after creation %d", events.size()));
        return events.toArray(new Event[0]);
    }

    public static float isEventRemovable(String expressionString, Expression expression)
    {
        int delta = 1;
        boolean eventCanBeDropped = false;
        Pattern pattern = Pattern.compile("(?<=\\().*?(?=\\))");
        Matcher matcher = pattern.matcher(expressionString);

        logger.warn(String.format("temp: %s", expressionString));
        float timeToStop = (float) -1.0;
        while(matcher.find()){
            String exprs = matcher.group();

            float temp = Float.parseFloat(exprs.replaceAll("[^\\d.]", ""));
            if (temp >  timeToStop){
                timeToStop = temp;
            }

        }
        expression.setVariable("t", timeToStop+delta);

        if(expression.evaluate() == 1d){
            logger.warn(String.format("Event cannot be dropped at time %f", timeToStop+delta));
            return (float) -1.0; //event cannot be dropped
        }
        else{
            logger.warn(String.format("Event can be dropped at time %f", timeToStop+delta));
            return timeToStop+delta; //event can be dropped
        }

    }

    public static Event[] cutArrayOfEvents(Event[] events, double currentStep){
        ArrayList<Event> eventsLeft = new ArrayList<Event>();
        for(Event e: events){
            if(e.keepAliveUntilTime > 0 && currentStep > e.keepAliveUntilTime){
                String printText = "[DELETED] Event with id: " + e.id + "; when " + e.when +  " keepAlive: " + e.keepAliveUntilTime;

                logger.warn(printText);
            }
            else{
                eventsLeft.add(e);
            }
        }

        printEvents(eventsLeft.toArray(Event[]::new));
        return eventsLeft.toArray(Event[]::new);
    }

    //Print all events
    public static void printEvents(Event[] events){
        logger.warn(String.format("events %d", events.length));
        String printText;
        for(Event e: events){
            printText = "Event with id: " + e.id + ", at time: " + e.timePoint
                                + " with doubles: " + Arrays.toString(e.doubleValues) + " with vrefs: " + Arrays.toString(e.doubleValuesRefs)
                                + "; with ints: " + Arrays.toString(e.intValues) + " with vrefs: " + Arrays.toString(e.intValuesRefs)
                                + "; with bools: " + Arrays.toString(e.boolValues) + " with vrefs: " + Arrays.toString(e.boolValuesRefs)
                                + "; with strings: " + Arrays.toString(e.stringValues) + " with vrefs: " + Arrays.toString(e.stringValuesRefs)
                                + "; when " + e.when + "; other when conditions: " + e.otherWhenConditions;
            logger.warn(printText);
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
