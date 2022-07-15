package org.intocps.maestro.faultinject;

import com.spencerwi.either.Either;

import org.intocps.maestro.interpreter.InterpreterException;
import org.intocps.maestro.interpreter.api.IValueLifecycleHandler;
import org.intocps.maestro.interpreter.values.ArrayValue;
import org.intocps.maestro.interpreter.values.BooleanValue;
import org.intocps.maestro.interpreter.values.ExternalModuleValue;
import org.intocps.maestro.interpreter.values.FunctionValue;
import org.intocps.maestro.interpreter.values.IntegerValue;
import org.intocps.maestro.interpreter.values.NumericValue;
import org.intocps.maestro.interpreter.values.RealValue;
import org.intocps.maestro.interpreter.values.StringValue;
import org.intocps.maestro.interpreter.values.UnsignedIntegerValue;
import org.intocps.maestro.interpreter.values.UpdatableValue;
import org.intocps.maestro.interpreter.values.Value;
import org.intocps.maestro.interpreter.values.fmi.FmuComponentStateValue;
import org.intocps.maestro.interpreter.values.fmi.FmuComponentValue;
import org.intocps.maestro.interpreter.values.fmi.FmuValue;
import org.intocps.fmi.Fmi2Status;
import org.intocps.fmi.Fmi2StatusKind;
import org.intocps.fmi.FmiInvalidNativeStateException;
import org.intocps.fmi.FmuInvocationException;
import org.intocps.fmi.FmuResult;
import org.intocps.fmi.IFmiComponent;
import org.intocps.fmi.IFmiComponentState;
import org.intocps.fmi.InvalidParameterException;
import org.apache.commons.compress.archivers.EntryStreamOffsets;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.crypto.Data;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;


@IValueLifecycleHandler.ValueLifecycle(name = "FaultInject")
public class FaultInjectRuntimeModule implements IValueLifecycleHandler {

    static final Logger logger = (Logger) LoggerFactory.getLogger(FaultInjectRuntimeModule.class);

    static String errorMsg = "value not a reference value";

    public static class FaultInjectModule extends ExternalModuleValue<String> {

        public static class WrapperFmuComponentValue extends ExternalModuleValue<IFmiComponent> {
            FmuComponentValue wrappedComponent;
            String wrapperID;
            FmuValue fmu;
            String eventsSpecificationFile;
            private double currentStep = 0.0;
            private double stepSize = 0.1;

            private Event[] simulationDurationEvents;

            //Used by the expression evaluator to take into account the state of the fmu during injection.
            private Datapoint currentInput = new Datapoint();
            private Datapoint currentOutput = new Datapoint();

            public WrapperFmuComponentValue(FmuComponentValue component, Map<String, Value> wrapperMembers,
                    String wrapperID, FmuValue fmu) {
                super(wrapperMembers, component.getModule());
                this.wrappedComponent = component;
                this.wrapperID = wrapperID;
                this.fmu = fmu;
            }

            public void dumpXmlFile() throws IOException {
                DocumentBuilderFactory dbFactory =
                        DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = null;
                try {
                    dBuilder = dbFactory.newDocumentBuilder();
                } catch (ParserConfigurationException e) {
                    e.printStackTrace();
                }
                Document doc = dBuilder.newDocument();

                // root element
                Element rootElement = doc.createElement("events");
                doc.appendChild(rootElement);
                for (Event e: simulationDurationEvents){
                    logger.info(String.format("[dumpXmmlFle] event: %s", e.id));
                    // supercars element
                    Element supercar = doc.createElement("event");
                    rootElement.appendChild(supercar);

                    // setting attribute to element
                    Attr attr = doc.createAttribute("id");
                    attr.setValue(String.valueOf(e.id));
                    supercar.setAttributeNode(attr);
                }

                // write the content into xml file
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = null;
                try {
                    transformer = transformerFactory.newTransformer();
                } catch (TransformerConfigurationException e) {
                    e.printStackTrace();
                }
                DOMSource source = new DOMSource(doc);

                System.out.println(System.getProperty("user.dir")+"/events_xml_log.xml");
                logger.info(System.getProperty("user.dir")+"/events_xml_log.xml");
                File logFile = new File(System.getProperty("user.dir")+"/events_xml_log.xml");
                logFile.createNewFile(); // if file already exists will do nothing

                StreamResult result = new StreamResult(logFile);

                try {
                    transformer.transform(source, result);
                } catch (TransformerException e) {
                    e.printStackTrace();
                }
            }

            public static Event[] createDurationEvents(String faultSpecFile, String componentID){
                Event[] simuEvents = {};
                try {
                    boolean verbose = true;
                    simuEvents = Event.getEventswithDuration(faultSpecFile, componentID, verbose);
                    logger.info(String.format("length of events: %d", simuEvents.length));
                    Event.printEvents(simuEvents);
                    return simuEvents;
                } catch (NumberFormatException | NullPointerException | SAXException | IOException | ParserConfigurationException | InvalidFIConfigurationException e) {
                    logger.error("Something went terribly wrong when creating the events");
                    logger.error(e.toString());
                    return simuEvents;
                }
            }
            
            public static boolean getBool(Value value) {

                value = value.deref();
        
                if (value instanceof BooleanValue) {
                    return ((BooleanValue) value).getValue();
                }
                throw new InterpreterException("Value is not boolean");
            }
        
            public static double getDouble(Value value) {
        
                value = value.deref();
        
                if (value instanceof RealValue) {
                    return ((RealValue) value).getValue();
                }
                throw new InterpreterException("Value is not double");
            }
        
            public static String getString(Value value) {
        
                value = value.deref();
        
                if (value instanceof StringValue) {
                    return ((StringValue) value).getValue();
                }
                throw new InterpreterException("Value is not string");
            }
        
            public static long getUint(Value value) {

                value = value.deref();
        
                if (value instanceof UnsignedIntegerValue) {
                    return ((UnsignedIntegerValue) value).getValue();
                }
                if (value instanceof IntegerValue) {
                    return ((IntegerValue) value).getValue();
                }
                throw new InterpreterException("Value is not unsigned integer");
            }

            static <T extends Value> List<T> getArrayValue(Value value, Class<T> clz) {
                return getArrayValue(value, Optional.empty(), clz);
            }
        
            static <T extends Value> List<T> getArrayValue(Value value, Optional<Long> limit, Class<T> clz) {
        
                value = value.deref();
        
                if (value instanceof ArrayValue) {
        
                    ArrayValue<? extends Value> array = (ArrayValue<Value>) value;
                    if (((ArrayValue) value).getValues().isEmpty()) {
                        return Collections.emptyList();
                    }
        
                    if (!clz.isAssignableFrom(array.getValues().get(0).deref().getClass())) {
                        throw new InterpreterException("Array not containing the right type. Expected: " + clz.getSimpleName() + " Actual: " +
                                array.getValues().get(0).getClass().getSimpleName());
                    }
                    if (limit.isPresent()) {
                        return array.getValues().stream().limit(limit.get()).map(Value::deref).map(clz::cast).collect(Collectors.toList());
                    } else {
                        return array.getValues().stream().map(Value::deref).map(clz::cast).collect(Collectors.toList());
                    }
                }
                throw new InterpreterException("Value is not an array");
            }

            public boolean evaluateWhenCondition(Expression e, Expression o, double simtime){
                //System.out.println(e);
                Set<String> whenVars = e.getVariableNames();
                Set<String> whenOtherVars = o.getVariableNames();
                logger.debug(String.format("EVAL at time %f",simtime));

                //if variable in expression set its value
                if (whenVars.contains("t"))//simulation time
                {
                    //System.out.println("EVAL");
                    e.setVariable("t", simtime);
                    //logger.debug(String.format("Set value of t variable %f", currentStep + stepSize));
                }
                //Check if any boolean inputs are needed in the expression
                for (Map.Entry<Long,Boolean> entry : currentInput.booleanValues.entrySet())
                {
                    String var = "var_"+entry.getKey();
                    //logger.debug(var);
                    if (whenOtherVars.contains(var))
                    {
                        if(entry.getValue().booleanValue())
                        {
                            o.setVariable(var, 1d);
                        }
                        else{
                            o.setVariable(var, 0d);
                        }
                        //logger.debug(String.format("Set value of %s input variable %s", var, entry.getValue()));
                    }
                }
                //Check if any boolean outputs are needed in the expression
                for (Map.Entry<Long,Boolean> entry : currentOutput.booleanValues.entrySet())
                {
                    String var = "var_"+entry.getKey();
                    //logger.debug(var);
                    if (whenOtherVars.contains(var))
                    {
                        if(entry.getValue().booleanValue())
                        {
                            o.setVariable(var, 1d);
                        }
                        else{
                            o.setVariable(var, 0d);
                        }
                        //logger.debug(String.format("Set value of %s output variable %s", var, entry.getValue()));
                    }
                }
                
                //Check if any double inputs are needed in the expression
                for (Map.Entry<Long,Double> entry : currentInput.doubleValues.entrySet())
                {
                    String var = "var_"+entry.getKey();
                    //logger.debug(var);
                    if (whenOtherVars.contains(var))
                    {
                        o.setVariable(var, entry.getValue());
                        //logger.debug(String.format("Set value of %s input variable %f", var, entry.getValue()));
                    }
                }
                //Check if any double outputs are needed in the expression
                for (Map.Entry<Long,Double> entry : currentOutput.doubleValues.entrySet())
                {
                    String var = "var_"+entry.getKey();
                    //logger.debug(var);
                    if (whenOtherVars.contains(var))
                    {
                        o.setVariable(var, entry.getValue());
                        //logger.debug(String.format("Set value of %s output variable %f", var, entry.getValue()));
                    }
                }
                
                //Check if any int inputs are needed in the expression
                for (Map.Entry<Long,Integer> entry : currentInput.integerValues.entrySet())
                {
                    String var = "var_"+entry.getKey();
                    //logger.debug(var);
                    if (whenOtherVars.contains(var))
                    {
                        o.setVariable(var, entry.getValue());
                        //logger.debug(String.format("Set value of %s input variable %f", var, entry.getValue()));
                    }
                }
                //Check if any int outputs are needed in the expression
                for (Map.Entry<Long,Integer> entry : currentOutput.integerValues.entrySet())
                {
                    String var = "var_"+entry.getKey();
                    //logger.debug(var);
                    if (whenOtherVars.contains(var))
                    {
                        o.setVariable(var, entry.getValue());
                        //logger.debug(String.format("Set value of %s output variable %f", var, entry.getValue()));
                    }
                }

                if(e.evaluate() == 1d)
                {
                    if((currentStep > 0.0) && o.evaluate() == 1d)
                    {
                        //System.out.println("INJECTING");
                        return true;
                    }
                    else
                    {
                        return false;
                    }
                }
                else
                {
                    return false;
                }
            }

            public Pair<double[], long[]> getDoublesFromEvent(double simtime){
                //Check the first event in simulationEvents
                double[] dv = {};
                long[] dvr = {};
                Pair<double[], long[]> result = Pair.of(dv,dvr);
                //loop over duration events
                boolean withinDuration;
                for(var i = 0; i < simulationDurationEvents.length; i++){

                    withinDuration = evaluateWhenCondition(simulationDurationEvents[i].when, simulationDurationEvents[i].otherWhenConditions, simtime);

                    if(withinDuration){//comparing doubles...
                        logger.debug("Looking for Duration Events");
                        //Evaluate expression, by checking which variables are contained, and setting them first. 
                        List<Double> doubleValues = new ArrayList<>(simulationDurationEvents[i].doubleValuesRefs.length);

                        for(int vr = 0; vr < simulationDurationEvents[i].doubleValuesRefs.length; vr++)
                        {
                                Expression e = simulationDurationEvents[i].expressionForDoubles.get(vr);
                                //System.out.println(e);
    
                                Set<String> vars = e.getVariableNames();
                                //logger.debug(String.format("Nr of events %s", Arrays.toString(vars.toArray())));
    
                                //if variable in expression set its value
                                if (vars.contains("t"))//simulation time
                                {
                                    e.setVariable("t", simtime);
                                    //logger.debug(String.format("Set value of t variable %f", currentStep + stepSize));
                                }
                                //Check if any inputs are needed in the expression
                                for (Map.Entry<Long,Double> entry : currentInput.doubleValues.entrySet())
                                {
                                    String var = "var_"+entry.getKey();
                                    logger.debug(var);
                                    if (vars.contains(var))
                                    {
                                        e.setVariable(var, entry.getValue());
                                        //logger.debug(String.format("Set value of %s input variable %f", var, entry.getValue()));
                                    }
                                }
                                //Check if any outputs are needed in the expression
                                for (Map.Entry<Long,Double> entry : currentOutput.doubleValues.entrySet())
                                {
                                    String var = "var_"+entry.getKey();
                                    logger.debug(var);
                                    if (vars.contains(var))
                                    {
                                        e.setVariable(var, entry.getValue());
                                        //logger.debug(String.format("Set value of %s output variable %f", var, entry.getValue()));
                                    }
                                }
                                doubleValues.add(e.evaluate());
                        }
                        if(simulationDurationEvents[i].doubleValuesRefs.length > 0)
                        {
                            result = Pair.of(doubleValues.stream().mapToDouble(Double::doubleValue).toArray(), simulationDurationEvents[i].doubleValuesRefs);
                        }
                        
                    }
                }
                
                return result;
            }

            public Pair<int[], long[]> getIntsFromEvent(double simtime){
                //Check the first event in simulationEvents
                int[] dv = {};
                long[] dvr = {};
                Pair<int[], long[]> result = Pair.of(dv,dvr);
                
                //checking for duration events
                //loop over duration events
                boolean withinDuration;
                for(var i = 0; i < simulationDurationEvents.length; i++){
                    withinDuration = evaluateWhenCondition(simulationDurationEvents[i].when, simulationDurationEvents[i].otherWhenConditions, simtime);


                    if(withinDuration){//comparing doubles...
                        logger.debug("Looking for Duration Events");
                        List<Integer> intValues = new ArrayList<>(simulationDurationEvents[i].intValuesRefs.length);
                        Event.printEvents(simulationDurationEvents);
                        for(int vr = 0; vr < simulationDurationEvents[i].intValuesRefs.length; vr++)
                        {

                            logger.debug(String.format("intvrefs %d", simulationDurationEvents[i].intValuesRefs.length));
                            Event.printEvent(simulationDurationEvents, i);
                            Expression e = simulationDurationEvents[i].expressionForInts.get(vr); 
                            String eString = "" + e;
                            logger.debug(eString);

                            Set<String> vars = e.getVariableNames();
                                    //logger.debug(String.format("Nr of events %s", Arrays.toString(vars.toArray())));

                            //if variable in expression set its value
                            if (vars.contains("t"))//simulation time
                            {
                                e.setVariable("t", simtime);
                                //logger.debug(String.format("Set value of t variable %f", currentStep + stepSize));
                            }
                            //Check if any inputs are needed in the expression
                            for (Map.Entry<Long,Integer> entry : currentInput.integerValues.entrySet())
                            {
                                String var = "var_"+entry.getKey();
                                logger.debug(var);
                                if (vars.contains(var))
                                {
                                    e.setVariable(var, entry.getValue());
                                    //logger.debug(String.format("Set value of %s input variable %f", var, entry.getValue()));
                                }
                            }
                            //Check if any outputs are needed in the expression
                            for (Map.Entry<Long,Integer> entry : currentOutput.integerValues.entrySet())
                            {
                                String var = "var_"+entry.getKey();
                                logger.debug(var);
                                if (vars.contains(var))
                                {
                                    e.setVariable(var, entry.getValue());
                                    //logger.debug(String.format("Set value of %s output variable %f", var, entry.getValue()));
                                }
                            }
                            intValues.add((int) e.evaluate());
                        }
                        if(simulationDurationEvents[i].intValuesRefs.length > 0)
                        {
                            result = Pair.of(intValues.stream().mapToInt(Integer::intValue).toArray(), simulationDurationEvents[i].intValuesRefs);
                        }
                    }
                }
                return result;
            }

            public Pair<boolean[], long[]> getBooleansFromEvent(double simtime){
                //Check the first event in simulationEvents
                boolean[] dv = {};
                long[] dvr = {};
                Pair<boolean[], long[]> result = Pair.of(dv,dvr);
                //checking for duration events
                //loop over duration events
                boolean withinDuration;
                for(var i = 0; i < simulationDurationEvents.length; i++){
                    withinDuration = evaluateWhenCondition(simulationDurationEvents[i].when,simulationDurationEvents[i].otherWhenConditions, simtime);

                    if(withinDuration){//comparing doubles...
                        logger.debug("Looking for Duration Events");
                        List<Boolean> boolValues = new ArrayList<>(simulationDurationEvents[i].boolValuesRefs.length);
                    
                        for(int vr = 0; vr < simulationDurationEvents[i].boolValuesRefs.length; vr++)
                        {
                            logger.debug("iterating");
                            Expression e = simulationDurationEvents[i].expressionForBools.get(vr);
                            System.out.println(e);

                            Set<String> vars = e.getVariableNames();
                            //logger.debug(String.format("Nr of events %s", Arrays.toString(vars.toArray())));

                            //if variable in expression set its value
                            if (vars.contains("t"))//simulation time
                            {
                                e.setVariable("t", simtime);
                                //logger.debug(String.format("Set value of t variable %f", currentStep + stepSize));
                            }
                            //Check if any inputs are needed in the expression
                            for (Map.Entry<Long,Boolean> entry : currentInput.booleanValues.entrySet())
                            {
                                String var = "var_"+entry.getKey();
                                logger.debug(var);
                                if (vars.contains(var))
                                {
                                    if(entry.getValue().booleanValue())
                                    {
                                        e.setVariable(var, 1d);
                                    }
                                    else{
                                        e.setVariable(var, 0d);
                                    }
                                    //logger.debug(String.format("Set value of %s input variable %s", var, entry.getValue()));
                                }
                            }
                            //Check if any outputs are needed in the expression
                            for (Map.Entry<Long,Boolean> entry : currentOutput.booleanValues.entrySet())
                            {
                                String var = "var_"+entry.getKey();
                                logger.debug(var);
                                if (vars.contains(var))
                                {
                                    if(entry.getValue().booleanValue())
                                    {
                                        e.setVariable(var, 1d);
                                    }
                                    else{
                                        e.setVariable(var, 0d);
                                    }
                                    //logger.debug(String.format("Set value of %s output variable %s", var, entry.getValue()));
                                }
                            }
                            if(e.evaluate() == 0d)
                            {
                                boolValues.add(false);
                            }
                            else
                            {
                                boolValues.add(true);
                            }
                        }
                        if(simulationDurationEvents[i].boolValuesRefs.length > 0)
                        {
                            result = Pair.of(ArrayUtils.toPrimitive(boolValues.toArray(ArrayUtils.EMPTY_BOOLEAN_OBJECT_ARRAY)), simulationDurationEvents[i].boolValuesRefs);
                        }
                        
                    }
                }
                return result;
            }

            public Pair<String[], long[]> getStringsFromEvent(double simtime){
                //Check the first event in simulationEvents
                String[] dv = {};
                long[] dvr = {};
                Pair<String[], long[]> result = Pair.of(dv,dvr);
                boolean withinDuration;
                for(var i = 0; i < simulationDurationEvents.length; i++){
                    withinDuration = evaluateWhenCondition(simulationDurationEvents[i].when, simulationDurationEvents[i].otherWhenConditions,simtime);
                    if(withinDuration){//comparing doubles...
                        logger.debug("Looking for Duration Events");
                        result = Pair.of(simulationDurationEvents[i].stringValues, simulationDurationEvents[i].stringValuesRefs);
                    }
                }
                return result;
            }

            public static <T> T[] inject(T[]  values, long[] valueRefs, T[] newValues, long[] newValuesRefs){
                //Check that values and valuesRefs have the same length
                if(values.length != valueRefs.length){
                    throw new WrapperException("size mismatch between value and valueRef array");
                }
                //Check that newValues and newValuesRefs have the same length
                if(newValues.length != newValuesRefs.length){
                    System.out.println(newValues.length);
                    System.out.println(newValuesRefs.length);
                    throw new WrapperException("size mismatch between value and valueRef array");
                }

                //Iterate over valueRefs
                for (long vr : valueRefs) { 
                    //if an element of value refs is present in newValuesRefs, set the new value in values from newValues
                    int idx = ArrayUtils.indexOf(newValuesRefs, vr);
                    if(idx!=-1){
                        int idy = ArrayUtils.indexOf(valueRefs, vr);
                        if(idy!=-1){
                            String v = "" + values[idy];
                            String nv = "" + newValues[idy];
                            logger.debug(String.format("Injecting vref: %d, old value: %s, new value: %s", vr, v, nv));
                            values[idy] = newValues[idx];
                        }
                    }
                } 
                return values;
            }

            private WrapperFmuComponentValue getWrapperComponent(FmuComponentValue component, String wrapperID, FmuValue fmu, String faultSpecFile) {
                logger.info("creating members");
                Map<String, Value> wrapperMembers = new HashMap<>();

                wrapperMembers.put("setDebugLogging", new FunctionValue.ExternalFunctionValue(fcargs -> {

                    checkArgLength(fcargs, 3);
        
                    boolean debugLogginOn = getBool(fcargs.get(0));
                    //int arraySize = getInteger(fcargs.get(1));
                    List<StringValue> categories = getArrayValue(fcargs.get(2), StringValue.class);
        
                    try {
                        Fmi2Status res = component.getModule().setDebugLogging(debugLogginOn, categories.stream().map(StringValue::getValue).toArray(String[]::new));
                        return new IntegerValue(res.value);
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
                }));
                
                wrapperMembers.put("doStep", new FunctionValue.ExternalFunctionValue(fcargs -> {

                    checkArgLength(fcargs, 3);
        
                    double currentCommunicationPoint = getDouble(fcargs.get(0));
                    double communicationStepSize = getDouble(fcargs.get(1));
                    boolean noSetFMUStatePriorToCurrentPoint = getBool(fcargs.get(2));
                    
                    logger.info(String.format("doStep for time %f", currentCommunicationPoint+communicationStepSize));
                    // Keep track of the current timestep in which the fmu is. Needed by the inject functions. 
                    stepSize = communicationStepSize;
                    currentStep = currentCommunicationPoint + stepSize;
                    // Cleanup the events array
                    simulationDurationEvents = Event.cutArrayOfEvents(simulationDurationEvents, currentStep);
                    //logger.debug(String.format("length of events: %d", simulationDurationEvents.length));
                    //Event.printEvents(simulationDurationEvents);
        
                    try {
                        Fmi2Status res = component.getModule().doStep(currentCommunicationPoint, communicationStepSize, noSetFMUStatePriorToCurrentPoint);
                        //logger.debug(String.format("doStep outcome %d", res.value));
                        return new IntegerValue(res.value);
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
                }));
                wrapperMembers.put("setReal", new FunctionValue.ExternalFunctionValue(fcargs -> {

                    checkArgLength(fcargs, 3);
                    long elementsToUse = getUint(fcargs.get(1));

                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), Optional.of(elementsToUse), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
                    double[] values = getArrayValue(fcargs.get(2), Optional.of(elementsToUse), RealValue.class).stream().mapToDouble(RealValue::getValue).toArray();
                    
                    //logger.debug(String.format("The values to set %s for time %f", Arrays.toString(values), currentStep+stepSize));

                    //logger.debug(String.format("scalarValueIndices %s", Arrays.toString(scalarValueIndices)));
                    
                    if(simulationDurationEvents.length != 0){
                        //Get data from the next event if any
                        double[] result;
                        long[] newValuesRefs;

                        Pair<double[], long[]> out = getDoublesFromEvent(currentStep+stepSize);
                        result = out.getLeft();
                        newValuesRefs = out.getRight();
                        
                        //Turn arrays of primitives to arrays of Double
                        Double[] newValues = DoubleStream.of(result).boxed().collect(Collectors.toList()).toArray(Double[]::new);
                        Double[] oldValues = DoubleStream.of(values).boxed().collect(Collectors.toList()).toArray(Double[]::new);

                        // Inject -- if so defined in the specification -- the values before setting them
                        Double[] injected = inject(oldValues, scalarValueIndices, newValues, newValuesRefs);
                        values = ArrayUtils.toPrimitive(injected);
                    }
                    
                    logger.debug(String.format("The INPUT values %s", Arrays.toString(values)));

                    //clear previous double outputs
                    currentInput.doubleValues.clear();

                    //put new values
                    for(int i=0; i < values.length; i++){
                        currentInput.doubleValues.put(scalarValueIndices[i], values[i]);
                        //logger.debug(String.format("current input doubles %d, %f", scalarValueIndices[i], values[i]));
                    }

                    try {
                        Fmi2Status res = component.getModule().setReals(scalarValueIndices, values);
                        //logger.debug(String.format("setupReal outcome %d", res.value));
                        return new IntegerValue(res.value);
                    } catch (InvalidParameterException | FmiInvalidNativeStateException e) {
                        throw new InterpreterException(e);
                    }
        
                }));
                wrapperMembers.put("getReal", new FunctionValue.ExternalFunctionValue(fcargs -> {

                    checkArgLength(fcargs, 3);
        
                    if (!(fcargs.get(2) instanceof UpdatableValue)) {
                        throw new InterpreterException(FaultInjectRuntimeModule.errorMsg);
                    }
                    
                    long elementsToUse = getUint(fcargs.get(1));
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), Optional.of(elementsToUse), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
        
                    try {
                        FmuResult<double[]> res = component.getModule().getReal(scalarValueIndices);
                        //logger.debug(String.format("getReal outcome: %d", res.status.value));

                        if (res.status == Fmi2Status.OK) {
                            UpdatableValue ref = (UpdatableValue) fcargs.get(2);
                            //logger.debug(String.format("getReal outcome %d", res.status.value));
        
                            List<RealValue> values = Arrays.stream(ArrayUtils.toObject(res.result)).map(d -> new RealValue(d)).collect(Collectors.toList());
        
                            //Inject after getting the values
                            if(simulationDurationEvents.length != 0){
                                //Get data from the next event if any
                                double[] result;
                                long[] newValuesRefs;
        
                                Pair<double[], long[]> out = getDoublesFromEvent(currentStep);
                                result = out.getLeft();
                                newValuesRefs = out.getRight();
                                
                                //Turn arrays of primitives to arrays of Double
                                Double[] newValues = DoubleStream.of(result).boxed().collect(Collectors.toList()).toArray(Double[]::new);

                                //Turn List<RealValue> to Double[]
                                Double[] oldValues = values.stream().map(x->x.getValue()).collect(Collectors.toList()).toArray(Double[]::new);
        
                                // Inject -- if so defined in the specification -- the values before setting them
                                Double[] injected = inject(oldValues, scalarValueIndices, newValues, newValuesRefs);

                                //convert Double[] to List<RealValue>
                                values = Arrays.asList(injected).stream().map(x-> new RealValue(x)).collect(Collectors.toList());

                                logger.debug(String.format("The OUTPUT values %s", Arrays.toString(values.toArray())));
                                
                            }
                            //clear previous double outputs
                            currentOutput.doubleValues.clear();
                            double[] vals = ArrayUtils.toPrimitive(values.stream().map(x->x.getValue()).collect(Collectors.toList()).toArray(Double[]::new));
                            //put new values
                            for(int i=0; i < vals.length; i++){
                                currentOutput.doubleValues.put(scalarValueIndices[i], vals[i]);
                                //logger.debug(String.format("current output doubles %d, %f", scalarValueIndices[i], vals[i]));
                            }

                            ref.setValue(new ArrayValue<>(values));
                        }
        
                        
                        return new IntegerValue(res.status.value);
        
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
        
        
                }));
                wrapperMembers.put("setBoolean", new FunctionValue.ExternalFunctionValue(fcargs -> {

                    checkArgLength(fcargs, 3);
                    long elementsToUse = getUint(fcargs.get(1));
        
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), Optional.of(elementsToUse), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
                    boolean[] values = ArrayUtils.toPrimitive(
                            getArrayValue(fcargs.get(2), Optional.of(elementsToUse), BooleanValue.class).stream().map(BooleanValue::getValue).collect(Collectors.toList())
                                    .toArray(new Boolean[]{}));
                    if(simulationDurationEvents.length != 0){
                        //Get data from the next event if any
                        boolean[] result;
                        long[] newValuesRefs;

                        Pair<boolean[], long[]> out = getBooleansFromEvent(currentStep+stepSize);
                        result = out.getLeft();
                        newValuesRefs = out.getRight();
                        
                        //Turn arrays of primitives to arrays of Boolean
                        Boolean[] newValues = ArrayUtils.toObject(result);

                        Boolean[] oldValues = ArrayUtils.toObject(values);
                        // Inject -- if so defined in the specification -- the values before setting them
                        Boolean[] injected = inject(oldValues, scalarValueIndices, newValues, newValuesRefs);
                        values = ArrayUtils.toPrimitive(injected); 
                    }
                    //clear previous bool inputs
                    currentInput.doubleValues.clear();

                    //put new values
                    for(int i=0; i < values.length; i++){
                        currentInput.booleanValues.put(scalarValueIndices[i], values[i]);
                        //logger.debug(String.format("current input doubles %d, %f", scalarValueIndices[i], values[i]));
                    }
                    logger.debug(String.format("The INPUT values %s", Arrays.toString(values)));
                    
                    try {
                        Fmi2Status res = component.getModule().setBooleans(scalarValueIndices, values);
                        return new IntegerValue(res.value);
                    } catch (InvalidParameterException | FmiInvalidNativeStateException e) {
                        throw new InterpreterException(e);
                    }
                }));
                wrapperMembers.put("getBoolean", new FunctionValue.ExternalFunctionValue(fcargs -> {

                    checkArgLength(fcargs, 3);
        
                    if (!(fcargs.get(2) instanceof UpdatableValue)) {
                        throw new InterpreterException(FaultInjectRuntimeModule.errorMsg);
                    }
                    long elementsToUse = getUint(fcargs.get(1));
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), Optional.of(elementsToUse), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
        
        
                    try {
                        FmuResult<boolean[]> res = component.getModule().getBooleans(scalarValueIndices);
                        //logger.debug(String.format("getBoolean outcome %d", res.status.value));
                        if (res.status == Fmi2Status.OK) {
                            UpdatableValue ref = (UpdatableValue) fcargs.get(2);
        
                            List<BooleanValue> values = Arrays.stream(ArrayUtils.toObject(res.result)).map(BooleanValue::new).collect(Collectors.toList());
        
                            //Inject after getting the values
                            if(simulationDurationEvents.length != 0){
                                //Get data from the next event if any
                                boolean[] result;
                                long[] newValuesRefs;
        
                                Pair<boolean[], long[]> out = getBooleansFromEvent(currentStep);
                                result = out.getLeft();
                                newValuesRefs = out.getRight();
                                
                                //Turn arrays of primitives to arrays of Double
                                Boolean[] newValues = ArrayUtils.toObject(result);

                                //Turn List<BooleanValue> to Boolean[]
                                Boolean[] oldValues = values.stream().map(x->x.getValue()).collect(Collectors.toList()).toArray(Boolean[]::new);
        
                                // Inject -- if so defined in the specification -- the values before setting them
                                Boolean[] injected = inject(oldValues, scalarValueIndices, newValues, newValuesRefs);

                                //convert Boolean[] to List<BooleanValue>
                                values = Arrays.asList(injected).stream().map(x-> new BooleanValue(x)).collect(Collectors.toList());

                                logger.debug(String.format("The OUTPUT values %s", Arrays.toString(values.toArray())));
                                
                            }
                            //clear previous boolean outputs
                            currentOutput.booleanValues.clear();
                            boolean[] vals = ArrayUtils.toPrimitive(values.stream().map(x->x.getValue()).collect(Collectors.toList()).toArray(Boolean[]::new));
                            //put new values
                            for(int i=0; i < vals.length; i++){
                                currentOutput.booleanValues.put(scalarValueIndices[i], vals[i]);
                                //logger.debug(String.format("current output doubles %d, %f", scalarValueIndices[i], vals[i]));
                            }
                            ref.setValue(new ArrayValue<>(values));
                        }
        
        
                        return new IntegerValue(res.status.value);
        
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
        
                }));
                wrapperMembers.put("setInteger", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    checkArgLength(fcargs, 3);
                    long elementsToUse = getUint(fcargs.get(1));

                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), Optional.of(elementsToUse), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
                    int[] values = getArrayValue(fcargs.get(2), Optional.of(elementsToUse), IntegerValue.class).stream().mapToInt(IntegerValue::getValue).toArray();
                    
                    if(simulationDurationEvents.length != 0){
                        //Get data from the next event if any data
                        int[] result;
                        long[] newValuesRefs;

                        Pair<int[], long[]> out = getIntsFromEvent(currentStep+stepSize);
                        result = out.getLeft();
                        newValuesRefs = out.getRight();
                        
                        //Turn arrays of primitives to arrays of Boolean
                        Integer[] newValues = IntStream.of(result).boxed().collect(Collectors.toList()).toArray(Integer[]::new);

                        Integer[] oldValues = IntStream.of(values).boxed().collect(Collectors.toList()).toArray(Integer[]::new);

                        // Inject -- if so defined in the specification -- the values before setting them
                        Integer[] injected = inject(oldValues, scalarValueIndices, newValues, newValuesRefs);
                        values = ArrayUtils.toPrimitive(injected); 
                    }

                    logger.debug(String.format("The INPUT values %s", Arrays.toString(values)));
                    //clear previous double outputs
                    currentInput.integerValues.clear();

                    //put new values
                    for(int i=0; i < values.length; i++){
                        currentInput.integerValues.put(scalarValueIndices[i], values[i]);
                        //logger.debug(String.format("current input doubles %d, %f", scalarValueIndices[i], values[i]));
                    }

                    try {
                        Fmi2Status res = component.getModule().setIntegers(scalarValueIndices, values);
                        //logger.debug(String.format("setupInteger outcome %d", res.value));
                        return new IntegerValue(res.value);
                    } catch (InvalidParameterException | FmiInvalidNativeStateException e) {
                        throw new InterpreterException(e);
                    }
                }));
                wrapperMembers.put("getInteger", new FunctionValue.ExternalFunctionValue(fcargs -> {
        
                    checkArgLength(fcargs, 3);
        
                    if (!(fcargs.get(2) instanceof UpdatableValue)) {
                        throw new InterpreterException(FaultInjectRuntimeModule.errorMsg);
                    }
                    long elementsToUse = getUint(fcargs.get(1));
        
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), Optional.of(elementsToUse), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
        
        
                    try {
                        FmuResult<int[]> res = component.getModule().getInteger(scalarValueIndices);
        
                        if (res.status == Fmi2Status.OK) {
                            UpdatableValue ref = (UpdatableValue) fcargs.get(2);
        
                            List<IntegerValue> values =
                                    Arrays.stream(ArrayUtils.toObject(res.result)).map(i -> new IntegerValue(i)).collect(Collectors.toList());
        
                            //Inject after getting the values
                            if(simulationDurationEvents.length != 0){
                                //Get data from the next event if any
                                int[] result;
                                long[] newValuesRefs;
        
                                Pair<int[], long[]> out = getIntsFromEvent(currentStep);
                                result = out.getLeft();
                                newValuesRefs = out.getRight();
                                
                                //Turn arrays of primitives to arrays of Double
                                Integer[] newValues = IntStream.of(result).boxed().collect(Collectors.toList()).toArray(Integer[]::new);

                                //Turn List<RealValue> to Double[]
                                Integer[] oldValues = values.stream().map(x->x.getValue()).collect(Collectors.toList()).toArray(Integer[]::new);
        
                                // Inject -- if so defined in the specification -- the values before setting them
                                Integer[] injected = inject(oldValues, scalarValueIndices, newValues, newValuesRefs);

                                //convert Double[] to List<RealValue>
                                values = Arrays.asList(injected).stream().map(x-> new IntegerValue(x)).collect(Collectors.toList());

                                logger.debug(String.format("The OUTPUT values %s", Arrays.toString(values.toArray())));
                                
                            }
                            currentOutput.integerValues.clear();
                            int[] vals = ArrayUtils.toPrimitive(values.stream().map(x->x.getValue()).collect(Collectors.toList()).toArray(Integer[]::new));
                            //put new values
                            for(int i=0; i < vals.length; i++){
                                currentOutput.integerValues.put(scalarValueIndices[i], vals[i]);
                                //logger.debug(String.format("current output doubles %d, %f", scalarValueIndices[i], vals[i]));
                            }
                            ref.setValue(new ArrayValue<>(values));
                        }
        
        
                        return new IntegerValue(res.status.value);
        
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
        
        
                }));
                wrapperMembers.put("setString", new FunctionValue.ExternalFunctionValue(fcargs -> {

                    checkArgLength(fcargs, 3);
                    long elementsToUse = getUint(fcargs.get(1));

                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), Optional.of(elementsToUse), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
                    String[] values = getArrayValue(fcargs.get(2), Optional.of(elementsToUse), StringValue.class).stream().map(StringValue::getValue).collect(Collectors.toList())
                            .toArray(new String[]{});

                    if(simulationDurationEvents.length != 0){
                        //Get data from the next event if any data
                        String[] newValues;
                        long[] newValuesRefs;

                        Pair<String[], long[]> out = getStringsFromEvent(currentStep+stepSize);
                        newValues = out.getLeft();
                        newValuesRefs = out.getRight();
                                       
                        // Inject -- if so defined in the specification -- the values before setting them
                        values = inject(values, scalarValueIndices, newValues, newValuesRefs);
                    }

                    logger.debug(String.format("The INPUT values %s", Arrays.toString(values)));

                    try {
                        Fmi2Status res = component.getModule().setStrings(scalarValueIndices, values);
                        return new IntegerValue(res.value);
                    } catch (InvalidParameterException | FmiInvalidNativeStateException e) {
                        throw new InterpreterException(e);
                    }
        
                }));
                wrapperMembers.put("getString", new FunctionValue.ExternalFunctionValue(fcargs -> {
        
                    checkArgLength(fcargs, 3);
        
                    if (!(fcargs.get(2) instanceof UpdatableValue)) {
                        throw new InterpreterException(FaultInjectRuntimeModule.errorMsg);
                    }
                    long elementsToUse = getUint(fcargs.get(1));
        
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), Optional.of(elementsToUse), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
        
        
                    try {
                        FmuResult<String[]> res = component.getModule().getStrings(scalarValueIndices);
        
                        if (res.status == Fmi2Status.OK) {
                            UpdatableValue ref = (UpdatableValue) fcargs.get(2);
        
                            List<StringValue> values = Arrays.stream(res.result).map(StringValue::new).collect(Collectors.toList());
        
                            //Inject after getting the values
                            if(simulationDurationEvents.length != 0){
                                //Get data from the next event if any
                                String[] newValues;
                                long[] newValuesRefs;
        
                                Pair<String[], long[]> out = getStringsFromEvent(currentStep);
                                newValues = out.getLeft();
                                newValuesRefs = out.getRight();
                                
                                //Turn List<RealValue> to Double[]
                                String[] oldValues = values.stream().map(x->x.getValue()).collect(Collectors.toList()).toArray(String[]::new);
        
                                // Inject -- if so defined in the specification -- the values before setting them
                                String[] injected = inject(oldValues, scalarValueIndices, newValues, newValuesRefs);

                                //convert Double[] to List<RealValue>
                                values = Arrays.asList(injected).stream().map(x-> new StringValue(x)).collect(Collectors.toList());

                                logger.debug(String.format("The OUTPUT values %s", Arrays.toString(values.toArray())));
                                
                            }

                            ref.setValue(new ArrayValue<>(values));
                        }
        
        
                        return new IntegerValue(res.status.value);
        
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
        
        
                }));
                
                wrapperMembers.put("setupExperiment", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    logger.info("SETUPEXPERIMENT");

                    checkArgLength(fcargs, 5);
        
                    boolean toleranceDefined = getBool(fcargs.get(0));
                    double tolerance = getDouble(fcargs.get(1));
                    double startTime = getDouble(fcargs.get(2));
                    boolean stopTimeDefined = getBool(fcargs.get(3));
                    double stopTime = getDouble(fcargs.get(4));
                    try {
                        Fmi2Status res = component.getModule().setupExperiment(toleranceDefined, tolerance, startTime, stopTimeDefined, stopTime);
                        logger.info(String.format("setupExperiment outcome %d", res.value));
                        return new IntegerValue(res.value);
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
                }));
                wrapperMembers.put("enterInitializationMode", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    logger.info("ENTERINITIALIZATIONMODE");
                    checkArgLength(fcargs, 0);
                    try {
                        Fmi2Status res = component.getModule().enterInitializationMode();
                        //logger.debug(String.format("EnterInitializationMode outcome %d", res.value));
                        return new IntegerValue(res.value);
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
        
                }));
                wrapperMembers.put("exitInitializationMode", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    logger.info("EXITINITIALIZATIONMODE");
                    checkArgLength(fcargs, 0);
                    try {
                        Fmi2Status res = component.getModule().exitInitializationMode();
                        //logger.debug(String.format("ExitInitializationMode outcome %d", res.value));
                        return new IntegerValue(res.value);
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
                }));
                wrapperMembers.put("terminate", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    checkArgLength(fcargs, 0);
                    // Dump the contents of the events array (only the id) into an xml -- for test purposes
                    try {
                        dumpXmlFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        Fmi2Status res = component.getModule().terminate();
                        return new IntegerValue(res.value);
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
                }));
        
                wrapperMembers.put("setState", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    checkArgLength(fcargs, 1);
        
                    Value v = fcargs.get(0).deref();
        
                    if (v instanceof FmuComponentStateValue) {
                        try {
                            FmuComponentStateValue stateValue = (FmuComponentStateValue) v;
                            Fmi2Status res = component.getModule().setState(stateValue.getModule());
                            return new IntegerValue(res.value);
                        } catch (FmuInvocationException e) {
                            throw new InterpreterException(e);
                        }
                    }
        
                    throw new InterpreterException("Invalid value");
                }));
                wrapperMembers.put("getState", new FunctionValue.ExternalFunctionValue(fcargs -> {
        
                    checkArgLength(fcargs, 1);
        
                    if (!(fcargs.get(0) instanceof UpdatableValue)) {
                        throw new InterpreterException(FaultInjectRuntimeModule.errorMsg);
                    }
        
        
                    try {
        
                        FmuResult<IFmiComponentState> res = component.getModule().getState();
        
                        if (res.status == Fmi2Status.OK) {
                            UpdatableValue ref = (UpdatableValue) fcargs.get(0);
                            ref.setValue(new FmuComponentStateValue(res.result));
                        }
        
        
                        return new IntegerValue(res.status.value);
        
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
        
        
                }));
                wrapperMembers.put("freeState", new FunctionValue.ExternalFunctionValue(fcargs -> {
        
                    checkArgLength(fcargs, 1);
        
                    Value v = fcargs.get(0).deref();
        
                    if (v instanceof FmuComponentStateValue) {
                        try {
                            FmuComponentStateValue stateValue = (FmuComponentStateValue) v;
                            Fmi2Status res = component.getModule().freeState(stateValue.getModule());
                            return new IntegerValue(res.value);
                        } catch (FmuInvocationException e) {
                            throw new InterpreterException(e);
                        }
                    }
        
                    throw new InterpreterException("Invalid value");
        
        
                }));
        
                wrapperMembers.put("getRealStatus", new FunctionValue.ExternalFunctionValue(fcargs -> {
        
                    checkArgLength(fcargs, 2);
        
                    if (!(fcargs.get(1) instanceof UpdatableValue)) {
                        throw new InterpreterException(FaultInjectRuntimeModule.errorMsg);
                    }
        
                    Value kindValue = fcargs.get(0).deref();
        
                    if (!(kindValue instanceof IntegerValue)) {
                        throw new InterpreterException("Invalid kind value: " + kindValue);
                    }
        
                    int kind = ((IntegerValue) kindValue).getValue();
        
                    Fmi2StatusKind kindEnum = Arrays.stream(Fmi2StatusKind.values()).filter(v -> v.value == kind).findFirst().orElse(null);
        
                    try {
                        FmuResult<Double> res = component.getModule().getRealStatus(kindEnum);
        
                        if (res.status == Fmi2Status.OK) {
                            UpdatableValue ref = (UpdatableValue) fcargs.get(1);
        
                            ref.setValue(new RealValue(res.result));
                        }
        
        
                        return new IntegerValue(res.status.value);
        
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
        
        
                }));
        
                wrapperMembers.put("getRealOutputDerivatives", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    //   int getRealOutputDerivatives(long[] scalarValueIndices, UInt nvr, int[] order, ref double[] derivatives);
                    checkArgLength(fcargs, 4);
        
                    if (!(fcargs.get(3) instanceof UpdatableValue)) {
                        throw new InterpreterException(FaultInjectRuntimeModule.errorMsg);
                    }
        
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
        
                    int[] orders = getArrayValue(fcargs.get(2), NumericValue.class).stream().mapToInt(NumericValue::intValue).toArray();
        
        
                    try {
                        FmuResult<double[]> res = component.getModule().getRealOutputDerivatives(scalarValueIndices, orders);
        
                        if (res.status == Fmi2Status.OK) {
                            UpdatableValue ref = (UpdatableValue) fcargs.get(3);
        
                            List<RealValue> values = Arrays.stream(ArrayUtils.toObject(res.result)).map(d -> new RealValue(d)).collect(Collectors.toList());
        
                            ref.setValue(new ArrayValue<>(values));
                        }
        
        
                        return new IntegerValue(res.status.value);
        
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
        
                }));
                wrapperMembers.put("setRealInputDerivatives", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    // int setRealInputDerivatives(UInt[] scalarValueIndices, UInt nvr, int[] order, ref real[] derivatives);
                    checkArgLength(fcargs, 4);
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
        
                    int[] orders = getArrayValue(fcargs.get(2), NumericValue.class).stream().mapToInt(NumericValue::intValue).toArray();
        
                    double[] values = getArrayValue(fcargs.get(3), RealValue.class).stream().mapToDouble(RealValue::getValue).toArray();
        
                    try {
                        Fmi2Status res = component.getModule().setRealInputDerivatives(scalarValueIndices, orders, values);
                        return new IntegerValue(res.value);
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
        
                }));
                
                simulationDurationEvents = createDurationEvents(faultSpecFile, wrapperID);
                return new WrapperFmuComponentValue(component, wrapperMembers, wrapperID, fmu);
            }
        }

        public FaultInjectModule(String path) {
            super(createMembers(path), path);
        }

        /*
         * Thoughts on wrapper implementation: 1 implement a proxy implementation that
         * intercepts all calls before and after the FMI call. 2. implement a caching
         * implementation using before or after to do the caching. 3. implement an
         * alternative behavior based on the cache and rule set
         */

        private static Map<String, Value> createMembers(String faultSpecFile) {
            Map<String, Value> members = new HashMap<>();
            members.put("faultInject", new FunctionValue.ExternalFunctionValue(fargs -> {

                List<Value> args = fargs.stream().map(Value::deref).collect(Collectors.toList());

                checkArgLength(args, 3);

                Value fmuVal = args.get(0);
                Value compVal = args.get(1);
                Value idVal = args.get(2);

                FmuValue fmu = (FmuValue) fmuVal; // What about this?
                FmuComponentValue comp = (FmuComponentValue) compVal;
                String id = ((StringValue) idVal).getValue();
                logger.info("Creating wrapper with id: %s", id);
                
                // TODO create wrapper and configure it using the constraints and the id. Its
                // comp that needs to be wrapped and returned but for now
                // we just leave it as is. Remember if we need to do cross instance handling
                // also using observe we need to store this wrapper
                // locally and allow it access to the other wrappers data

                //To make the wrapper look at FmiInterpreter
                // Create and return wrapper
                WrapperFmuComponentValue wrapper = new WrapperFmuComponentValue(comp, null , id, fmu);
                return wrapper.getWrapperComponent(comp, id, fmu, faultSpecFile);
          
            }));

            members.put("observe", new FunctionValue.ExternalFunctionValue(fargs -> {
                List<Value> args = fargs.stream().map(Value::deref).collect(Collectors.toList());

                checkArgLength(args, 3);

                Value fmuVal = args.get(0);
                Value compVal = args.get(1);
                Value idVal = args.get(2);

                FmuValue fmu = (FmuValue) fmuVal;
                FmuComponentValue comp = (FmuComponentValue) compVal;
                String id = ((StringValue) idVal).getValue();

                //TODO create wrapper and so we can record all communication but without changing the behaviour. It's the wrapper that needs to be
                // returned but for now we just ignore all and return comp

                // create wrapper object
                //WrapperFmuComponentValue wrapper = new WrapperFmuComponentValue(comp);
                
                //To make the wrapper look at FmiInterpreter
                //return comp;
                return comp;

            }));


            members.put("returnFmuComponentValue", new FunctionValue.ExternalFunctionValue(fargs -> {
                List<Value> args = fargs.stream().map(Value::deref).collect(Collectors.toList());

                checkArgLength(args, 1);

                Value wrapperCompVal = args.get(0);

                WrapperFmuComponentValue wrapperComp = (WrapperFmuComponentValue) wrapperCompVal;

                return wrapperComp.wrappedComponent;

            }));


            return members;
        }
    }



    @Override
    public Either<Exception, Value> instantiate(List<Value> list) {

        List<Value> args = list.stream().map(Value::deref).collect(Collectors.toList());

        if (args.isEmpty() || !(args.get(0) instanceof StringValue)) {
            return Either.left(new Exception("FaultInject must be instantiated with a constraint path given as a string"));
        }
        
        //config path as arg literal string
        return Either.right(new FaultInjectModule(((StringValue) args.get(0)).getValue()));
    }

    @Override
    public void destroy(Value value) {
        //throw new UnsupportedOperationException("destroy function to be implemented");
        
    }

    @Override
    public InputStream getMablModule() {
        return this.getClass().getResourceAsStream("org/intocps/maestro/faultinject/FaultInject.mabl");
    }
}
