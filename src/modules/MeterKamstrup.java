/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.io.BufferedReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.Properties;
import util.PropUtil;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.Enumeration;

/**
 *
 * @author cristi
 */
public class MeterKamstrup extends Module {

    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        //    PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        //     lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);
        sCmdLine = PropUtil.GetString(pAttributes, "sCmdLine", "");
        sStartPath = PropUtil.GetString(pAttributes, "sStartPath", "");
        iTimeOut = PropUtil.GetInt(pAttributes, "iTimeOut", 30000);
        iPause = PropUtil.GetInt(pAttributes, "iPause", 30000);
        iDebug = PropUtil.GetInt(pAttributes, "iDebug", 0);
        lPeriod = PropUtil.GetLong(pAttributes, "lPeriod", 5000);
    }

//    Properties pAssociation = new Properties();
//
    Properties pDataSet = null;
    String sPrefix = "";
    Thread tLoop = null;
    Thread tWachDog = null;
    int iTimeOut = 30000;
    int iPause = 30000;
    long lLastRead = 0;
    int iDebug = 0;
    long lPeriod = 0;
    
    @Override
    public void Start() {
        try {
            sdfDate.setTimeZone(TimeZone.getDefault());
            tLoop = new Thread(new Runnable() {

                @Override
                public void run() {                
                    Loop();              
  // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }
            });
            tLoop.start();
            if (tWachDog == null) {
                tWachDog = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (true) {
                            try {
                                Thread.sleep(1000);
                                if (System.currentTimeMillis() - lLastRead > iTimeOut) {
                                    ReStartProc();
                                }
                            } catch (Exception e) {

                            }
                        }
                    }
                });
                tWachDog.start();
            }

        } catch (Exception e) {
            // Log(Name + "-Open-" + e.getMessage() + "-" + e.getCause());
        }
    }

    BufferedReader br = null;
    String sLine = "";
    String[] ssLineVars = null;
    String sDate = "";
    String sTime = "";

    private final SimpleDateFormat sdfDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    
    public void Loop() {
        
        
        while (bStop == 0) {
          try {  
                Thread.sleep(10);
                try {
                    sLine = br.readLine();
                } catch (Exception ex) {

                    ReStartProc();
                    continue;
                }
                if ((iDebug == 1) && (sLine!=null)) {
                    System.out.println(sLine);
                }
                lLastRead = System.currentTimeMillis();
                if ((sLine == null) || (sLine.length() < 1)) {
                    ReStartProc();
                    continue;
                }

                while (sLine.contains("  ")) {
                    sLine = sLine.replaceAll("  ", " ");
                }

                sLine = sLine.replaceAll(" ", "\t");
                ssLineVars = sLine.split("\t");
                if (ssLineVars.length < 2) {
                    
                    continue;
                }
                if ((ssLineVars[0].length() < 1) || (ssLineVars[1].length() < 1) || ("None".equals(ssLineVars[1]))) {
                    
                    continue;
                }

                if ("Date".equals(ssLineVars[0])) {

                        sDate = ssLineVars[1];
                        if (sTime.length() > 0) {
                            pDataSet.put(sPrefix + "Date", sDate + " " + sTime);
                            pDataSet.put(sPrefix + "Date" + "-ts", sdfDate.format(new Date()));
                        }

                } else if ("Time".equals(ssLineVars[0])) {

                        sTime = ssLineVars[1];

                } else {
                    pDataSet.put(sPrefix + ssLineVars[0], ssLineVars[1]);
                    pDataSet.put(sPrefix + ssLineVars[0] + "-ts",
                            sdfDate.format(new Date()));

                }

        
            } catch (Exception e) {
                if (iDebug == 1) {
                    System.out.println(e.getMessage());
                }
            }
        }
        

    }

    public int Pause = 0;
    public int memPause = 0;
    public int bStop = 0;

    public int Debug = 0;

    public long lIniSysTimeMs = 0;
    public long lMemSysTimeMs = 0;
    public long ldt = 0;
    public double ddt = 0.0;
    public long lDelay = 0;

    private String[] ssCmdLines;
    private String sCmdLine;
    private String sStartPath;
    private Process proc = null;
    ProcessBuilder pb = null;

    public void ReStartProc() {
        try {
            if (lPeriod > 0) {
                lDelay = lPeriod - (System.currentTimeMillis() % lPeriod);
                Thread.sleep(lDelay);
            } else {
                Thread.sleep(10);
            }
            
            if (proc != null) {
                if (isRunning(proc)) {
                    try {
                        br.close();
                    } catch (Exception ex2) {
                        System.out.println(ex2.getMessage());
                    }
                    proc.destroy();
                    Thread.sleep(iPause);
                }
            }
            ssCmdLines = sCmdLine.split(" ");
            pb = new ProcessBuilder(ssCmdLines);
            if (sStartPath.length() > 1) {
                pb.directory(new File(sStartPath));
            }
            pb.redirectErrorStream(true);

            proc = pb.start();
            br = new BufferedReader(new InputStreamReader(proc.getInputStream()));

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public static boolean isRunning(Process process) {
        try {
            if (process == null) {
                return false;
            }
            process.exitValue();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

}
