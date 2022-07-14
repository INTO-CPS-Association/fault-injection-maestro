package org.intocps.maestro.faultinject;

public class InvalidFIConfigurationException extends Exception{

        public InvalidFIConfigurationException (String str)
        {
            // calling the constructor of parent Exception
            super(str);
        }
}
