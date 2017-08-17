/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import util.PropUtil;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Enumeration;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.text.DateFormat;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import java.util.Hashtable;

/**
 *
 * @author vlad
 */
public class SMXGPIO_RPi extends Module {

    Properties pAssociation = new Properties();

    Properties pDataSet = null; // The data set of the real time database
    String sPrefix = ""; // The module's prefix, prefixed to the name of values in the data set
    Thread tLoop = null; // The thread that we are running on
    
    public int Pause = 0;
    public int memPause = 0;
    public int bStop = 0;

    public int Debug = 0;

    public long lPeriod = 0;
    public long lIniSysTimeMs = 0;
    public long lMemSysTimeMs = 0;
    public long ldt = 0;
    public double ddt = 0.0;
    public long lDelay = 0;
    
    public boolean consoleLogEnable = false;
    public boolean fileLogEnable = false; // Unimplemented
    public boolean dataSetConnection = false; // Unimplemented
    
    Map<String, GpioPinDigitalOutput> output_pins = new HashMap();
    Map<String, PinState> input_pins = new HashMap();
    Map<String, Integer> input_pins_cntr = new HashMap();

    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);
        int iNoOfPins = 0;
        String sAttributePrefix = "";
        String sPinName = "";
        String sPinNameCntr = "";
        String sPinCode = "";
        String sPinIO = "";
        
        final GpioController gpio = GpioFactory.getInstance();
        try {
            
            String value = PropUtil.GetString(pAttributes, "File_Log_Enable", "FALSE");
            if(value.equals("TRUE")) {
                fileLogEnable = true;
            } else {
                fileLogEnable = false;
            }
            value = PropUtil.GetString(pAttributes, "Console_Log_Enable", "FALSE");
            if(value.equals("TRUE")) {
                consoleLogEnable = true;
            } else {
                consoleLogEnable = false;
            }
            value = PropUtil.GetString(pAttributes, "DataSet_Connection_Enable", "FALSE");
            if(value.equals("TRUE")) {
                dataSetConnection = true;
            } else {
                dataSetConnection = false;
            }
            
            iNoOfPins = PropUtil.GetInt(pAttributes, "iNoOfPins", 0);
            System.out.println("Number of pins: " + iNoOfPins);
            System.out.println("" + lPeriod);
            for (int i = 0; i < iNoOfPins; i++) {
                try {
                    sAttributePrefix = "P" + Integer.toString(i+1) + "-";
                    sPinName = pAttributes.getProperty(sAttributePrefix + "Name", sAttributePrefix);
                    sPinNameCntr = pAttributes.getProperty(sAttributePrefix + "Counter", sPinName + "-Cntr");
                    sPinCode = pAttributes.getProperty(sAttributePrefix + "Code", "");
                    sPinIO = pAttributes.getProperty(sAttributePrefix + "PinIO", "");
                    //System.out.println(sPinName + " " + sPinCode + " " + sPinIO + ".");
                    if(sPinName.length() < 2) {
                        continue;
                    }
                    if(sPinIO.equals("Input")) {
                        final GpioPinDigitalInput myButton = gpio.provisionDigitalInputPin(RaspiPin.getPinByAddress(Integer.parseInt(sPinCode)), PinPullResistance.PULL_DOWN);
                        myButton.setShutdownOptions(true);
        
                        final String pinname = sPinName;
                        final String pinnamecntr = sPinNameCntr;
                        input_pins.put(pinname, PinState.LOW);
                        
                        input_pins_cntr.put(sPinNameCntr, 0);
                        
                        
                        //System.out.println("Loaded pin " + sPinCode + " as " + pinname + " (Input)");
                        
                        myButton.addListener(new GpioPinListenerDigital() {
                            @Override
                            public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                                // display pin state on console
                                if(consoleLogEnable) {
                                    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS");
                                    Date date = new Date();
                                    System.out.println("<SMXGPIO_RPi: " + sName + "> [" + dateFormat.format(date) + "] --> GPIO PIN STATE CHANGE: " + event.getPin() + "(" + pinname + ")" + " = " + event.getState());
                                }
                                
                                if(event.getState() == PinState.LOW) {
                                    pDataSet.put(sPrefix + pinname, "FALSE");
                                } else {
                                    pDataSet.put(sPrefix + pinname, "TRUE");
                                    int crt = input_pins_cntr.get(pinnamecntr);
                                    input_pins_cntr.put(pinnamecntr, crt + 1);
                                    if(consoleLogEnable) {
                                        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS");
                                        Date date = new Date();
                                        System.out.println("<SMXGPIO_RPi: " + sName + "> [" + dateFormat.format(date) + "] --> GPIO PIN COUNTER: " + event.getPin() + "(" + pinname + ")" + " = " + (crt + 1));
                                    }
                                }

                                input_pins.put(pinname, event.getState());
                            }

                        });
                    } else if(sPinIO.equals("Output")) {
                        final GpioPinDigitalOutput pin = gpio.provisionDigitalOutputPin(RaspiPin.getPinByAddress(Integer.parseInt(sPinCode)), sPinName, PinState.HIGH);
                        output_pins.put(sPinName, pin);
                        //System.out.println("Loaded pin " + sPinCode + " as " + sPinName + " (Output)");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

        } catch (Exception e) {

        }
    }

    
    @Override
    public void Start() {
        try {
            tLoop = new Thread(new Runnable() {

                @Override
                public void run() {
                    Loop();
                    // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }
            });
            tLoop.start();

        } catch (Exception e) {
            // Log(Name + "-Open-" + e.getMessage() + "-" + e.getCause());
        }
    }
   

    public void Loop() {
        while (bStop == 0) {
            try {
                if (lPeriod > 0) {
                    //lIniSysTimeMs = System.currentTimeMillis();
                    lDelay = lPeriod - (System.currentTimeMillis() % lPeriod);
                    Thread.sleep(lDelay);
                } else {
                    Thread.sleep(5000);
                }
                if (lMemSysTimeMs == 0) {
                    lMemSysTimeMs = System.currentTimeMillis();
                }

                lIniSysTimeMs = System.currentTimeMillis();
                ldt = lIniSysTimeMs - lMemSysTimeMs;
                lMemSysTimeMs = lIniSysTimeMs;
                    //System.out.println("<SMXGPIO_RPi>");
                
                for(Map.Entry<String, GpioPinDigitalOutput> pin: output_pins.entrySet()) {
                    String value = PropUtil.GetString(pDataSet, sPrefix + pin.getKey(), "ERROR");
                    if(value.equals("TRUE")) {
                        pin.getValue().high();
                    } else {
                        pin.getValue().low();
                    }
                    if(consoleLogEnable) {
                        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS");
                        Date date = new Date();
                        System.out.println("<SMXGPIO_RPi> [" + dateFormat.format(date) + "] --> GPIO PIN CURRENT STATE: " + pin.getKey() + " = " + value);
                    }
                }

            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }
}
