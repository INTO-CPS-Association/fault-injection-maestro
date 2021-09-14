#### Development Environment
You need Java 11 and maven 3.6 to build the project (??).
The project can be built from CLI using the maven commands.
```bash
mvn clean
mvn compile
mvn test
```

MaBL file for the SimpleTest.java: SmallFaultInjectTest.mabl

To run only one test: mvn test -Dtest=TestClassName#testMethod -DfailIfNoTests=false, e.g.

```bash
$ mvn test -Dtest=injectCorrectnessTest#test -DfailIfNoTests=false
```

The `-DfailIfNoTests`is set to `false`, to avoid an error due to no tests in faultinject folder.

#### Water-tank Co-simulation Case-Study
*NOTE: Download the code with tag icsrs21

The co-simulation for the watertank, with and w/o fault injection (FI), can be performed by running the test: WaterTankTest.java
The corresponding MaBL file: watertank-casestudy.mabl
Events can be changed at faultInjectSpecificationWaterTank.xml

To run with FI leave faultInjectSpecificationWaterTank.xml as is.
To run w/o FI remove the events in faultInjectSpecificationWaterTank.xml. The file should look like:

```xml
<events>

</events>
```

#### RBMQ Co-simulation Case-Study
*NOTE: Download the code with tag icsrs21

The relevant files are in rbmq_example.


#### Development Notes
* Tests need to be added for xml files with multiple variables of the same type in one event (one-shot, or duration)
* implement a function than cleans up the events array.

#### How to use
It is possible to define one-time events e.g.
```xml
<event id="2" when="t=9.0">
    <variable valRef="3" type="real" newVal="50.0" vars=""/>
</event>
```
Note that all variables that should be affected at that time-step can be included within the same one-time event.

It is also possible to define events with a duration. Different variables might be injected for different periods as such, events with different and overlapping durations
can be defined, e.g.
```xml
<event id="3" when="(t&gt;=0.2) &amp; (t&lt;0.4)">
    <variable valRef="3" type="real" newVal="57.0" vars=""/>
</event>
<event id="4" when="(t&gt;=0.3) &amp; (t&lt;0.6)">
    <variable valRef="4" type="real" newVal="60.0" vars=""/>
</event>
```

Should there be multiple overlapping events with duration that target the same variable, the latter will be injected with the value in the event defined last in the xml file. 

```newVal``` can also be a mathematical expression, e.g.
```xml
<event id="2" when="t=9.0">
    <variable valRef="3" type="real" newVal="50.0 + t" vars=""/>
</event>
```
where t will be resolved to the simulation step the event applies to.

It might also be needed to calculate the injected valued based on the inputs or outputs of the fmu. In this case, these can be added as variables following this naming convention:
```var_{valueref}```. In addition this has to be added in vars, where the separate variable names are separated by comma ```,```. E.g., assume we want to include an input with value reference 3, and an output with value reference 4, then the event definition would look like:
```xml
<event id="2" when="t=9.0">
    <variable valRef="3" type="real" newVal="50.0 + t + var_3 * var_4" vars="var_3,var_4"/>
</event>
```
In the same way these expression can be defined for duration events. 

Expressions can be defined only for the real, int, and bool types. In case of injection of strings, the value assigned to ```newVal``` is going to copied directly to the input/output.
Injection for type int functions in the same way as for double. Note that double values will be used in the calculation if given as such in the expression, however the final value to which an input/output is set will be rounded to an int.

Expressions for type bool accept the following operators: not -> "~", and -> "&", or -> "|". A boolean expression can be used on all fmu variables, inputs/outputs.


Additionally, the when condition can be expanded to include conditions on other inputs/outputs of the fmu. These can be included in other, and the variables need to be specified in vars as well. The expression in other will not be evaluated for time step equal to 0.0.

```xml
<event id="1" when="(t&gt;=0.2) &amp; (t&lt;0.4)" other="var_2 &gt; 0" vars="var_2,">
    <variable valRef="2" type="real" newVal="t+36" vars="var_2," />
</event>
```
