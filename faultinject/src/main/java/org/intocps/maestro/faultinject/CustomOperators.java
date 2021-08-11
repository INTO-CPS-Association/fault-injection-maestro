package org.intocps.maestro.faultinject;

import net.objecthunter.exp4j.operator.Operator;

public class CustomOperators {

        Operator or = new Operator("|", 2, true, Operator.PRECEDENCE_ADDITION - 1) {
        
            @Override
            public double apply(double[] values) {
                if (values[0] == 1d || values[1] ==1d) {
                    return 1d;
                } else {
                    return 0d;
                }
            }
        };

        Operator and = new Operator("&", 2, true, Operator.PRECEDENCE_ADDITION - 1) {
        
            @Override
            public double apply(double[] values) {
                if (values[0] == 1d && values[1] ==1d) {
                    return 1d;
                } else {
                    return 0d;
                }
            }
        };

        Operator not = new Operator("~", 1, true, Operator.PRECEDENCE_ADDITION - 1) {
        
            @Override
            public double apply(double[] values) {
                if (values[0] == 0d) {
                    return 1d;
                } else {
                    return 0d;
                }
            }
        };

        Operator gteq = new Operator(">=", 2, true, Operator.PRECEDENCE_ADDITION - 1) {
    
            @Override
            public double apply(double[] values) {
                int atStartorBigger = Double.compare(values[0], values[1]);
                boolean equalToStart = Math.abs(values[0] - values[1]) <= 0.0000001;
                if (atStartorBigger  > 0 || equalToStart) {
                    return 1d;
                } else {
                    return 0d;
                }
            }
        };

        Operator gt = new Operator(">", 2, true, Operator.PRECEDENCE_ADDITION - 1) {
    
            @Override
            public double apply(double[] values) {
                if (values[0] > values[1]) {
                    return 1d;
                } else {
                    return 0d;
                }
            }
        };

        Operator lteq = new Operator("<=", 2, true, Operator.PRECEDENCE_ADDITION - 1) {
    
            @Override
            public double apply(double[] values) {
                int atEndorSmaller = Double.compare(values[0], values[1]);
                boolean equalToEnd = Math.abs(values[0] - values[1]) <= 0.0000001;
                if (equalToEnd || atEndorSmaller < 0) {
                    return 1d;
                } else {
                    return 0d;
                }
            }
        };

        Operator lt = new Operator("<", 2, true, Operator.PRECEDENCE_ADDITION - 1) {
    
            @Override
            public double apply(double[] values) {
                if (values[0] < values[1]) {
                    return 1d;
                } else {
                    return 0d;
                }
            }
        };

        Operator eq = new Operator("=", 2, true, Operator.PRECEDENCE_ADDITION - 1) {
    
            @Override
            public double apply(double[] values) {
                if (Math.abs(values[0] - values[1]) <= 0.0000001) {
                    return 1d;
                } else {
                    return 0d;
                }
            }
        };
}
