/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules.contrib;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import util.PropUtil;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Enumeration;
import modules.Module;


/**
 *
 * @author mihai
 */
public class S4G_LESSAg extends Module {

    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);

        //insert in database metadata related to the Modbus module 
        String sTSDate;
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        sTSDate = sdf.format(new Date());
        pDataSet.put("Module/S4G_LESSAg/"+sName+"/StartDateTime", sTSDate); // DateTime
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

                // Here is running the main function
                LoopFunction();
                double P_CONS, P_PV, P_PCC, P_BAT;
                //P_CONS = Double.parseDouble((String) pDataSet.get(sPrefix + "SMX/Values/P_CONS/value"));
                P_PV = Double.parseDouble((String) pDataSet.get(sPrefix + "SMX/Values/P_PV/value"));
                P_PCC = Double.parseDouble((String) pDataSet.get(sPrefix + "SMX/Values/P_PCC/value"));
                P_BAT = Double.parseDouble((String) pDataSet.get(sPrefix + "SMX/Values/P_BAT/value"));
                P_CONS = -P_PCC - P_PV - P_BAT;
                if(P_PCC<0) P_BAT=P_BAT+P_PCC;
                System.out.println("Hello: P_CONS="+P_CONS+"\tP_PV="+P_PV+"\tP_PCC="+P_PCC+"\tP_BAT="+P_BAT);
                pDataSet.put(sPrefix+"SMX/Values/P_BAT/value", Double2DecPointStr(P_BAT,2));

            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }

    public void LoopFunction() {
        try {
            
        } catch (Exception e) {

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
