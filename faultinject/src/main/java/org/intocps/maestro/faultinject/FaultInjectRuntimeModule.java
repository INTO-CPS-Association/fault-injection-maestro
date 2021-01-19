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

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@IValueLifecycleHandler.ValueLifecycle(name = "FaultInject")
public class FaultInjectRuntimeModule implements IValueLifecycleHandler {

    static final Logger logger = LoggerFactory.getLogger(FaultInjectRuntimeModule.class);

    public static class FaultInjectModule extends ExternalModuleValue<String> {

        public static class WrapperFmuComponentValue extends ExternalModuleValue<IFmiComponent>{
            FmuComponentValue wrappedComponent;
            String wrapperID;
            String eventsSpecificationFile;
            private static double currentStep = 0.0;
            // TODO The spec below is to be removed. The specification will come in through a file.
            private static double stepForEvent = 0.6;

            public WrapperFmuComponentValue(FmuComponentValue component, Map<String, Value> wrapperMembers, String wrapperID) {
                super(wrapperMembers, component.getModule());
                this.wrappedComponent = component;
                this.wrapperID = wrapperID;
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
        
            static <T extends Value> List<T> getArrayValue(Value value, Class<T> clz) {

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
        
                    return array.getValues().stream().map(Value::deref).map(clz::cast).collect(Collectors.toList());
                }
                throw new InterpreterException("Value is not an array");
        
        
            }

            // Needs access to the values of input, the value refs, and the injection specification file
            private static double[] injectDoubles(double[] values){
                logger.warn("inject method");
                // TODO check whether it's time to inject an event from a config file

                // TODO If smth needs to be injected, update the values variable

                if(currentStep >= stepForEvent){
                    logger.warn("Injecting");

                }
                return values;
            }

            private static WrapperFmuComponentValue getWrapperComponent(FmuComponentValue component, String wrapperID) {
                logger.warn("creating members");
                Map<String, Value> wrapperMembers = new HashMap<>();

                wrapperMembers.put("setDebugLogging", new FunctionValue.ExternalFunctionValue(fcargs -> {

                    checkArgLength(fcargs, 3);
        
                    boolean debugLogginOn = getBool(fcargs.get(0));
                    //                        int arraySize = getInteger(fcargs.get(1));
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
        
                    try {
                        Fmi2Status res = component.getModule().doStep(currentCommunicationPoint, communicationStepSize, noSetFMUStatePriorToCurrentPoint);
                        logger.warn(String.format("doStep outcome %d", res.value));
                        return new IntegerValue(res.value);
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
                }));
                wrapperMembers.put("setReal", new FunctionValue.ExternalFunctionValue(fcargs -> {

                    checkArgLength(fcargs, 3);
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
                    double[] values = getArrayValue(fcargs.get(2), RealValue.class).stream().mapToDouble(RealValue::getValue).toArray();
                    logger.warn(String.format("The values to set %s", Arrays.toString(values)));

                    // Inject -- if so defined in the specification -- the values before setting them
                    values = injectDoubles(values);

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
                        throw new InterpreterException("value not a reference value");
                    }
        
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
        
                    try {
                        FmuResult<double[]> res = component.getModule().getReal(scalarValueIndices);
                        logger.warn(String.format("getReal outcome: %d", res.status.value));
        
                        if (res.status == Fmi2Status.OK) {
                            UpdatableValue ref = (UpdatableValue) fcargs.get(2);
                            logger.warn(String.format("getReal outcome %d", res.status.value));
        
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
        
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
                    boolean[] values = ArrayUtils.toPrimitive(
                            getArrayValue(fcargs.get(2), BooleanValue.class).stream().map(BooleanValue::getValue).collect(Collectors.toList())
                                    .toArray(new Boolean[]{}));
        
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
                        throw new InterpreterException("value not a reference value");
                    }
        
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
        
        
                    try {
                        FmuResult<boolean[]> res = component.getModule().getBooleans(scalarValueIndices);
                        logger.warn(String.format("getBoolean outcome %d", res.status.value));
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
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
                    int[] values = getArrayValue(fcargs.get(2), IntegerValue.class).stream().mapToInt(IntegerValue::getValue).toArray();
        
                    try {
                        Fmi2Status res = component.getModule().setIntegers(scalarValueIndices, values);
                        return new IntegerValue(res.value);
                    } catch (InvalidParameterException | FmiInvalidNativeStateException e) {
                        throw new InterpreterException(e);
                    }
                }));
                wrapperMembers.put("getInteger", new FunctionValue.ExternalFunctionValue(fcargs -> {
        
                    checkArgLength(fcargs, 3);
        
                    if (!(fcargs.get(2) instanceof UpdatableValue)) {
                        throw new InterpreterException("value not a reference value");
                    }
        
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
        
        
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
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
                    String[] values = getArrayValue(fcargs.get(2), StringValue.class).stream().map(StringValue::getValue).collect(Collectors.toList())
                            .toArray(new String[]{});
        
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
                        throw new InterpreterException("value not a reference value");
                    }
        
                    long[] scalarValueIndices = getArrayValue(fcargs.get(0), NumericValue.class).stream().mapToLong(NumericValue::longValue).toArray();
        
        
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
                    logger.warn("setupExperiment");

                    checkArgLength(fcargs, 5);
        
                    boolean toleranceDefined = getBool(fcargs.get(0));
                    double tolerance = getDouble(fcargs.get(1));
                    double startTime = getDouble(fcargs.get(2));
                    boolean stopTimeDefined = getBool(fcargs.get(3));
                    double stopTime = getDouble(fcargs.get(4));
                    try {
                        Fmi2Status res = component.getModule().setupExperiment(toleranceDefined, tolerance, startTime, stopTimeDefined, stopTime);
                        logger.warn(String.format("setupExperiment outcome %d", res.value));
                        return new IntegerValue(res.value);
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
                }));
                wrapperMembers.put("enterInitializationMode", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    logger.warn("EnterInitializationMode");
                    checkArgLength(fcargs, 0);
                    try {
                        Fmi2Status res = component.getModule().enterInitializationMode();
                        logger.warn(String.format("EnterInitializationMode outcome %d", res.value));
                        return new IntegerValue(res.value);
                    } catch (FmuInvocationException e) {
                        throw new InterpreterException(e);
                    }
        
                }));
                wrapperMembers.put("exitInitializationMode", new FunctionValue.ExternalFunctionValue(fcargs -> {
                    logger.warn("ExitInitializationMode");
                    checkArgLength(fcargs, 0);
                    try {
                        Fmi2Status res = component.getModule().exitInitializationMode();
                        logger.warn(String.format("ExitInitializationMode outcome %d", res.value));
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
                        throw new InterpreterException("value not a reference value");
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
                        throw new InterpreterException("value not a reference value");
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
                        throw new InterpreterException("value not a reference value");
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
                
                return new WrapperFmuComponentValue(component, wrapperMembers, wrapperID);
            }
        }

        public FaultInjectModule(String path) {
            super(createMembers(), path);
        }

        /*
         * Thoughts on wrapper implementation: 1 implement a proxy implementation that
         * intercepts all calls before and after the FMI call. 2. implement a caching
         * implementation using before or after to do the caching. 3. implement an
         * alternative behavior based on the cache and rule set
         */

        private static Map<String, Value> createMembers() {
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
                //return comp;
                // Create and return wrapper 
                return WrapperFmuComponentValue.getWrapperComponent(comp, id);
          
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
