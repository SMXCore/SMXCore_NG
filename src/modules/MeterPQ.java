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

/**
 *
 * @author cristi
 */
public class MeterPQ extends Module {
    
    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        // PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);
    }
    
    Properties pAssociation = new Properties();
    
    Properties pDataSet = null;
    String sPrefix = "";
    Thread tLoop = null;
    
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
                
                U1 = Double.parseDouble((String) pDataSet.get(sPrefix + "LD01/U1"));
                U2 = Double.parseDouble((String) pDataSet.get(sPrefix + "LD01/U2"));
                U3 = Double.parseDouble((String) pDataSet.get(sPrefix + "LD01/U3"));
                
                I1 = Double.parseDouble((String) pDataSet.get(sPrefix + "LD01/I1"));
                I2 = Double.parseDouble((String) pDataSet.get(sPrefix + "LD01/I2"));
                I3 = Double.parseDouble((String) pDataSet.get(sPrefix + "LD01/I3"));
                
                Umed = (U1 + U2 + U3) / 3;
                
                pDataSet.put(sPrefix+"LD01/Umed", Double2DecPointStr(Umed,2));
                pDataSet.put(sPrefix+"LD01/Umed-ts", sdf.format(new Date()));
                
                dtNow=new Date();
                if((dtNow.getSeconds()==0)&&(dtNow.getMinutes()%2==0)){
                    System.out.println(sdf.format(dtNow));
                }
                
                
                
            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }
            
        }
    }
    
    double U1, U2, U3, I1, I2, I3, Umed;
    Date dtNow;
    
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
    
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        public String Double2DecPointStr(double dVal, int iDecimals) {
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
