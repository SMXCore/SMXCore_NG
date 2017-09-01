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
 * @author mihai
 */
public class Serial2SocketAndSerial extends Module {

    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        iDebug = PropUtil.GetInt(pAttributes, "iDebug", 0);

        sPortName = PropUtil.GetString(pAttributes, "sPortName", "");
        iPortSpeed = PropUtil.GetInt(pAttributes, "iPortSpeed", 300);
        sPortParam = PropUtil.GetString(pAttributes, "sPortParam", "7E1");

        iReadTimeout = PropUtil.GetInt(pAttributes, "iReadTimeout", 50000);
        iInterCharTimeout = PropUtil.GetInt(pAttributes, "iInterCharTimeout", 1000);
        iCheckEcho = PropUtil.GetInt(pAttributes, "iCheckEcho", 1);

        // iPortSpeed = PropUtil.GetInt(pAttributes, "iPortSpeed", 300);
        sssBridge = new ByteSocketServer();
        sssBridge.setAttributes(pAttributes);
        sssBridge.Initialize();
        iSSBCheckPeriod = PropUtil.GetInt(pAttributes, "iSSBCheckPeriod", 2000);
        iSSBReadTimeout = PropUtil.GetInt(pAttributes, "iSSBReadTimeout", 2000);

        // Serial Port 2
        sPort2Name = PropUtil.GetString(pAttributes, "sPort2Name", "");
        iPort2Speed = PropUtil.GetInt(pAttributes, "iPort2Speed", 300);
        sPort2Param = PropUtil.GetString(pAttributes, "sPort2Param", "7E1");

        iRead2Timeout = PropUtil.GetInt(pAttributes, "iRead2Timeout", 50000);
        iInterChar2Timeout = PropUtil.GetInt(pAttributes, "iInterChar2Timeout", 1000);
        iCheckEcho2 = PropUtil.GetInt(pAttributes, "iCheckEcho2", 1);

        iInactivity2Timeout = PropUtil.GetInt(pAttributes, "iInactivity2Timeout", 30000);

        sReadDisableParam = PropUtil.GetString(pAttributes, "sReadDisableParam", "");
        if (sReadDisableParam.length() < 2) {
            sReadDisableParam = this.sName + "sReadDisableParam";
            pDataSet.put(sReadDisableParam, "0");
        }

        sReadDisabledParam = PropUtil.GetString(pAttributes, "sReadDisabledParam", "");
        if (sReadDisabledParam.length() < 2) {
            sReadDisabledParam = this.sName + "sReadDisabledParam";
            pDataSet.put(sReadDisabledParam, "1");
        }

    }
    Properties pDataSet = null;

    ByteSocketServer sssBridge = null;
    int iSSBReadTimeout = 2000;
    int iSSBCheckPeriod = 10;

    int iReadDisable = 0;
    int iReadDisabledParam = 1;
    String sReadDisableParam = "";
    String sReadDisabledParam = "";

    Thread tMeterCalc = null;

    String sPortName = "";
    int iPortSpeed = 300;
    String sPortParam = "";

    int iReadTimeout = 50000;
    int iInterCharTimeout = 1000;
    int iCheckEcho = 1;

    // Serial Port 2
    String sPort2Name = "";
    int iPort2Speed = 300;
    String sPort2Param = "";

    int iRead2Timeout = 50000;
    int iInterChar2Timeout = 1000;
    int iCheckEcho2 = 1;
    int iInactivity2Timeout = 30000;

    Thread tSerialPort2 = null;

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

            tSerialPort2 = new Thread(new Runnable() {

                @Override
                public void run() {
                    ReadSerialPort2();
                }
            });
            tSerialPort2.start();

            sssBridge.Start();

        } catch (Exception e) {
        }
    }
    String sResp2 = "";
    String sCrtResp2 = "";
    String sLastResp2 = "";
    long lIniSysTime2 = 0;
    long lMsgSysTime2 = 0;
    int iAvailable2 = 0;
    int iMemAvailable2 = 0;

    int iPort2Rec = 0;

    public void ReadSerialPort2() {
        while (bStop == 0) {
            try {

                Thread.sleep(10);

                if (serialPort2 == null) {
                    ReOpenPort2();
                }
                if (serialPort2 == null) {
                    continue;
                }
                lIniSysTime2 = System.currentTimeMillis();
                /*               while (System.currentTimeMillis() - lIniSysTime2 < iRead2Timeout) {
                 //sCrtResp = serialPort.readString(65535, iInterCharTimeout);
                 Thread.sleep(iInterChar2Timeout);
                 iAvailable2 = serialPort2.getInputBufferBytesCount();
                 if ((iAvailable2 > 0) && (iAvailable2 == iMemAvailable2)) {
                 break;
                 }
                 if ((iAvailable2 > 0) && (iAvailable2 != iMemAvailable2)) {
                 lIniSysTime2 = System.currentTimeMillis();
                 }
                 iMemAvailable2 = iAvailable2;
                 } */

                //Test
  /*              if (System.currentTimeMillis() % 120000 > 110000) {
                 if (iPort2Rec == 0) {
                 lMsgSysTime2 = System.currentTimeMillis();
                 iPort2Rec = 1;
                 pDataSet.put(sReadDisableParam, "1");
                 sResp2 = "7EA00703215303C77E";
                 }
                 } */
                Thread.sleep(iInterChar2Timeout);
                iAvailable2 = serialPort2.getInputBufferBytesCount();
                if (iAvailable2 > 0) {
                    sResp2 = ConvertUtil.ByteStr2HexStr(serialPort2.readBytes(iAvailable2, iInterChar2Timeout));
                    //sResp = serialPort.readHexString(iAvailable, iInterCharTimeout);
                    //System.out.println(sResp);
                    GetAddMsgBytes(sResp2, true);
                    if (iDebug == 1) {
                        System.out.println(System.currentTimeMillis() + " Rec2: " + sResp2);
                    }
                    /*    if (iCheckEcho2 == 1) {
                     if (sResp2.startsWith(sLastResp2)) {
                     sResp2 = sResp2.substring(sLastResp2.length());
                     }
                     }*/
                    if (sResp2.length() > 0) {
                        lMsgSysTime2 = System.currentTimeMillis();
                        iPort2Rec = 1;
                        pDataSet.put(sReadDisableParam, "1");
                    }

                } else {
                    // ReOpen();
                    if ((iPort2Rec == 1) && (System.currentTimeMillis() - lMsgSysTime2 > iInactivity2Timeout)) {
                        iPort2Rec = 0;
                        pDataSet.put(sReadDisableParam, "0");
                        sResp2 = "";

                    }
                }
                //sResp = iec1107util.IecRespClean(sResp);

            } catch (Exception e) {
                ReOpenPort2();
                if (iDebug == 1) {
                    System.out.println(e.getMessage());
                }
            }
        }
    }

    public String sMsgBytes = "";

    public synchronized String GetAddMsgBytes(String sBytes, boolean bAdd) {
        String sTempBytes = "";
        if (bAdd) {
            sMsgBytes += sBytes;
            return sMsgBytes;
        } else {
            sTempBytes = sMsgBytes;
            sMsgBytes = "";
            return sTempBytes;
        }
    }

    public void WritePort2(String sQuery) {
        try {
            if ((sQuery == null) || (sQuery.length() < 1)) {
                sLastResp2 = "";
                return;
            }

            serialPort2.writeBytes(ConvertUtil.HexStr2ByteStr(sQuery));
            sLastResp2 = sQuery;
            if (iDebug == 1) {
                System.out.println(System.currentTimeMillis() + " Port 2 Send: " + sQuery);
            }
        } catch (Exception e) {
            if (iDebug == 1) {
                System.out.println(e.getMessage());
            }
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

                if (iPort2Rec == 0) {
                    sSSBridgeMessage = sssBridge.ReadLastMessage();
                    ServeBridge();
                } else {
                    iReadDisabledParam = PropUtil.GetInt(pDataSet, sReadDisabledParam, 1);
                    if (iReadDisabledParam == 1) {
                        ServePort2();
                    } else {
                        if (System.currentTimeMillis() - lMsgSysTime2 > 20000) {
                            pDataSet.put(sReadDisabledParam, "1");
                        } else {
                            sSSBridgeMessage = sssBridge.ReadLastMessage();
                            ServeBridge();
                        }
                    }
                }

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

    public SerialPort serialPort2 = null;

    public void ReOpenPort2() {
        try {
            try {
                if (serialPort2 != null) {
                    serialPort2.closePort();
                    serialPort2 = null;
                    /// Thread.sleep(1000);
                }
            } catch (Exception e) {
            }
            //Thread.sleep(1000);

            serialPort2 = new SerialPort(sPort2Name);
            serialPort2.openPort();

            SetParam2(iPort2Speed);

            //serialPort.enableReceiveTimeout(iReadTimeOut);
            // serialPort.setDTR(true);
            // serialPort.setRTS(true);
            serialPort2.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);

        } catch (Exception e) {
            try {
                Thread.sleep(10);
                if (serialPort2 != null) {
                    serialPort2.closePort();
                }
                serialPort2 = null;
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

            // while (sSSBridgeMessage.length() > 0) {
            //Thread.sleep(200);
            sRec = Exchange(sSSBridgeMessage, -1);

            if (iDebug == 1) {
                if (sSSBridgeMessage.length() + sRec.length() > 0) {
                    System.out.println("Msg: " + sRec);
                }
            }
            if (sRec.length() > 0) {
                sssBridge.SocketWrite(sRec);
            } else {
                //sRec = Exchange(iec1107util.getCloseString(), -1);
            }

            //     sSSBridgeMessage = GetBridgeMessage();
            // }
        } catch (Exception e) {

            if (iDebug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }

    public void ServePort2() {
        String sQuery = "";
        try {

            //while (sResp2.length() > 0) {
            //Thread.sleep(200);
            sQuery = GetAddMsgBytes("", false);
            if (sQuery.length() > 0) {
                serialPort.writeBytes(ConvertUtil.HexStr2ByteStr(sQuery));
                lMsgSysTime2 = System.currentTimeMillis();
                if (iDebug == 1) {
                    System.out.println(System.currentTimeMillis() + " Send: " + sQuery);
                }
                sQuery = "";
            }
            iAvailable = serialPort.getInputBufferBytesCount();
            sResp="";
            if (iAvailable > 0) {
                sResp = ConvertUtil.ByteStr2HexStr(serialPort.readBytes(iAvailable, iInterCharTimeout));

                if (iDebug == 1) {
                    System.out.println(System.currentTimeMillis() + " Rec: " + sResp);
                }
            }
            sRec = sResp;
            /*           sRec = Exchange(sResp2, -1);
             sResp2 = ""; */
            if (iDebug == 1) {
                if (sRec.length() > 0) {
                    System.out.println("Port 2 Write: " + sRec);
                }
            }
            if (sRec.length() > 0) {
                WritePort2(sRec);
                lMsgSysTime2 = System.currentTimeMillis();

            } else {
                //sRec = Exchange(iec1107util.getCloseString(), -1);
            }

            //sSSBridgeMessage = GetBridgeMessage();
            // }
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

    public void SetParam2(int iNewBoud) {
        try {
            if (sPort2Param.length() < 2) {
                serialPort2.setParams(iNewBoud,
                        SerialPort.DATABITS_8,
                        SerialPort.STOPBITS_1,
                        SerialPort.PARITY_NONE);
            } else if (('7' == sPort2Param.charAt(0)) && ('E' == sPort2Param.charAt(1))) {
                serialPort2.setParams(iNewBoud,
                        SerialPort.DATABITS_7,
                        SerialPort.STOPBITS_1,
                        SerialPort.PARITY_EVEN);

            } else if (('8' == sPort2Param.charAt(0)) && ('E' == sPort2Param.charAt(1))) {
                serialPort2.setParams(iNewBoud,
                        SerialPort.DATABITS_8,
                        SerialPort.STOPBITS_1,
                        SerialPort.PARITY_EVEN);
            } else {
                serialPort2.setParams(iNewBoud,
                        SerialPort.DATABITS_8,
                        SerialPort.STOPBITS_1,
                        SerialPort.PARITY_NONE);
            }
        } catch (Exception e) {
            ReOpenPort2();
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
