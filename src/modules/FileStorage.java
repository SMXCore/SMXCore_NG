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
public class FileStorage extends Module {

    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        PropUtil.LoadFromFile(pStoreAssociation, PropUtil.GetString(pAttributes, "pStoreAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);
        iCommpress = PropUtil.GetInt(pAttributes, "iCommpress ", 1);
        iMove = PropUtil.GetInt(pAttributes, "iMove ", 1);
        sFileName = PropUtil.GetString(pAttributes, "sFileName", sName);
        sFileExt = PropUtil.GetString(pAttributes, "sFileExt", "");
        sSeparator = PropUtil.GetString(pAttributes, "sSeparator", "\t");

    }

    int iCommpress;
    int iMove;

    Properties pStoreAssociation = new Properties();

    Properties pDataSet = null;
    String sPrefix = "";
    Thread tStoreLoop = null;

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

}
