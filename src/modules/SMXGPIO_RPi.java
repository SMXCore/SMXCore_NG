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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Hashtable;

import java.io.PrintWriter;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

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
    public boolean dataSetConnection = false; 
    public boolean read_therm = false;
    
    String truth = "TRUE";
    String falsehood = "FALSE";
    
    public String filePath = new String();
    public String fileName = new String();
    String lastDate = new String();
    PrintWriter file;
    boolean fileworks = true;

    Map<String, GpioPinDigitalOutput> output_pins = new HashMap();
    Map<String, Boolean> output_pins_state = new HashMap();
    Map<String, GpioPinDigitalInput> input_pins = new HashMap();
    Map<String, PinState> input_pins_state = new HashMap();
    Map<String, Integer> input_pins_cntr = new HashMap();

    Date refreshFile() {
        DateFormat df = new SimpleDateFormat("yyyyMMdd");
        Date date = new Date();
        if(!df.format(date).equals(lastDate)) {
            try {
                DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd.");
                FileWriter fw = new FileWriter(filePath + dateFormat.format(date) + fileName, true);
                BufferedWriter bw = new BufferedWriter(fw);
                file = new PrintWriter(bw);
                fileworks = true;
            } catch (Exception e) {
                e.printStackTrace();
                fileworks = false;
            }
        }
        return date;
    }
    
    void printToFile(String s) {
        DateFormat df = new SimpleDateFormat("yyyy/MM/dd\tHH:mm:ss:SSS\t");
        Date date = refreshFile();
        file.println(df.format(date) + s);
        file.flush();
    }
    
    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);
        filePath = PropUtil.GetString(pAttributes, "File_Log_Path", "");
        fileName = PropUtil.GetString(pAttributes, "File_Log", sName);
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
            
            value = PropUtil.GetString(pAttributes, "UseZeroesOnes", "FALSE");
            if(value.equals("TRUE")) {
                truth = "1";
                falsehood = "0";
            }

            iNoOfPins = PropUtil.GetInt(pAttributes, "iNoOfPins", 0);
            read_therm = (PropUtil.GetInt(pAttributes, "ReadTherm", 0) > 0);
//            System.out.println("Number of pins: " + iNoOfPins);
//            System.out.println("" + lPeriod);
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
                        input_pins.put(pinname, myButton);
                        input_pins_state.put(pinname, myButton.getState()); 

                        input_pins_cntr.put(sPinNameCntr, 0);


                        //System.out.println("Loaded pin " + sPinCode + " as " + pinname + " (Input)");

                        myButton.addListener(new GpioPinListenerDigital() {
                            @Override
                            public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                                // display pin state on console and file
                                String state = "";
                                if(event.getState() == PinState.HIGH) {
                                    state = truth;
                                } else {
                                    state = falsehood;
                                }
                                String message = "STATE CHANGE" + "\t" + event.getPin() + "\t" + pinname + "\t" + state;
                                if(consoleLogEnable) {
                                    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS");
                                    Date date = new Date();
                                    System.out.println("<SMXGPIO_RPi: " + sName + "> [" + dateFormat.format(date) + "] --> " + message);
                                }
                                if(fileLogEnable) {
                                    printToFile(message);
                                }

                                if(event.getState() == PinState.LOW) {
                                    pDataSet.put(sPrefix + pinname, falsehood);
                                } else {
                                    int crt = input_pins_cntr.get(pinnamecntr);
                                    input_pins_cntr.put(pinnamecntr, crt + 1);
                                    if(dataSetConnection) {
                                        pDataSet.put(sPrefix + pinname, truth);
                                        pDataSet.put(sPrefix + pinnamecntr, crt + 1);
                                    }
//                                    message = "COUNTER\t" + event.getPin() + "\t" + pinname + "\t" + (crt + 1);
//                                    if(consoleLogEnable) {
//                                        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS");
//                                        Date date = new Date();
//                                        System.out.println("<SMXGPIO_RPi: " + sName + "> [" + dateFormat.format(date) + "] --> " + message);
//                                    }
//                                    if(fileLogEnable) {
//                                        try {
//                                            printToFile(message);
//                                        } catch(Exception e) {
//                                            e.printStackTrace();
//                                        }
//                                    }
                                }

                                input_pins_state.put(pinname, event.getState());
                            }

                        });
                    } else if(sPinIO.equals("Output")) {
                        final GpioPinDigitalOutput pin = gpio.provisionDigitalOutputPin(RaspiPin.getPinByAddress(Integer.parseInt(sPinCode)), sPinName, PinState.HIGH);
                        output_pins.put(sPinName, pin);
                        output_pins_state.put(sPinName, false);
                        //System.out.println("Loaded pin " + sPinCode + " as " + sPinName + " (Output)");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            
            String res = runAndGetOutput("id -u");
            if(res.equals("0") && read_therm) {
                res = runAndGetOutput("grep /proc/modules -e \"w1_therm\" | wc -l");
                if(res.equals("0")) {
                    runAndGetOutput("dtoverlay w1-gpio gpiopin=4 pullup=0");
                }
            }

        } catch (Exception e) {

        }
    }
    
    String runAndGetOutput(String cmd) {
        try {
            Process p;
            p = Runtime.getRuntime().exec(cmd);
            BufferedReader stdInput = new BufferedReader(new 
                 InputStreamReader(p.getInputStream()));
            String s = null;
            String complete = "";
            while ((s = stdInput.readLine()) != null) {
                complete = complete + s;
            }
            return complete;
        } catch(Exception e) {
            return "Java Error " + e.getMessage();
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
                for(Map.Entry<String, GpioPinDigitalInput> pin: input_pins.entrySet()) {
                    String pinname = pin.getKey();
                    PinState ps = pin.getValue().getState();
                    if(ps == PinState.HIGH) { 
                        pDataSet.put(sPrefix + pinname, truth);
                    } else {
                        pDataSet.put(sPrefix + pinname, falsehood);
                    }
                    input_pins_state.put(pinname, ps);
                }

                for(Map.Entry<String, GpioPinDigitalOutput> pin: output_pins.entrySet()) {
                    String value = PropUtil.GetString(pDataSet, sPrefix + pin.getKey(), "ERROR");
                    boolean crt = output_pins_state.get(pin.getKey());
                    boolean changed = false;
                        System.out.println("Testing pin " + pin.getValue().getPin().getAddress() + " with internal: " + sPrefix + pin.getKey());
                    if(value.equals(truth)) {
                        System.out.println(pin.getValue().getState() + " is now TRUE: " + sPrefix + pin.getKey());
                        pin.getValue().high();
                        output_pins_state.put(pin.getKey(), true);
                        if(crt != true) {
                            changed = true;
                        }
                    } else {
                        pin.getValue().low();
                        output_pins_state.put(pin.getKey(), false);
                        if(crt != false) {
                            changed = true;
                        }
                    }
                    if(changed) {
                        String message = "STATE CHANGE" + "\t\t" + pin.getKey() + "\t" + value;
                        if(consoleLogEnable) {
                            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS");
                            Date date = new Date();
                            System.out.println("<SMXGPIO_RPi> [" + dateFormat.format(date) + "] --> " + message);
                        }
                        if(fileLogEnable) {
                            printToFile(message);
                        }
                    }
                }
                
                if(read_therm) {
                    final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:/sys/bus/w1/devices/28*");
                    Files.walkFileTree(Paths.get("/sys/bus/w1/devices/"), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            if (matcher.matches(file)) {
                                String res = runAndGetOutput("cat " + file.toString() + "/w1_slave");
                                int i = res.length() - 1;
                                while(i > 0 && res.charAt(i) >= '0' && res.charAt(i) <= '9') { 
                                    i--;
                                }
                                String rem = res.substring(i + 1);
                                double temp = Double.parseDouble(rem);
                                temp = temp / 1000;
                                pDataSet.put(sPrefix + file.toString(), String.valueOf(temp));
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }

            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                    if(fileLogEnable) {
                        printToFile(e.getMessage());
                    }
                }
            }

        }
    }
}
