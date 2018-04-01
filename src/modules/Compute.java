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
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Scanner;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import util.SmartProperties;
import util.SmartProperties.Metadata;

/**
 *
 * @author vlad, mihai
 */
public class Compute extends Module {

    JsonArray commands;
    
    @Override
    public void Initialize() {
        
        JsonObject jso = read_jso_from_file(sAttributesFile);
        pDataSet = (SmartProperties) mmManager.getSharedData(jso.getString("dataSet", ""));
        lPeriod = jso.getInt("period", 1000);
        commands = jso.getJsonArray("commands");
    }
    
    void processCmd(JsonObject command) {
        try {
            String cmd = command.getString("Cmd", "");
            int period = command.getInt("Period", 1); // Neimplementat
            JsonArray Input = command.getJsonArray("Input");
            JsonArray Output = command.getJsonArray("Output");
            
            // Implement the Compute algorithm assocaited with "AddItems"
            if(cmd.equals("AddItems")) {
                // momentan implementez ca si cum toate tipurile de variabile ar fii double
                double sum = 0.0;
                for(int i = 0; i < Input.size(); i++) {
                    // Read type of item
                    Metadata meta = pDataSet.getmeta(Output.getString(0));
                    String type = meta.type;
                    Date ts = meta.timestamp;
                    // make calculations
                    sum += PropUtil.GetDouble(pDataSet, Input.getString(i), 0.0);
                }
                // Saves result in the first item of output set of variables
                pDataSet.put(Output.getString(0), Double.toString(sum));
                // Set the type equial to "double"
                Metadata meta = pDataSet.getmeta(Output.getString(0));
                meta.type = "double"; // ca un exemplu
            } else 
                // Implement the Compute algorithm associated with "DirectComponents"
                if(cmd.equals("DirectComponents")) {
                // momentan implementez ca si cum toate tipurile de variabile ar fii double
                JsonArray matrix = command.getJsonArray("Matrix");
                // simple example, to be developed later in a real Compute function
                for(int i = 0; i < Output.size(); i++) { // dpdv matematic transpun si devin vectori coloana (mai usor de implementat)
                    double sum = 0.0;
                    JsonArray line = matrix.getJsonArray(i);
                    for(int j = 0; j < Input.size(); j++) {
                        sum += line.getJsonNumber(j).doubleValue() * PropUtil.GetDouble(pDataSet, Input.getString(j), 0.0);
                    }
                    pDataSet.put(Output.getString(i), Double.toString(sum));
                    Metadata meta = pDataSet.getmeta(Output.getString(i ));
                    meta.type = "double"; // ca un exemplu
                }
            }
        } catch(Exception ex) {
            
        }
    }
    
    JsonObject read_jso_from_file(String file) {
        JsonObject jso = null;
        try {
            String file_content = new Scanner(new File(file)).useDelimiter("\\Z").next();
            // comment processing

            String comment_pattern = "(^|\\n)(#|//)[^\\n]*(\\n|$)";
            int max_iterations = 1000;
            int ijk = 0;
            while(true) {
                ijk++;
                String new_file_content = file_content.replaceAll(comment_pattern, "\n");
                if(new_file_content.equals(file_content) || ijk == max_iterations) {
                    break;
                } else file_content = new_file_content;
            }

            JsonReader jsr = Json.createReader(new StringReader(file_content));
            jso = jsr.readObject();
        } catch(Exception ex) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            String sStackTrace = sw.toString();
            logger.warning(ex.getMessage() + "\n Stacktrace: " + sStackTrace);
        }
        return jso;
    }

    Properties pAssociation = new Properties();

    SmartProperties pDataSet = null; // The data set of the real time database
    String sPrefix = ""; // The module's prefix, prefixed to the name of values in the data set
    Thread tLoop = null; // The thread that we are running on

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

                for(int i = 0; i < commands.size(); i++) {
                    processCmd(commands.getJsonObject(i));
                }
                
                lIniSysTimeMs = System.currentTimeMillis();
                ldt = lIniSysTimeMs - lMemSysTimeMs;
                lMemSysTimeMs = lIniSysTimeMs;


            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }



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


}
