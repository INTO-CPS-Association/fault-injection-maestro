#### Development Environment
You need Java 11 and maven 3.6 to build the project (??).
The project can be built from CLI using the maven commands.
```bash
mvn clean
mvn compile
mvn test
```

MaBL file for the SimpleTest.java: SmallFaultInjectTest.mabl

#### Water-tank Co-simulation Case-Study
*NOTE: Download the code at the wrapperFmuComponent branch.

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
The relevant files are in rbmq_example.


#### Development Notes
It is possible to define one-time events e.g.

<event id="2" timeStep="9.0">
    <variable valRef="3" type="real" newVal="50.0" />
</event>
Note that all variables that should be affected at that time-step should be included within the same one-time event.

It is also possible to define events with a duration. Different variables might be injected for different periods as such, events with different and overlapping durations
can be defined, e.g.

<event id="3" timeStep="21.0" duration="5.0" durationToggle="false">
    <variable valRef="3" type="real" newVal="57.0" />
</event>
<event id="4" timeStep="25.0" duration="2.0" durationToggle="false">
    <variable valRef="4" type="real" newVal="60.0" />
</event>

If the injection should be performed for the whole time, then durationToggle can be set to "true". Note that the attributes duration and durationToggle are optional, if not defined, they will de set to default values, i.e. 1 and false.

Should there be multiple overlapping events with duration that target the same variable, the latter will be injected with the value in the event defined last in the xml file. 