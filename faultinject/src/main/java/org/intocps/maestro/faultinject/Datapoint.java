package org.intocps.maestro.faultinject;

import java.util.HashMap;
import java.util.Map;

public class Datapoint {

    Map <Long, Integer> integerValues = new HashMap<Long, Integer>();
    Map <Long, Double> doubleValues = new HashMap<Long, Double>();
    Map <Long, Boolean> booleanValues = new HashMap<Long, Boolean>();
    Map <Long, String> stringValues = new HashMap<Long, String>();
    
}
