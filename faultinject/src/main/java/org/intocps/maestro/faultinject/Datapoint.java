package org.intocps.maestro.faultinject;

import java.util.HashMap;
import java.util.Map;

public class Datapoint {

    public Map<Long, Integer> integerValues = new HashMap<Long, Integer>();
    public Map<Long, Double> doubleValues = new HashMap<Long, Double>();
    public Map<Long, Boolean> booleanValues = new HashMap<Long, Boolean>();
    public Map<Long, String> stringValues = new HashMap<Long, String>();

}
