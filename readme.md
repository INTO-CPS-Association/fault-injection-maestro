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
