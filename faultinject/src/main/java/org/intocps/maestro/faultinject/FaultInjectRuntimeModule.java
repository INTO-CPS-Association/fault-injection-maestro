package org.intocps.maestro.faultinject;

import com.spencerwi.either.Either;
import org.intocps.maestro.interpreter.api.IValueLifecycleHandler;
import org.intocps.maestro.interpreter.values.ExternalModuleValue;
import org.intocps.maestro.interpreter.values.FunctionValue;
import org.intocps.maestro.interpreter.values.StringValue;
import org.intocps.maestro.interpreter.values.Value;
import org.intocps.maestro.interpreter.values.fmi.FmuComponentValue;
import org.intocps.maestro.interpreter.values.fmi.FmuValue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@IValueLifecycleHandler.ValueLifecycle(name = "FaultInject")
public class FaultInjectRuntimeModule implements IValueLifecycleHandler {

    public static class FaultInjectModule extends ExternalModuleValue<String> {

        public FaultInjectModule(String path) {
            super(createMembers(), path);
        }

        /*
         * Thoughts on wrapper implementation:
         *  1 implement a proxy implementation that intercepts all calls before and after the FMI call.
         *  2. implement a caching implementation using before or after to do the caching.
         *  3. implement an alternative behavior based on the cache and
         * rule set*/

        private static Map<String, Value> createMembers() {
            Map<String, Value> members = new HashMap<>();

            members.put("faultInject", new FunctionValue.ExternalFunctionValue(fargs -> {
                List<Value> args = fargs.stream().map(Value::deref).collect(Collectors.toList());

                checkArgLength(args, 3);

                Value fmuVal = args.get(0);
                Value compVal = args.get(1);
                Value idVal = args.get(2);

                FmuValue fmu = (FmuValue) fmuVal;
                FmuComponentValue comp = (FmuComponentValue) compVal;
                String id = ((StringValue) idVal).getValue();

                //TODO create wrapper and configure it using the constraints and the id. Its comp that needs to be wrapped and returned but for now
                // we just leave it as is. Remember if we need to do cross instance handling also using observe we need to store this wrapper
                // locally and allow it access to the other wrappers data

                //To make the wrapper look at FmiInterpreter
                return comp;

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
                return comp;

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

    }

    @Override
    public InputStream getMablModule() {
        return this.getClass().getResourceAsStream("org/intocps/maestro/faultinject/FaultInject.mabl");
    }
}
