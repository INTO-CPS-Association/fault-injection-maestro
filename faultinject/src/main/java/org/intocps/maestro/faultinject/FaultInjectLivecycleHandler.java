package org.intocps.maestro.faultinject;


import com.spencerwi.either.Either;
import org.intocps.maestro.faultinject.values.FaultInjectModuleValue;
import org.intocps.maestro.interpreter.api.IValueLifecycleHandler;
import org.intocps.maestro.interpreter.values.StringValue;
import org.intocps.maestro.interpreter.values.Value;

import java.io.File;
import java.io.InputStream;
import java.util.List;

@IValueLifecycleHandler.ValueLifecycle(name = "FaultInject")
public class FaultInjectLivecycleHandler implements IValueLifecycleHandler {
    private final File workingDirectory;

    public FaultInjectLivecycleHandler(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public Either<Exception, Value> instantiate(List<Value> list) {

        List<Value> args = list.stream().map(Value::deref).toList();

        if (args.isEmpty() || !(args.get(0) instanceof StringValue)) {
            return Either.left(new Exception("FaultInject must be instantiated with a constraint path given as a string"));
        }

        //config path as arg literal string
        return Either.right(new FaultInjectModuleValue(this.workingDirectory,((StringValue) args.get(0)).getValue()));
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
