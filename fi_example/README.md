# Running a simple FI example using the water-tank

## Co-simulation Setup

### The water-tank example

The co-simulation is composed of a water-tank, and a controller, which aims to maintain the level in the water-tank between ```1``` and ```2```. 
More details on this case-study can be found [here](https://github.com/INTO-CPS-Association/example-single_watertank).

To run an experiment with fault-injection, we inject a fault in the input of the tank (control signal from controller to open or close), such that between time ```12``` and ```20```, this signal is always closed, irrespective of the actual level in the tank. 
This can be done by configuring an xml as follows (```wt_fault.xml```):

```xml
<events>
    <event id="id-A" when="(t&lt;=20.0) &amp; (t&gt;=12.0)" other="" vars="">
        <variable valRef="16" type="real" newVal="0.0" vars="" />
    </event>
</events>
```

Install dependencies:

```bash
$ apt-get install openjdk-17-jre
$ pip install pandas matplotlib
```


The example can be run by executing in a terminal the following (change in the script the name of the jar to what you saved it):

```bash
$ ./run_fi_example.sh
```

This script will run the experiment with fault-injection, and without. In the end it will plot
the results in both cases and save the figures both for FI and without FI. 
Note how the level in the tank increases in the FI case.

### The incubator example
This example applies FI to an [incubator](https://github.com/INTO-CPS-Association/example_digital-twin_incubator), and is adapted from the example used in the [tutorial for FM21](https://github.com/INTO-CPS-Association/fm_dt_tutorial_2021).

It is suggested to create a python virtual environment with python3.10, as there have been issues with the installation of ```wxPython```
on a Mac with a lower version.

```bash
$ python3.10 -m venv venv   
$ source venv/bin/activate #activate the venv
```

Install dependencies:
```bash
$ apt-get install openjdk-17-jre
$ pip install -U -r requirements.txt
```

As in the water-tank two experiments, with and without FI are run:

```bash
$ ./run_fi_example.sh
```

In this case the fault is injected in the Plant, and affects its output, the air temperature.
The new value is calculated such that it is lower than the real value, using an exponential function.
As before the plots and generated figures can be inspected.

## Cleaning up

To clean up the generated files run in the ```fi_example``` folder:

```bash
$ ./clean.sh
```