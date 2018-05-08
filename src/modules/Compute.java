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
import java.text.DateFormat;
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

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
/**
 *
 * @author vlad, mihai
 */
public class Compute extends Module {

    JsonArray commands;
    PrintWriter file;
    
    @Override
    public void Initialize() {
        
        JsonObject jso = read_jso_from_file(sAttributesFile);
        pDataSet = (SmartProperties) mmManager.getSharedData(jso.getString("dataSet", ""));
        lPeriod = jso.getInt("period", 1000);
        commands = jso.getJsonArray("commands");
    }
    
    void printToFile(String s) {
        try {
        //DateFormat df = new SimpleDateFormat("yyyy/MM/dd\tHH:mm:ss:SSS\t");
            //Date date = refreshFile();
            DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd.");
            FileWriter fw = new FileWriter("Compute_log.txt", true);
            BufferedWriter bw = new BufferedWriter(fw);
            file = new PrintWriter(bw);
            //file.println("------------------------------------------------------");
            file.println(s);
            file.flush();
            file.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    void processCmd(JsonObject command) {
        try {
            String cmd = command.getString("Cmd", "");
            int period = command.getInt("Period", 1); // Neimplementat
            JsonArray Input = command.getJsonArray("Input");
            JsonArray Output = command.getJsonArray("Output");
            
            //System.out.println("processCmd0: "+cmd.toString());
            // Implement the Compute algorithm assocaited with "AddItems"
            if(cmd.equals("AddItems")) {
                // momentan implementez ca si cum toate tipurile de variabile ar fii double
                double sum = 0.0;
                for(int i = 0; i < Input.size(); i++) {
                    // Read type of item
                    Metadata meta = pDataSet.getmeta(Output.getString(i));
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
            } else 
                // Implement the Compute algorithm associated with "Send_email_01"
                if(cmd.equals("Send_email_01")) {
                    DateFormat df = new SimpleDateFormat("yyyy/MM/dd\tHH"); // hour based
                    //DateFormat df = new SimpleDateFormat("yyyy/MM/dd\tHH:mm"); // minute based
                    String date = df.format(new Date());
                    if(!(date.equals(pAttributes.getProperty("Send_email_01_time", "")))) {
                        pAttributes.setProperty("Send_email_01_time", date);
                        Properties props = new Properties();
                        props.put("mail.smtp.host", "smtp.gmail.com");
                        props.put("mail.smtp.socketFactory.port", "465");
                        props.put("mail.smtp.socketFactory.class",
                                        "javax.net.ssl.SSLSocketFactory");
                        props.put("mail.smtp.auth", "true");
                        props.put("mail.smtp.port", "465");
                        final String user = command.getString("User");
                        final String password = command.getString("Password");
                        final String recipient = command.getString("Recipient");
                        final String subject = command.getString("Subject");

                        Session session = Session.getDefaultInstance(props,
                                new javax.mail.Authenticator() {
                                        protected PasswordAuthentication getPasswordAuthentication() {
                                                return new PasswordAuthentication(user,password);
                                        }
                                });

                        try {

                                Message message = new MimeMessage(session);
                                message.setFrom(new InternetAddress(user));
                                message.setRecipients(Message.RecipientType.TO,
                                                InternetAddress.parse(recipient));
                                message.setSubject(subject);
                                message.setText("Dear Mr. Me," +
                                                "\n\n No spam to my email, please!");

                                Transport.send(message);

                                System.out.println("Email Sent");

                        } catch (MessagingException e) {
                                throw new RuntimeException(e);
                        }
                    }
                    
            } else 
                // Implement the Compute algorithm associated with "ChangeValue_Lib01"
                if(cmd.equals("ChangeValue_Lib01_numkMG")) {
                    // A value which has as sufix k, M, G will be mulktiplied by 1000 (kilo), 1'000'000 (Mega) or 1'000'000'000 (Giga)
                    //double new_result=0;
                    //System.out.println("processCmd1: "+cmd.toString());
                    String variable_string_ini="";
                    String variable_string_final="";
                    double new_value = 0.0;
                    //System.out.println("processCmd3: "+Input.getString(0));
                    Metadata meta = pDataSet.getmeta(Input.getString(0));
                    String type = meta.type;
//                    Date ts = meta.timestamp;
                    // make calculations
                    
                    //System.out.println("processCmd2: "+cmd.toString());
                    variable_string_ini = (String) pDataSet.getProperty(Input.getString(0), "");
                    //System.out.println("ChangeValue_Lib01_numkMG: " + variable_string_ini+"\n");

                    if(  variable_string_ini.endsWith("m") == true) {
                        variable_string_final = variable_string_ini.replace("m","");
                        new_value =  Double.parseDouble(variable_string_final)*0.001;     
                    } else
                    if(  variable_string_ini.endsWith("k") == true) {
                        variable_string_final = variable_string_ini.replace("k","");
                        new_value =  Double.parseDouble(variable_string_final)*1000;     
                    } else
                    if(  variable_string_ini.endsWith("M") == true) {
                        variable_string_final = variable_string_ini.replace("M","");
                        new_value =  Double.parseDouble(variable_string_final)*1000*1000;     
                    } else 
                    new_value =  Double.parseDouble(variable_string_ini);
                    //System.out.println("ChangeValue_Lib01_numkMG_new value: " + Double.toString(new_value));
                    //System.out.println("ChangeValue_Lib01_numkMG_new name_of value: " + Output.getString(0));
                              // variable_string_final = 0;
                            //PropUtil.GetDouble(pDataSet, Input.getString(0), 0.0);
//                            sum += line.getJsonNumber(j).doubleValue() * PropUtil.GetDouble(pDataSet, Input.getString(j), 0.0);
                        
                    pDataSet.put(Output.getString(0), Double.toString(new_value));
                    //Metadata meta = pDataSet.getmeta(Output.getString(0));
                    //meta.type = "double"; // ca un exemplu
                    
                } else 
                // Implement the Compute algorithm associated with "ChangeValue_Lib01"
                if(cmd.equals("ChangeValue_Lib01_multiply")) {
                    // A value will be mulktiplied by a constant and a sign will be given based on the sig of another value
                    //double new_result=0;
                    //System.out.println("processCmd1: "+cmd.toString());
                    String variable_string_ini="";
                    String variable_string_multiplier="";
                    String variable_string_value_sign="";
                    //String variable_string_final="";
                    double new_value = 0.0;
                    //printToFile("Hello");
                    //System.out.println("processCmd3: "+Input.getString(0));
                    Metadata meta = pDataSet.getmeta(Input.getString(0));
                    String type = meta.type;
//                    Date ts = meta.timestamp;
                    // make calculations
                    
                    //System.out.println("processCmd2: "+cmd.toString());
                    variable_string_ini = (String) pDataSet.getProperty(Input.getString(0), "");
                    //printToFile("H1="+variable_string_ini+" str="+Input.getString(0));
                    variable_string_multiplier = (String) Input.getString(1);
                    variable_string_value_sign = (String) pDataSet.getProperty(Input.getString(2), "");
                    //printToFile("H2="+variable_string_multiplier+" str="+Input.getString(1));
                    //variable_string_final = (String) pDataSet.getProperty(Input.getString(2), "");
                    //System.out.println("ChangeValue_Lib01_numkMG: " + variable_string_ini+"\n");
                    new_value =  Double.parseDouble(variable_string_ini)*Double.parseDouble(variable_string_multiplier); 
                    if(Double.parseDouble(variable_string_value_sign)<0) new_value=-new_value;
                    //printToFile("H3="+new_value+" str="+Output.getString(0));

                    //System.out.println("ChangeValue_Lib01_numkMG_new value: " + Double.toString(new_value));
                    //System.out.println("ChangeValue_Lib01_numkMG_new name_of value: " + Output.getString(0));
                              // variable_string_final = 0;
                            //PropUtil.GetDouble(pDataSet, Input.getString(0), 0.0);
//                            sum += line.getJsonNumber(j).doubleValue() * PropUtil.GetDouble(pDataSet, Input.getString(j), 0.0);
                        
                    pDataSet.put(Output.getString(0), Double.toString(new_value));
                    //Metadata meta = pDataSet.getmeta(Output.getString(0));
                    //meta.type = "double"; // ca un exemplu
                    
                } else
                if(cmd.equals("ChangeValue_Lib01_Add3Values")) {
                    // A value will be mulktiplied by a constant and a sign will be given based on the sig of another value
                    //double new_result=0;
                    //System.out.println("processCmd1: "+cmd.toString());
                    String variable_string1="";
                    String variable_string2="";
                    String variable_string3="";
                    //String variable_string_final="";
                    double new_value = 0.0;
                    //printToFile("Hello");
                    //System.out.println("processCmd3: "+Input.getString(0));
                    Metadata meta = pDataSet.getmeta(Input.getString(0));
                    String type = meta.type;
//                    Date ts = meta.timestamp;
                    // make calculations
                    
                    //System.out.println("processCmd2: "+cmd.toString());
                    variable_string1 = (String) pDataSet.getProperty(Input.getString(0), "");
                    //printToFile("H1="+variable_string_ini+" str="+Input.getString(0));
                    variable_string2 = (String) pDataSet.getProperty(Input.getString(1), "");
                    variable_string3 = (String) pDataSet.getProperty(Input.getString(2), "");
                    new_value =  Double.parseDouble(variable_string1)+Double.parseDouble(variable_string2)+Double.parseDouble(variable_string3); 
                        
                    pDataSet.put(Output.getString(0), Double.toString(new_value));
                    
                }
        } 
        catch(Exception ex) {
            
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
