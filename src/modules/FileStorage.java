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
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;

/**
 *
 * @author cristi, mihai
 */
public class FileStorage extends Module {

    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        //PropUtil.LoadFromFile(pStoreAssociation, PropUtil.GetString(pAttributes, "pStoreAssociation", ""));
        LoadAssoc(PropUtil.GetString(pAttributes, "pStoreAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);
        iCommpress = PropUtil.GetInt(pAttributes, "iCommpress ", 1);
        iMove = PropUtil.GetInt(pAttributes, "iMove ", 1);
        sFileName = PropUtil.GetString(pAttributes, "sFileName", sName);
        sFileExt = PropUtil.GetString(pAttributes, "sFileExt", "");
        sSeparator = PropUtil.GetString(pAttributes, "sSeparator", "\t");

        //insert in database metadata related to the Modbus module 
        String sTSDate;
        sTSDate = sdfLogDate.format(new Date());
        pDataSet.put("Module/FileStorage/"+sName+"/StartDateTime", sTSDate); // DateTime
        String s1;
        s1 = String.valueOf(lPeriod); pDataSet.put("Module/FileStorage/"+sName+"/lPeriod", s1); // 
        //s1 = sPrefix; 
        pDataSet.put("Module/FileStorage/"+sName+"/sPrefix", sPrefix); // 
        pDataSet.put("Module/FileStorage/"+sName+"/sFileName", sFileName); // 
    }
    
    int iCommpress;
    int iMove;

    Properties pStoreAssociation = new Properties();
    Hashtable<String, Integer> pDecimalsAssociation = new Hashtable<>();

    Properties pDataSet = null;
    String sPrefix = "";
    Thread tStoreLoop = null;
    
    public void LoadAssoc(String file) {
        if(file.endsWith("json")) {
            LoadJsonAssoc(file);
        } else {
            LoadTxtAssoc(file);
        }
    }
    
    void LoadTxtAssoc(String file) {
        PropUtil.LoadFromFile(pStoreAssociation,  file);
    }
    
    void LoadJsonAssoc(String file) {
        try {
            JsonReader jsr = Json.createReader(new FileInputStream(file));
            JsonObject jso = jsr.readObject();
            Iterator it = jso.entrySet().iterator();
            if(pStoreAssociation == null) {
                pStoreAssociation = new Properties();
            }
            while(it.hasNext()) {
                try {
                    Map.Entry crt = (Map.Entry) it.next();
                    String name = (String) crt.getKey();
                    JsonValue value = (JsonValue) crt.getValue();
                    if(value.getValueType() == JsonValue.ValueType.STRING) {
                        JsonString ss = (JsonString) value;
                        String fss = ss.getString();
                        pStoreAssociation.put(name, fss);
                    } else if(value.getValueType() == JsonValue.ValueType.OBJECT) {
                        JsonObject ass = (JsonObject) value;
                        String fss = ass.getString("label");
                        pStoreAssociation.put(name, fss);
                        JsonValue decv = ass.get("decimals");
                        if(decv != null && decv.getValueType() == JsonValue.ValueType.NUMBER) {
                            JsonNumber decn = (JsonNumber) decv;
                            Integer dec = decn.intValue();
                            pDecimalsAssociation.put(name, dec);
                        }
                    }
                } catch(Exception ex) {
//                    if (Debug == 1) {
//                        System.out.println(ex.getMessage());
//                    }
                    logger.warning(ex.getMessage());
                }
            }
        } catch(Exception ex) {
//            if (Debug == 1) {
//                System.out.println(ex.getMessage());
//            }
            logger.warning(ex.getMessage());
        }
    }

    @Override
    public void Start() {
        try {
            tStoreLoop = new Thread(new Runnable() {

                @Override
                public void run() {
                    StoreLoop();
                    // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }
            });
            tStoreLoop.start();

        } catch (Exception e) {
            // Log(Name + "-Open-" + e.getMessage() + "-" + e.getCause());
        }
    }
    BufferedWriter bwStorage = null;
    String sCrtFileName = "";
    String sLastFileName = "";
    String sFileExt = "";
    String sFileName = "";
    String sRow = "";
    String sSeparator = "";

    public void StoreLoop() {

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
                    pStoreAssociation = new Properties();
                    Initialize();
                    bReinitialize = false;
                    bwStorage = null;
                }
                     
                dCrtDate = new Date();
                sDate = sdfLogDate.format(dCrtDate);

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

    SimpleDateFormat sdf = new SimpleDateFormat("YYYYMMdd");
    SimpleDateFormat sdfLogDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    SimpleDateFormat sdfLogDateMs = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss SSS");
    SimpleDateFormat sdfLogMs = new SimpleDateFormat("SSS");

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

    String sDate = "";
    Date dCrtDate = null;
    Date dLastDate = null;
    String sLastRow = "";

    String sKey;
    String sValue;
    Enumeration eKeys;
    String sInternalAttr;
    String sSameValues;

    public String GetValues() {
        String sRet = "";
        try {
            eKeys = pStoreAssociation.keys();
            while (eKeys.hasMoreElements()) {
                try {

                    sInternalAttr = sPrefix + (String) eKeys.nextElement();
                    sValue = (String) pDataSet.getProperty(sInternalAttr, "");
                    Integer decimals = pDecimalsAssociation.get(sInternalAttr);
                    if(decimals != null) {
                        sValue = Double2DecPointStr(Double.parseDouble(sValue), decimals);
                    }
                    sRet += sValue + sSeparator;

                } catch (Exception ex) {
                    if (Debug == 1) {
                        System.out.println(ex.getMessage());
                    }
                }
            }
            if (sRet.equals(sLastRow)) {
                sSameValues = "1";
            } else {
                sSameValues = "0";
            }
            sLastRow = sRet;
            sRet = sSameValues + sSeparator + sRet;

            sRet = sdfLogDateMs.format(dCrtDate) + sSeparator
                    + sdfLogDate.format(dCrtDate) + sSeparator
                    + sdfLogMs.format(dCrtDate) + sSeparator
                    + sRet;

        } catch (Exception e) {
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
        return sRet + "\r\n";
    }

    public String GetHeader() {

        String sRet = "";
        try {
            eKeys = pStoreAssociation.keys();
            while (eKeys.hasMoreElements()) {
                try {

                    sInternalAttr = (String) eKeys.nextElement();
                    if (sInternalAttr.startsWith("#")) {
                        continue;
                    }
                    sValue = sPrefix + (String) pStoreAssociation.getProperty(sInternalAttr, "");
                    sRet += sValue + sSeparator;

                } catch (Exception ex) {

                    if (Debug == 1) {
                        System.out.println(ex.getMessage());
                    }
                }
            }

            sRet = "SysDate-ms" + sSeparator
                    + "SysDate" + sSeparator
                    + "SysTime-ms" + sSeparator
                    + "SameValues" + sSeparator + sRet;

        } catch (Exception e) {
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
        return sRet + "\r\n";

    }

    public static String Double2DecPointStr(double dVal, int iDecimals) {
        String sResult = " ";
        int iLen, iNoOfIntDigits;
        try {
            sResult = Double.toString(dVal);
            iLen = sResult.length();
            iNoOfIntDigits = sResult.indexOf(".");
            if (iLen - iNoOfIntDigits - 1 > iDecimals) {
                if (iDecimals < 1) {
                    sResult = sResult.substring(0, iNoOfIntDigits + iDecimals);
                } else {
                    sResult = sResult.substring(0, iNoOfIntDigits + iDecimals + 1);
                }
            }

        } catch (Exception e) {

        }
        return sResult;
    }
    
}
