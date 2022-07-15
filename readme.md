#### Development Environment
You need Java 11 and maven 3.6 to build the project.
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

The `-DfailIfNoTests`is set to `false`, to avoid an error due to no tests in the faultinject folder.

#### Water-tank Co-simulation Case-Study
*NOTE: Download the code with tag icsrs21

The co-simulation for the watertank, with and w/o fault injection (FI), can be performed by running the test: ```WaterTankTest.java```
The corresponding MaBL file: ```watertank-casestudy.mabl```
Events can be changed at ```faultInjectSpecificationWaterTank.xml```

To run with FI leave ```faultInjectSpecificationWaterTank.xml``` as is.
To run w/o FI remove the events in ```faultInjectSpecificationWaterTank.xml```. The file should look like:

```xml
<events>

</events>
```

#### RBMQ Co-simulation Case-Study
*NOTE: Download the code with tag icsrs21

The relevant files are in rbmq_example. Run test ````rbmqMonitorTest````


#### Development Notes
* Tests need to be added for xml files with multiple variables of the same type in one event (one-shot, or duration)

#### How to use
It is possible to define one-time events e.g.
```xml
<event id="wrapper_id_#no" when="t=9.0">
    <variable valRef="3" type="real" newVal="50.0" vars=""/>
</event>
```
Note that all variables that should be affected at that time-step can be included within the same one-time event.

It is also possible to define events with a duration. Different variables might be injected for different periods as such, events with different and overlapping durations
can be defined, e.g.
```xml
<events>
    <event id="wrapper_id_#no" when="(t&gt;=0.2) &amp; (t&lt;0.4)">
        <variable valRef="3" type="real" newVal="57.0" vars=""/>
    </event>
    <event id="wrapper_id_#no" when="(t&gt;=0.3) &amp; (t&lt;0.6)">
        <variable valRef="4" type="real" newVal="60.0" vars=""/>
    </event>
</events>
```

Should there be multiple overlapping events with duration that target the same variable, the latter will be injected with the value in the event defined last in the xml file. 

```newVal``` can also be a mathematical expression, e.g.
```xml
<event id="wrapper_id_#no" when="t=9.0">
    <variable valRef="3" type="real" newVal="50.0 + t" vars=""/>
</event>
```
where t will be resolved to the simulation step the event applies to.

It might also be needed to calculate the injected valued based on the inputs or outputs of the fmu. In this case, these can be added as variables following this naming convention:
```var_{valueref}```. In addition this has to be added in vars, where the separate variable names are separated by comma ```,```. E.g., assume we want to include an input with value reference 3, and an output with value reference 4, then the event definition would look like:
```xml
<event id="wrapper_id_#no" when="t=9.0">
    <variable valRef="3" type="real" newVal="50.0 + t + var_3 * var_4" vars="var_3,var_4"/>
</event>
```
In the same way these expressions can be defined for duration events. 

Expressions can be defined only for the real, int, and bool types. In case of injection of strings, the value assigned to ```newVal``` is going to be copied directly to the input/output.
Injection for type int functions in the same way as for double. Note that double values will be used in the calculation if given as such in the expression, however the final value to which an input/output is set will be rounded to an int.

Expressions for type bool accept the following operators: not -> "~", and -> "&", or -> "|". A boolean expression can be used on all fmu variables, inputs/outputs.


Additionally, the when condition can be expanded to include conditions on other inputs/outputs of the fmu. These can be included in other, and the variables need to be specified in vars as well. The expression in other will not be evaluated for time step equal to 0.0.

```xml
<event id="1" when="(t&gt;=0.2) &amp; (t&lt;0.4)" other="var_2 &gt; 0" vars="var_2,">
    <variable valRef="2" type="real" newVal="t+36" vars="var_2," />
</event>
```

Finally, a cleanup function is added that removes events that cannot be executed after a time-point has passed.

##### Configuration at the co-simulation level
It is possible to specify the FI in the json file used to specify a co-simulation, example is given below:

```json
{
  "fmus": {
    "{alltypes}": "target/test-classes/AllTypes.fmu",

    "{alltypesB}": "target/test-classes/AllTypesB.fmu"
  },
  "connections": {
    "{alltypesB}.alltypesB.boolOutput": ["{alltypes}.alltypesA.boolInput"],
    "{alltypesB}.alltypesB.integerOutput": ["{alltypes}.alltypesA.integerInput"],
    "{alltypesB}.alltypesB.doubleOutput": ["{alltypes}.alltypesA.doubleInput"],
    "{alltypesB}.alltypesB.stringOutput": ["{alltypes}.alltypesA.stringInput"], 

    "{alltypes}.alltypesA.boolOutput": ["{alltypes}.alltypesB.boolInput"],
    "{alltypes}.alltypesA.integerOutput": ["{alltypes}.alltypesB.integerInput"],
    "{alltypes}.alltypesA.doubleOutput": ["{alltypes}.alltypesB.doubleInput"],
    "{alltypes}.alltypesA.stringOutput": ["{alltypes}.alltypesB.stringInput"]

  },
  "parameters": {
  },
  "logVariables": {
    "{alltypes}.alltypesA":["doubleOutput", "boolOutput", "integerOutput", "stringOutput"]
  },
  "algorithm":{"type":"fixed-step","size":0.1},

  "faultInjectConfigurationPath": "target/test-classes/test_clean/testClean.xml",
  "faultInjectInstances": {
    "alltypesA": "id-A"
  }
}
```

Note that, one should specify a path to the xml configuration file where all the events are, as well as the instances that need to be injected. 
In the above example, instance ```alltypesA``` is injected. The value of the field, i.e. ```id-A``` is important when specifying the events, and
has to be part of the string field ```id``` specified for each event, otherwise the event will be ignored. Consider the following as a working example:

```xml
<events>
    <event id="id-A.1" when="(t&gt;=0.2) &amp; (t&lt;0.4)">
        <variable valRef="3" type="bool" newVal="1" />
    </event>
    <event id="id-A.2" when="(t&gt;=1.7) | (t&lt;1.6)">
        <variable valRef="4" type="bool" newVal="1" />
    </event>
    <event id="id-A.3" when="((t&gt;=0.2) &amp; (t&lt;0.4)) | (t&gt;=0.7) | (t&lt;0.6)">
        <variable valRef="5" type="bool" newVal="1" />
    </event>
    <event id="id-A.4" when="(t=0.2) | (t&lt;0.8)">
        <variable valRef="6" type="bool" newVal="1" />
    </event>
    <event id="id-A.5" when="(t=0.2) | (t&gt;0.4)">
        <variable valRef="7" type="bool" newVal="1" />
    </event>
</events>
```

In case one would like to inject multiple instances, the co-simulation json file needs to be adjusted as below:

```json
{
  "fmus": {
    "{alltypes}": "target/test-classes/AllTypes.fmu",

    "{alltypesB}": "target/test-classes/AllTypesB.fmu"
  },
  "connections": {
    "{alltypesB}.alltypesB.boolOutput": ["{alltypes}.alltypesA.boolInput"],
    "{alltypesB}.alltypesB.integerOutput": ["{alltypes}.alltypesA.integerInput"],
    "{alltypesB}.alltypesB.doubleOutput": ["{alltypes}.alltypesA.doubleInput"],
    "{alltypesB}.alltypesB.stringOutput": ["{alltypes}.alltypesA.stringInput"], 

    "{alltypes}.alltypesA.boolOutput": ["{alltypes}.alltypesB.boolInput"],
    "{alltypes}.alltypesA.integerOutput": ["{alltypes}.alltypesB.integerInput"],
    "{alltypes}.alltypesA.doubleOutput": ["{alltypes}.alltypesB.doubleInput"],
    "{alltypes}.alltypesA.stringOutput": ["{alltypes}.alltypesB.stringInput"]

  },
  "parameters": {
  },
  "logVariables": {
    "{alltypes}.alltypesA":["doubleOutput", "boolOutput", "integerOutput", "stringOutput"]
  },
  "algorithm":{"type":"fixed-step","size":0.1},

  "faultInjectConfigurationPath": "target/test-classes/test_clean/testClean.xml",
  "faultInjectInstances": {
    "alltypesA": "id-A",
    "alltypesB": "id-B"
  }
}
```

Events for both can be specified in the same xml file as below:

```xml
<events>
    <event id="id-A_1" when="(t&gt;=0.2) &amp; (t&lt;0.5)" >
        <variable valRef="3" type="real" newVal="t+2*var_3" vars="var_3," />
    </event>

    <event id="id-A_2" when="t=8.0" >
        <variable valRef="1" type="bool" newVal="~var_1" vars="var_1," />
    </event>

    <event id="id-B_1" when="t&gt;=10.0" >
        <variable valRef="5" type="int" newVal="var_5+35" vars="var_5," />
    </event>

    <event id="id-B_2" when="t=12.0" >
        <variable valRef="7" type="string" newVal="halloj" vars=""/>
    </event>
</events>
```

The first two listed events will be injected to ```alltypesA```, whereas the last two will be injected to ```alltypesB```

#### Running from commandline with a maestro jar

Run the following on a terminal:

```bash
java -cp ./maestro-2.2.0-jar-with-dependencies.jar:./faultinject-1.0.0-SNAPSHOT-jar-with-dependencies.jar org.intocps.maestro.Main import sg1 --interpret --inline-framework-config -output ./out simulation-config.json config.json ./FaultInject.mabl
```

where the ```simulation-config.json``` and ```config.json``` are configuration files for the co-simulation.