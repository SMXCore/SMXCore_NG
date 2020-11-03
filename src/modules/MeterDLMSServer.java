/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import gurux.GXDLMSBase;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import util.PropUtil;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Enumeration;
import gurux.dlms.enums.InterfaceType;

/**
 *
 * @author cristi
 */
public class MeterDLMSServer extends Module {

    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        PropUtil.LoadFromFile(pDS2DLMSAssociation, PropUtil.GetString(pAttributes, "pDS2DLMSAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);

        iPort = PropUtil.GetInt(pAttributes, "iPort", 4059);

        iLNReferencing = PropUtil.GetInt(pAttributes, "iLNReferencing", 0);
        iInterfaceType = PropUtil.GetInt(pAttributes, "iInterfaceType", 0);
        iPeriodicUpdate = PropUtil.GetInt(pAttributes, "iPeriodicUpdate", 0);

        sObjFile = PropUtil.GetString(pAttributes, "sObjFile", "");
    }

    int iPeriodicUpdate;
    Properties pDLMS2DSAssociation = new Properties();
    Properties pDS2DLMSAssociation = new Properties();
    int iPort = 0;
    int iLNReferencing = 0;
    int iInterfaceType = 0;
    String sObjFile = "";

    GXDLMSBase DLMSServer;

    Properties pDataSet = null;
    String sPrefix = "";
    Thread tLoop = null;
    Thread tProfileCapture = null;

    boolean bLNReferencing = false;

    @Override
    public void Start() {
        try {
            PropUtil.SwitchKeyValue(pDS2DLMSAssociation, pDLMS2DSAssociation);
            bLNReferencing = false;
            if (iLNReferencing == 1) {
                bLNReferencing = true;
            }
            if (iInterfaceType == 0) {
                DLMSServer = new GXDLMSBase(bLNReferencing, InterfaceType.HDLC);
            } else {
                DLMSServer = new GXDLMSBase(bLNReferencing, InterfaceType.WRAPPER);
            }

            DLMSServer.pDLMS2DSAssociation = pDLMS2DSAssociation;
            DLMSServer.pDS2DLMSAssociation = pDS2DLMSAssociation;
            DLMSServer.pDataSet = pDataSet;
            DLMSServer.sPrefix = sPrefix;
            DLMSServer.iPeriodicUpdate = iPeriodicUpdate;

            DLMSServer.LoadObjects(sObjFile);

            if (iPeriodicUpdate == 1) {
                DLMSServer.Update();
                tLoop = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        Loop();
                        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }
                });
                tLoop.start();
            }
            tProfileCapture = new Thread(new Runnable() {

                @Override
                public void run() {
                    CaptureProfiles();
                    // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }
            });
            tProfileCapture.start();

          //  Thread.sleep(60000 - (System.currentTimeMillis() % 60000));
            DLMSServer.Initialize(iPort);
        } catch (Exception e) {
            // Log(Name + "-Open-" + e.getMessage() + "-" + e.getCause());
        }
    }

    public void CaptureProfiles() {
        while (bStop == 0) {
            try {
                Thread.sleep(1000 - (System.currentTimeMillis() % 1000));
                //DLMSServer.Update();
                DLMSServer.UpdateLoadProfiles();
            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }

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

                DLMSServer.Update();

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
