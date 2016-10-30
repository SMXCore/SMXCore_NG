/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.io.BufferedReader;
import java.text.SimpleDateFormat;
import java.util.Date;
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
public class RunProccess extends Module {

    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        //    PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        sCmdLine = PropUtil.GetString(pAttributes, "sCmdLine", "");
        sStartPath = PropUtil.GetString(pAttributes, "sStartPath", "");
        iTimeOut = PropUtil.GetInt(pAttributes, "iTimeOut", 10000);
        iPause = PropUtil.GetInt(pAttributes, "iPause", 2000);
    }

//    Properties pAssociation = new Properties();
//
    Properties pDataSet = null;
    String sPrefix = "";
    Thread tLoop = null;
    Thread tWachDog = null;
    int iTimeOut = 10000;
    int iPause = 10000;
    long lLastRead = 0;

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
            if (tWachDog == null) {
                tWachDog = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (true) {
                            try {
                                Thread.sleep(iTimeOut);
                                if (!isRunning(proc)) {
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

    private final SimpleDateFormat sdfDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    BufferedReader br = null;
    String sLine = "";

    public void Loop() {

        while (bStop == 0) {
            try {
                Thread.sleep(10);
                try {
                    sLine = br.readLine();
                } catch (Exception ex) {
                    continue;
                }
                pDataSet.put(sPrefix + sName, sdfDate.format(new Date()) + "-" + sLine);
            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }
    /*    public int Pause = 0;
     public int memPause = 0;
     public int bStop = 0;

     public int Debug = 0;

     public long lPeriod = 0;
     public long lIniSysTimeMs = 0;
     public long lMemSysTimeMs = 0;
     public long ldt = 0;
     public double ddt = 0.0;
     public long lDelay = 0;*/
    public int Debug = 0;
    public int bStop = 0;

    private String[] ssCmdLines;
    private String sCmdLine;
    private String sStartPath;
    private Process proc = null;
    ProcessBuilder pb = null;

    public void ReStartProc() {
        try {
            if (proc != null) {
                if (isRunning(proc)) {
                    proc.destroy();
                }
            }
            
            Thread.sleep(iPause);
            
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
