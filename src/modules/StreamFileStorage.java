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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import static java.time.temporal.ChronoUnit.NANOS;
import static java.time.temporal.ChronoUnit.SECONDS;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Scanner;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

/**
 *
 * @author mihubu
 */
public class StreamFileStorage extends Module {
    

    @Override
    public void Initialize() {
        /*pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);*/
        lastacc = LocalDateTime.now();
        
        JsonObject jso = read_jso_from_file(sAttributesFile);
        
        sPrefix = jso.getString("prefix");
        pDataSet = mmManager.getSharedData(jso.getString("dataSet", ""));
        lPeriod = jso.getInt("lPeriod", 5000);
        
        iCommpress = jso.getInt("compress", 1);//PropUtil.GetInt(pAttributes, "iCommpress ", 1);
        iMove = jso.getInt("move", 1); //PropUtil.GetInt(pAttributes, "iMove ", 1);
        sFileName = jso.getString("filename", sName); //PropUtil.GetString(pAttributes, "sFileName", sName);
        sFileExt = jso.getString("fileext", ""); //PropUtil.GetString(pAttributes, "sFileExt", "");
        sSeparator = jso.getString("separator", "\t"); //PropUtil.GetString(pAttributes, "sSeparator", "\t");
        bufferLoc = jso.getString("buffer_location", "");
    }

    Properties pAssociation = new Properties();

    Properties pDataSet = null; // The data set of the real time database
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
    
    String sDate = "";
    Date dCrtDate = null;
    Date dLastDate = null;
    BufferedWriter bwStorage = null;
    int iCommpress, iMove;
    String sCrtFileName = "";
    String sLastFileName = "";
    String sFileExt = "";
    String sFileName = "";
    String sRow = "";
    String sSeparator = "";
    SimpleDateFormat sdf = new SimpleDateFormat("YYYYMMdd");
    
    String bufferLoc;
   

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
                if(bReinitialize) {
                    Initialize();
                    bReinitialize = false;
                    bwStorage = null;
                }

                lIniSysTimeMs = System.currentTimeMillis();
                ldt = lIniSysTimeMs - lMemSysTimeMs;
                lMemSysTimeMs = lIniSysTimeMs;
                     
                dCrtDate = new Date();

                lIniSysTimeMs = System.currentTimeMillis();
                ldt = lIniSysTimeMs - lMemSysTimeMs;
                lMemSysTimeMs = lIniSysTimeMs;

                if (dLastDate != null) {
                    if (dLastDate.getDay() != dCrtDate.getDay()) {
                        // if (dLastDate.getMinutes() != dCrtDate.getMinutes()) {
                        try {
                            bwStorage.close();
                        } catch (Exception ex) {
                        }
                        bwStorage = null;
                        if (iCommpress == 1) {
                            if (iMove == 1) {
                                util.ZipFiles.getInstance().Compress(sCrtFileName, sCrtFileName + ".zip", "move");
                            } else {
                                util.ZipFiles.getInstance().Compress(sCrtFileName, sCrtFileName + ".zip", "null");
                            }
                        }

                    }
                }
                dLastDate = dCrtDate;

                if (bwStorage == null) {
                    sCrtFileName = sFileName + "-" + sdf.format(dCrtDate) + sFileExt;
                    bwStorage = new BufferedWriter(new FileWriter(sCrtFileName, true));

                    bwStorage.write(GetHeader());
                }
                sRow = GetValues();
                try {
                    bwStorage.write(sRow);
                    bwStorage.flush();
                } catch (Exception ex) {
                    try {
                        bwStorage.close();
                    } catch (Exception exc) {
                    }
                    bwStorage = null;
                }

            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }
    public String GetHeader() {
        return "Value" + sSeparator + "Timestamp" + sSeparator + "TimestampSMX" + sSeparator + "Difftime" + "\r\n";
    }
    LocalDateTime lastacc;
    LocalTime lt;
    public String GetValues() {
        String sRet = "";
        String thebuf = pDataSet.getProperty("/INTERNALBUF" + bufferLoc, "[]");
        if(thebuf.equals("[]"))
            return "";
        pDataSet.setProperty("/INTERNALBUF" + bufferLoc, "[]");
        
        DateTimeFormatter propFormat = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral(" ")
            .appendPattern("HH:mm:ss")
            .appendPattern(":SSSSSS")
            // create formatter
            .toFormatter();
        
        
        DateTimeFormatter formatld = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm:ss")
            .appendPattern(":SSSSSS")
            // create formatter
            .toFormatter();
        
        JsonReader jsonReader = Json.createReader(new StringReader(thebuf));
        JsonArray array = jsonReader.readArray();
        LocalDateTime nlac = lastacc;
        for(int i = 0; i < array.size(); i++) {
            JsonArray crt = array.getJsonArray(i);
            String ts_smx = crt.getString(2);
            LocalDateTime ts_smxdt = LocalDateTime.parse(ts_smx, propFormat);
            if(ts_smxdt.isAfter(lastacc)) {
                LocalTime thistime = LocalTime.parse(crt.getString(1), formatld);
                if(lt == null) {
                    lt = thistime;
                }
                long timediffusec = (lt.until(thistime, NANOS) / 1000) % 1000000;
                long timediffs = lt.until(thistime, SECONDS);
                timediffusec += timediffs * 1000000;
                lt = thistime;
                for(int j = 0; j < crt.size(); j++) {
                    sRet += crt.get(j).toString();
                    sRet += sSeparator;
                }
                sRet += "" + timediffusec;
                if(ts_smxdt.isAfter(nlac)) {
                    nlac = ts_smxdt;
                }
                sRet += "\r\n";
            }
        }
        lastacc = nlac;
        
        return sRet;
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
}
