/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import jssc.SerialPort;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import util.ByteSocketServer;
import util.ConvertUtil;
import util.PropUtil;
import util.StringSocketServer;
import util.iec1107util;

/**
 *
 * @author cristi, mihai
 */
public class Serial2Socket extends Module {

    public void Initialize() {
        iDebug = PropUtil.GetInt(pAttributes, "iDebug", 0);

        sPortName = PropUtil.GetString(pAttributes, "sPortName", "");
        iPortSpeed = PropUtil.GetInt(pAttributes, "iPortSpeed", 300);
        sPortParam = PropUtil.GetString(pAttributes, "sPortParam", "7E1");

        iReadTimeout = PropUtil.GetInt(pAttributes, "iReadTimeout", 50000);
        iInterCharTimeout = PropUtil.GetInt(pAttributes, "iInterCharTimeout", 1000);
                                                          
        iPortSpeed = PropUtil.GetInt(pAttributes, "iPortSpeed", 300);

        sssBridge = new ByteSocketServer();
        sssBridge.setAttributes(pAttributes);
        sssBridge.Initialize();
        iSSBCheckPeriod = PropUtil.GetInt(pAttributes, "iSSBCheckPeriod", 2000);
        iSSBReadTimeout = PropUtil.GetInt(pAttributes, "iSSBReadTimeout", 2000);

    }

    ByteSocketServer sssBridge = null;
    int iSSBReadTimeout = 2000;
    int iSSBCheckPeriod = 10;

    Thread tMeterCalc = null;

    String sPortName = "";
    int iPortSpeed = 300;
    String sPortParam = "";

    int iReadTimeout = 50000;
    int iInterCharTimeout = 1000;
    int iCheckEcho = 1;

    @Override
    public void Start() {
        try {
            tMeterCalc = new Thread(new Runnable() {
                @Override
                public void run() {
                    Read();
                }
            });
            tMeterCalc.start();

            sssBridge.Start();

        } catch (Exception e) {
        }
    }

    public void Read() {

        while (bStop == 0) {
            try {

                Thread.sleep(10);

                if (Pause == 1) {
                    if (memPause == 0) {
                        memPause = Pause;
                        lMemSysTimeMs = 0;
                    }
                    continue;
                }
                memPause = Pause;

                if (serialPort == null) {
                    ReOpen();
                }
                if (serialPort == null) {
                    continue;
                }

                sSSBridgeMessage = sssBridge.ReadLastMessage();

                ServeBridge();

            } catch (Exception e) {
                if (iDebug == 1) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }

    String sSSBridgeMessage = "";
    String sResp = "";
    String sCrtResp = "";
    long lIniSysTime = 0;
    int iAvailable = 0;
    int iMemAvailable = 0;

    public String Exchange(String sQuery, int iPortSpeedCh) {
        try {
            if ((sQuery == null) || (sQuery.length() < 1)) {
                return "";
            }
            sResp = "";
            iAvailable = 0;
            lIniSysTime = System.currentTimeMillis();

            serialPort.writeBytes(ConvertUtil.HexStr2ByteStr(sQuery));

            if (iDebug == 1) {
                System.out.println(System.currentTimeMillis() + " Send: " + sQuery);
            }

            //System.out.println(sQuery);
            if (iPortSpeedCh > 0) {
                Thread.sleep(250);
                SetParam(iPortSpeedCh);
            } else {
                //             Thread.sleep(200);

            }
            while (System.currentTimeMillis() - lIniSysTime < iReadTimeout) {
                //sCrtResp = serialPort.readString(65535, iInterCharTimeout);
                Thread.sleep(iInterCharTimeout);
                iAvailable = serialPort.getInputBufferBytesCount();
                if ((iAvailable > 0) && (iAvailable == iMemAvailable)) {
                    break;
                }
                if ((iAvailable > 0) && (iAvailable != iMemAvailable)) {
                    lIniSysTime = System.currentTimeMillis();
                }
                iMemAvailable = iAvailable;
            }
            if (iAvailable > 0) {
                sResp = ConvertUtil.ByteStr2HexStr(serialPort.readBytes(iAvailable, iInterCharTimeout));
                //sResp = serialPort.readHexString(iAvailable, iInterCharTimeout);
                //System.out.println(sResp);
                if (iDebug == 1) {
                    System.out.println(System.currentTimeMillis() + " Rec: " + sResp);
                }
            } else {
                // ReOpen();
            }
            //sResp = iec1107util.IecRespClean(sResp);
            if (iCheckEcho == 1) {
                if (sResp.startsWith(sQuery)) {
                    sResp = sResp.substring(sQuery.length());
                }
            }
        } catch (Exception e) {
            ReOpen();
        }

        return sResp;
    }

    final private SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss SSS");
    final private SimpleDateFormat sdfDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

    public int Pause = 0;
    public int memPause = 0;
    public int bStop = 0;

    public int iDebug = 0;

    public long lPeriod = 0;
    public long lIniSysTimeMs = 0;
    public long lMemSysTimeMs = 0;
    public long ldt = 0;
    public double ddt = 0.0;
    public long lDelay = 0;

    String sDate = "";

    public SerialPort serialPort = null;

    public void ReOpen() {
        try {
            try {
                if (serialPort != null) {
                    serialPort.closePort();
                    serialPort = null;
                    /// Thread.sleep(1000);
                }
            } catch (Exception e) {
            }
            //Thread.sleep(1000);

            serialPort = new SerialPort(sPortName);
            serialPort.openPort();

            SetParam(iPortSpeed);

            //serialPort.enableReceiveTimeout(iReadTimeOut);
            // serialPort.setDTR(true);
            // serialPort.setRTS(true);
            serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);

        } catch (Exception e) {
            try {
                Thread.sleep(10);
                if (serialPort != null) {
                    serialPort.closePort();
                }
                serialPort = null;
            } catch (Exception ex) {
            }

            if (iDebug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }

    String sRec = "";
    String sSpeed = "";
    int iNewBoud = 9600;
    int iStage = 0;

    public void ServeBridge() {

        try {

            while (sSSBridgeMessage.length() > 0) {

                //Thread.sleep(200);
                sRec = Exchange(sSSBridgeMessage, -1);

                if (iDebug == 1) {
                    System.out.println("Msg: " + sRec);
                }
                if (sRec.length() > 0) {
                    sssBridge.SocketWrite(sRec);
                } else {
                    //sRec = Exchange(iec1107util.getCloseString(), -1);
                }

                sSSBridgeMessage = GetBridgeMessage();
            }
        } catch (Exception e) {

            if (iDebug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }

    public static String String2HexVals(String sMessage) {
        String sRet = "";
        int i;
        int n;
        try {
            n = sMessage.length();
            for (i = 0; i < n; i++) {
                sRet += Integer.toString(sMessage.charAt(i), 16).toUpperCase();
            }
        } catch (Exception e) {

        }
        return sRet;
    }

    public String GetBridgeMessage() {
        String sReturn = "";
        int i;
        try {
            for (i = 0; i < iSSBReadTimeout; i += iSSBCheckPeriod) {
                sReturn = sssBridge.ReadLastMessage();
                if (sReturn.length() > 0) {
                    return sReturn;
                }
                Thread.sleep(iSSBCheckPeriod);
            }
        } catch (Exception e) {

        }
        return sReturn;
    }

    public void SetParam(int iNewBoud) {
        try {
            if (sPortParam.length() < 2) {
                serialPort.setParams(iNewBoud,
                        SerialPort.DATABITS_8,
                        SerialPort.STOPBITS_1,
                        SerialPort.PARITY_NONE);
            } else if (('7' == sPortParam.charAt(0)) && ('E' == sPortParam.charAt(1))) {
                serialPort.setParams(iNewBoud,
                        SerialPort.DATABITS_7,
                        SerialPort.STOPBITS_1,
                        SerialPort.PARITY_EVEN);

            } else if (('8' == sPortParam.charAt(0)) && ('E' == sPortParam.charAt(1))) {
                serialPort.setParams(iNewBoud,
                        SerialPort.DATABITS_8,
                        SerialPort.STOPBITS_1,
                        SerialPort.PARITY_EVEN);
            } else {
                serialPort.setParams(iNewBoud,
                        SerialPort.DATABITS_8,
                        SerialPort.STOPBITS_1,
                        SerialPort.PARITY_NONE);
            }
        } catch (Exception e) {
            ReOpen();
            if (iDebug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }

    iec1107util i1107u = new iec1107util();
    String[] sIecLines;
    String[] sIecCols;
    String[] sVals;
    String sVar;
    String sVal;
    String sUM;
    Date dtMeter;
    int iFirstPart = 0;
    String[] ssOBIS;
    String[] ssOBISdet;
    String[] ssTemp;

    int iPar = 0;

    private final SimpleDateFormat sdfIec = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
    private final SimpleDateFormat sdfUS = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    public SimpleDateFormat sdfDateFormat = null;

}
