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

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.lang.StackWalker.Option;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;


@IValueLifecycleHandler.ValueLifecycle(name = "FaultInject")
public class FaultInjectRuntimeModule implements IValueLifecycleHandler {

    static final Logger logger = LoggerFactory.getLogger(FaultInjectRuntimeModule.class);
    static String errorMsg = "value not a reference value";

    public static class FaultInjectModule extends ExternalModuleValue<String> {

        public static class WrapperFmuComponentValue extends ExternalModuleValue<IFmiComponent> {
            FmuComponentValue wrappedComponent;
            String wrapperID;
            FmuValue fmu;
            String eventsSpecificationFile;
            private static double currentStep = 0.0;
            private static double stepSize = 0.1;

            private static Event[] simulationEvents;

            public WrapperFmuComponentValue(FmuComponentValue component, Map<String, Value> wrapperMembers,
                    String wrapperID, FmuValue fmu) {
                super(wrapperMembers, component.getModule());
                this.wrappedComponent = component;
                this.wrapperID = wrapperID;
                this.fmu = fmu;
            }

            public static Event[] createEvents(String faultSpecFile){
                Event[] simuEvents = {};
                try {
                    boolean verbose = true;
                    simuEvents = Event.getEvents(faultSpecFile, verbose);
                    Event.printEvent(simuEvents);
                    return simuEvents;
                } catch (NumberFormatException | NullPointerException | SAXException | IOException
                        | ParserConfigurationException e) {
                    logger.error("Something went terribly wrong when creating the events");
                    e.printStackTrace();
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

            public static Pair<double[], long[]> getDoublesFromEvent(){
                //Check the first event in simulationEvents
                double[] dv = {};
                long[] dvr = {};
                Pair<double[], long[]> result = Pair.of(dv,dvr);
                if(Math.abs(currentStep + stepSize - simulationEvents[0].timePoint) <= 0.0000001){//comparing doubles...
                    logger.warn("Supposedly injecting");
                    result = Pair.of(simulationEvents[0].doubleValues, simulationEvents[0].doubleValuesRefs);
                }
                return result;
            }

            public static Pair<int[], long[]> getIntsFromEvent(){
                //Check the first event in simulationEvents
                int[] dv = {};
                long[] dvr = {};
                Pair<int[], long[]> result = Pair.of(dv,dvr);
                if(Math.abs(currentStep + stepSize - simulationEvents[0].timePoint) <= 0.0000001){//comparing doubles...
                    logger.warn("Supposedly injecting");
                    result = Pair.of(simulationEvents[0].intValues, simulationEvents[0].intValuesRefs);
                }
                return result;
            }

            public static Pair<boolean[], long[]> getBooleansFromEvent(){
                //Check the first event in simulationEvents
                boolean[] dv = {};
                long[] dvr = {};
                Pair<boolean[], long[]> result = Pair.of(dv,dvr);
                if(Math.abs(currentStep + stepSize - simulationEvents[0].timePoint) <= 0.0000001){//comparing doubles...
                    logger.warn("Supposedly injecting");
                    result = Pair.of(simulationEvents[0].boolValues, simulationEvents[0].boolValuesRefs);
                }
                return result;
            }

            public static Pair<String[], long[]> getStringsFromEvent(){
                //Check the first event in simulationEvents
                String[] dv = {};
                long[] dvr = {};
                Pair<String[], long[]> result = Pair.of(dv,dvr);
                if(Math.abs(currentStep + stepSize - simulationEvents[0].timePoint) <= 0.0000001){//comparing doubles...
                    logger.warn("Supposedly injecting");
                    result = Pair.of(simulationEvents[0].stringValues, simulationEvents[0].stringValuesRefs);
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
                    throw new WrapperException("size mismatch between value and valueRef array");
                }

                //Iterate over valueRefs
                for (long vr : valueRefs) { 
                    //if an element of value refs is present in newValuesRefs, set the new value in values from newValues
                    int idx = ArrayUtils.indexOf(newValuesRefs, vr);
                    if(idx!=-1){
                        int idy = ArrayUtils.indexOf(valueRefs, vr);
                        if(idy!=-1){
                            logger.warn("Injecting");
                            values[idy] = newValues[idx];
                        }
                    }
                } 
                return values;
            }

            private static WrapperFmuComponentValue getWrapperComponent(FmuComponentValue component, String wrapperID, FmuValue fmu, String faultSpecFile) {
                logger.warn("creating members");
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
                    
                    logger.warn(String.format("doStep at time %f", currentCommunicationPoint));
                    // Keep track of the current timestep in which the fmu is. Needed by the inject functions. 
                    currentStep = currentCommunicationPoint;
                    // Cleanup the events array
                    simulationEvents = Event.cutArrayOfEvents(simulationEvents, currentStep);
        
                    try {
                        Fmi2Status res = component.getModule().doStep(currentCommunicationPoint, communicationStepSize, noSetFMUStatePriorToCurrentPoint);
                        //logger.warn(String.format("doStep outcome %d", res.value));
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
                    
                    logger.warn(String.format("The values to set %s for time %f", Arrays.toString(values), currentStep+stepSize));

                    logger.warn(String.format("scalarValueIndices %s", Arrays.toString(scalarValueIndices)));
                    
                    if(simulationEvents.length != 0){
                        //Get data from the next event if any
                        double[] result;
                        long[] newValuesRefs;

                        Pair<double[], long[]> out = getDoublesFromEvent();
                        result = out.getLeft();
                        newValuesRefs = out.getRight();
                        
                        //Turn arrays of primitives to arrays of Double
                        Double[] newValues = DoubleStream.of(result).boxed().collect(Collectors.toList()).toArray(Double[]::new);
                        Double[] oldValues = DoubleStream.of(values).boxed().collect(Collectors.toList()).toArray(Double[]::new);

                        // Inject -- if so defined in the specification -- the values before setting them
                        Double[] injected = inject(oldValues, scalarValueIndices, newValues, newValuesRefs);
                        values = ArrayUtils.toPrimitive(injected);
                    }
                    
                    logger.warn(String.format("The values %s", Arrays.toString(values)));

                    try {
                        Fmi2Status res = component.getModule().setReals(scalarValueIndices, values);
                        logger.warn(String.format("setupReal outcome %d", res.value));
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
                        logger.warn(String.format("getReal outcome: %d", res.status.value));
        
                        if (res.status == Fmi2Status.OK) {
                            UpdatableValue ref = (UpdatableValue) fcargs.get(2);
                            //logger.warn(String.format("getReal outcome %d", res.status.value));
        
                            List<RealValue> values = Arrays.stream(ArrayUtils.toObject(res.result)).map(d -> new RealValue(d)).collect(Collectors.toList());
        
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
                    
                    if(simulationEvents.length != 0){
                        //Get data from the next event if any
                        boolean[] result;
                        long[] newValuesRefs;

                        Pair<boolean[], long[]> out = getBooleansFromEvent();
                        result = out.getLeft();
                        newValuesRefs = out.getRight();
                        
                        //Turn arrays of primitives to arrays of Boolean
                        Boolean[] newValues = Arrays.stream(ArrayUtils.toObject(result)).map(BooleanValue::new).collect(Collectors.toList()).toArray(Boolean[]::new);

                        Boolean[] oldValues = Arrays.stream(ArrayUtils.toObject(values)).map(BooleanValue::new).collect(Collectors.toList()).toArray(Boolean[]::new);

                        // Inject -- if so defined in the specification -- the values before setting them
                        Boolean[] injected = inject(oldValues, scalarValueIndices, newValues, newValuesRefs);
                        values = ArrayUtils.toPrimitive(injected); 
                    }
                    
                    logger.warn(String.format("The values %s", Arrays.toString(values)));
                    
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
                        //logger.warn(String.format("getBoolean outcome %d", res.status.value));
                        if (res.status == Fmi2Status.OK) {
                            UpdatableValue ref = (UpdatableValue) fcargs.get(2);
        
                            List<BooleanValue> values = Arrays.stream(ArrayUtils.toObject(res.result)).map(BooleanValue::new).collect(Collectors.toList());
        
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
                    
                    if(simulationEvents.length != 0){
                        //Get data from the next event if any data
                        int[] result;
                        long[] newValuesRefs;

                        Pair<int[], long[]> out = getIntsFromEvent();
                        result = out.getLeft();
                        newValuesRefs = out.getRight();
                        
                        //Turn arrays of primitives to arrays of Boolean
                        Integer[] newValues = IntStream.of(result).boxed().collect(Collectors.toList()).toArray(Integer[]::new);

                        Integer[] oldValues = IntStream.of(values).boxed().collect(Collectors.toList()).toArray(Integer[]::new);

                        // Inject -- if so defined in the specification -- the values before setting them
                        Integer[] injected = inject(oldValues, scalarValueIndices, newValues, newValuesRefs);
                        values = ArrayUtils.toPrimitive(injected); 
                    }

                    logger.warn(String.format("The values %s", Arrays.toString(values)));

                    try {
                        Fmi2Status res = component.getModule().setIntegers(scalarValueIndices, values);
                        logger.warn(String.format("setupInteger outcome %d", res.value));
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

                    if(simulationEvents.length != 0){
                        //Get data from the next event if any data
                        String[] newValues;
                        long[] newValuesRefs;

                        Pair<String[], long[]> out = getStringsFromEvent();
                        newValues = out.getLeft();
                        newValuesRefs = out.getRight();
                                       
                        // Inject -- if so defined in the specification -- the values before setting them
                        values = inject(values, scalarValueIndices, newValues, newValuesRefs);
                    }

                    logger.warn(String.format("The values %s", Arrays.toString(values)));

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
        
                            ref.setValue(new ArrayValue<>(values));
                        }
        
        
                        return new IntegerValue(res.status.value);
        
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
        
        
                }));
                
                wrapperMembers.put("setupExperiment", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    logger.warn("SETUPEXPERIMETN");

                    checkArgLength(fcargs, 5);
        
                    boolean toleranceDefined = getBool(fcargs.get(0));
                    double tolerance = getDouble(fcargs.get(1));
                    double startTime = getDouble(fcargs.get(2));
                    boolean stopTimeDefined = getBool(fcargs.get(3));
                    double stopTime = getDouble(fcargs.get(4));
                    try {
                        Fmi2Status res = component.getModule().setupExperiment(toleranceDefined, tolerance, startTime, stopTimeDefined, stopTime);
                        //logger.warn(String.format("setupExperiment outcome %d", res.value));
                        return new IntegerValue(res.value);
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
                }));
                wrapperMembers.put("enterInitializationMode", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    logger.warn("ENTERINITIALIZATIONMODE");
                    checkArgLength(fcargs, 0);
                    try {
                        Fmi2Status res = component.getModule().enterInitializationMode();
                        //logger.warn(String.format("EnterInitializationMode outcome %d", res.value));
                        return new IntegerValue(res.value);
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
        
                }));
                wrapperMembers.put("exitInitializationMode", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    logger.warn("EXITINITIALIZATIONMODE");
                    checkArgLength(fcargs, 0);
                    try {
                        Fmi2Status res = component.getModule().exitInitializationMode();
                        //logger.warn(String.format("ExitInitializationMode outcome %d", res.value));
                        return new IntegerValue(res.value);
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
                }));
                wrapperMembers.put("terminate", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    checkArgLength(fcargs, 0);
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
                
                simulationEvents = createEvents(faultSpecFile);
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
                
                // TODO create wrapper and configure it using the constraints and the id. Its
                // comp that needs to be wrapped and returned but for now
                // we just leave it as is. Remember if we need to do cross instance handling
                // also using observe we need to store this wrapper
                // locally and allow it access to the other wrappers data

                //To make the wrapper look at FmiInterpreter
                // Create and return wrapper 
                return WrapperFmuComponentValue.getWrapperComponent(comp, id, fmu, faultSpecFile);
          
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
        throw new UnsupportedOperationException("destroy function to be implemented");
    }

    @Override
    public InputStream getMablModule() {
        return this.getClass().getResourceAsStream("org/intocps/maestro/faultinject/FaultInject.mabl");
    }
}
