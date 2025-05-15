package org.intocps.maestro.faultinject.values;

import org.intocps.maestro.interpreter.values.*;
import org.intocps.maestro.interpreter.values.fmi.FmuComponentValue;
import org.intocps.maestro.interpreter.values.fmi.FmuValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class FaultInjectModuleValue extends ExternalModuleValue<String> {
    static final Logger logger = LoggerFactory.getLogger(FaultInjectModuleValue.class);

    public FaultInjectModuleValue(File workingDirectory, String path) {
        super(createMembers(workingDirectory, path), path);
    }

    /*
     * Thoughts on wrapper implementation: 1 implement a proxy implementation that
     * intercepts all calls before and after the FMI call. 2. implement a caching
     * implementation using before or after to do the caching. 3. implement an
     * alternative behavior based on the cache and rule set
     */

    private static Map<String, Value> createMembers(final File workingDirectory, String faultSpecFile) {
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
            WrapperFmuComponentValue wrapper = new WrapperFmuComponentValue(workingDirectory,comp, null, id, fmu);
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
