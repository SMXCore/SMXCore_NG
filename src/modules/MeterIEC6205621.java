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
import util.PropUtil;
import util.StringSocketServer;
import util.iec1107util;

/**
 *
 * @author cristi
 */
public class MeterIEC6205621 extends Module {

    public void Initialize() {
        iDebug = PropUtil.GetInt(pAttributes, "iDebug", 0);

        PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetLong(pAttributes, "lPeriod", 1000);

        sPortName = PropUtil.GetString(pAttributes, "sPortName", "");
        iPortSpeed = PropUtil.GetInt(pAttributes, "iPortSpeed", 300);
        sPortParam = PropUtil.GetString(pAttributes, "sPortParam", "7E1");

        iCheckBoudSwitch = PropUtil.GetInt(pAttributes, "iCheckBoudSwitch", 1);

        iReadTimeout = PropUtil.GetInt(pAttributes, "iReadTimeout", 50000);
        iInterCharTimeout = PropUtil.GetInt(pAttributes, "iInterCharTimeout", 1000);

        sDateSep = PropUtil.GetString(pAttributes, "sDateSep", "\\.");
        sTimeSep = PropUtil.GetString(pAttributes, "sTimeSep", "\\:");

        sAddress = PropUtil.GetString(pAttributes, "sAddress", "");

        iEnableSSBrdge = PropUtil.GetInt(pAttributes, "iEnableSSBrdge", 0);
        if (iEnableSSBrdge == 1) {
            sssBridge = new StringSocketServer();
            sssBridge.setAttributes(pAttributes);
            sssBridge.Initialize();
            iSSBCheckPeriod = PropUtil.GetInt(pAttributes, "iSSBCheckPeriod", 2000);
            iSSBReadTimeout = PropUtil.GetInt(pAttributes, "iSSBReadTimeout", 2000);
        }

        try {
            sdfDT = new SimpleDateFormat(PropUtil.GetString(pAssociation, "DateTimeFormat", "yyMMddHHmmss"));
            sdfD = new SimpleDateFormat(PropUtil.GetString(pAssociation, "DateFormat", "yyMMdd"));
            sdfT = new SimpleDateFormat(PropUtil.GetString(pAssociation, "TimeFormat", "HHmmss"));
        } catch (Exception e) {
        }
    }

    Properties pAssociation = new Properties();

    int iEnableSSBrdge = 0;
    StringSocketServer sssBridge = null;
    int iSSBReadTimeout = 2000;
    int iSSBCheckPeriod = 10;

    Properties pDataSet = null;
    String sPrefix = "";
    Thread tMeterCalc = null;

    String sAddress = "";
    String sPortName = "";
    int iPortSpeed = 300;
    String sPortParam = "";

    int iReadTimeout = 50000;

    int iCheckBoudSwitch = 1;
    int iInterCharTimeout = 1000;
    int iCheckEcho = 1;

    SimpleDateFormat sdfDT;
    SimpleDateFormat sdfD;
    SimpleDateFormat sdfT;

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
            if (iEnableSSBrdge == 1) {
                sssBridge.Start();
            }

        } catch (Exception e) {
        }
    }

    public void Read() {

        while (bStop == 0) {
            try {
                if (lPeriod > 0) {
                    //lIniSysTimeMs = System.currentTimeMillis();
                    lDelay = lPeriod - (System.currentTimeMillis() % lPeriod);
                    Thread.sleep(lDelay);
                } else {
                    Thread.sleep(10);
                }
                if (lMemSysTimeMs == 0) {
                    lMemSysTimeMs = System.currentTimeMillis();
                    //continue;
                }

                sDate = sdfDate.format(new Date());

                lIniSysTimeMs = System.currentTimeMillis();
                ldt = lIniSysTimeMs - lMemSysTimeMs;
                lMemSysTimeMs = lIniSysTimeMs;
                ddt = (double) ldt / 1000.0 / 3600.0;

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

                if (iEnableSSBrdge == 1) {
                    sSSBridgeMessage = sssBridge.ReadLastMessage();
                    sSSBridgeMessage = iec1107util.IecRespClean(sSSBridgeMessage);
                    if (sSSBridgeMessage.length() > 1) {
                        ServeBridge();
                    } else {
                        ReadVals();
                    }
                } else {
                    ReadVals();
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
            serialPort.writeBytes(sQuery.getBytes());

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
                sResp = serialPort.readString(iAvailable, iInterCharTimeout);
                //System.out.println(sResp);
                if (iDebug == 1) {
                    System.out.println(System.currentTimeMillis() + " Rec: " + sResp);
                }
            } else {
                // ReOpen();
            }
            sResp = iec1107util.IecRespClean(sResp);
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
                if (sSSBridgeMessage.startsWith("/?")) {
                    iStage = 0;
                    ReOpen();
                    // SetParam(iPortSpeed);
                    sRec = Exchange(sSSBridgeMessage, -1);
                    if (sRec.length() < 1) {
                        return;
                    }
                    if (iCheckBoudSwitch == 1) {
                        sssBridge.SocketWrite(sRec);
                        sSSBridgeMessage = GetBridgeMessage();
                        if (sRec.length() < 1) {
                            //sRec = Exchange(iec1107util.getCloseString(), -1);
                            return;
                        }

                        sSpeed = sSSBridgeMessage.substring(2, 3);
                        if (sSpeed.equals("6")) {
                            iNewBoud = 19200;
                        } else if (sSpeed.equals("5")) {
                            iNewBoud = 9600;
                        } else if (sSpeed.equals("4")) {
                            iNewBoud = 4800;
                        } else if (sSpeed.equals("3")) {
                            iNewBoud = 2400;
                        } else if (sSpeed.equals("2")) {
                            iNewBoud = 1200;
                        } else if (sSpeed.equals("1")) {
                            iNewBoud = 600;
                        } else if (sSpeed.equals("0")) {
                            iNewBoud = 300;
                        }
                        sRec = Exchange(sSSBridgeMessage, iNewBoud);
                        if (sRec.length() < 1) {
                            return;
                        }
                    }
                    //sSSBridgeMessage = GetBridgeMessage();
                    sssBridge.SocketWrite(sRec);
                    iStage = 1;

                } else {
                    //Thread.sleep(200);
                    sRec = Exchange(sSSBridgeMessage, -1);
                    if ((iStage == 0)) {
                        iStage = 1;
                        if (sSSBridgeMessage.indexOf("P0" + iec1107util.sSTX) < 0) {

                        } else {
                            iStage = 1;
                            if (!sRec.startsWith(iec1107util.sSOH)) {
                                sRec = iec1107util.sSOH + sRec.indexOf("P0" + iec1107util.sSTX);
                            }
                        }
                    }
                    if ((iStage == 1)) {
                        if (!sRec.startsWith(iec1107util.sACK)) {
                            sRec = iec1107util.sACK;
                        }
                        iStage = 2;
                    }
                    if ((iStage == 2)) {
                        // if (!sRec.startsWith(iec1107util.sSTX)) {
                        if ((sRec.indexOf(")") > 0) && (sRec.length() > 32)) {
                            sRec = iec1107util.sSTX + "(" + sRec.substring(sRec.indexOf(")") - 32);
                        } else {
                            sRec = iec1107util.sACK;
                        }
                        //}
                    }
                    if (iDebug == 1) {
                        System.out.println("Stage: " + iStage);
                        System.out.println("Msg: " + sRec);
                    }
                    if (sRec.length() > 0) {
                        sssBridge.SocketWrite(sRec);
                    } else {
                        //sRec = Exchange(iec1107util.getCloseString(), -1);
                    }
                }
                sSSBridgeMessage = GetBridgeMessage();
            }
        } catch (Exception e) {

            if (iDebug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }

    public String GetBridgeMessage() {
        String sReturn = "";
        int i;
        try {
            for (i = 0; i < iSSBReadTimeout; i += iSSBCheckPeriod) {
                sReturn = sssBridge.ReadLastMessage();
                if (sReturn.length() > 0) {
                    return iec1107util.IecRespClean(sReturn);
                }
                Thread.sleep(iSSBCheckPeriod);
            }
        } catch (Exception e) {

        }
        return sReturn;
    }

    public void ReadVals() {

        try {
            ReOpen();
            // SetParam(iPortSpeed);
            sRec = Exchange(util.iec1107util.getInitString(sAddress), -1);
            if (sRec.length() < 6) {
                //sRec = Exchange(iec1107util.getCloseString(), -1);
                return;
            }
            if (iCheckBoudSwitch == 1) {
                sSpeed = sRec.substring(4, 5);
                if (sSpeed.equals("6")) {
                    iNewBoud = 19200;
                } else if (sSpeed.equals("5")) {
                    iNewBoud = 9600;
                } else if (sSpeed.equals("4")) {
                    iNewBoud = 4800;
                } else if (sSpeed.equals("3")) {
                    iNewBoud = 2400;
                } else if (sSpeed.equals("2")) {
                    iNewBoud = 1200;
                } else if (sSpeed.equals("1")) {
                    iNewBoud = 600;
                } else if (sSpeed.equals("0")) {
                    iNewBoud = 300;
                }

                //Thread.sleep(200);
                sRec = Exchange(util.iec1107util.getReadoutString(sSpeed), iNewBoud);
            }
            if (sRec.length() > 4) {
                WriteIecVals(sRec);
            } else {
                //sRec = Exchange(iec1107util.getCloseString(), -1);
            }

        } catch (Exception e) {

            if (iDebug == 1) {
                System.out.println(e.getMessage());
            }
        }
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

    public String sIECDateSep = "-";
    public String sIECTimeSep = ":";
    public String sDateSep = ".";
    public String sTimeSep = ":";

    int iPar = 0;
    String sWriteMeterDate = "";
    
    String sTemp="";

    public void WriteIecVals(String sIec) {

        try {

            sWriteMeterDate = sDate;
            sIec = util.iec1107util.IecRespClean(sIec);
            if (sIec.startsWith(" ")) {
                sIec = sIec.replaceAll(" ", "");
            }
            sIec = sIec.replaceAll("\r", "");
            sIec = sIec.replaceAll("\n", "~");
            sIec = sIec.replaceAll("\\)", "");
            sIecLines = sIec.split("~");

            for (String sLine : sIecLines) {
                try {
                    if (sLine.length() < 4) {
                        continue;
                    }
                    if (sLine.charAt(0) < '0') {
                        sLine = sLine.substring(1);
                    }
                    sIecCols = sLine.split("\\(");

                    iFirstPart = sIecCols[0].indexOf(":");
                    if (iFirstPart < 0) {
                        sIecCols[0] = "1-1:" + sIecCols[0];
                    }
                    ssOBIS = sIecCols[0].split("\\.");
                    if (ssOBIS.length == 2) {
                        sIecCols[0] = sIecCols[0] + ".0";
                    }
                    sIecCols[0] = sIecCols[0].replaceAll(":", "-");
                    ssOBISdet = i1107u.getObisDet(sIecCols[0], pAssociation);

                    if (ssOBISdet == null) {
                        continue;
                    }

                    if (iDebug == 1) {
                        System.out.println("sIecCols[0]=" + sIecCols[0] + "  " + "ssOBISdet=" + ssOBISdet[1]);
                    }
                    sVals = sIecCols[1].split("\\*");

                    if (ssOBISdet[2].equals("s") ) { //String
                        pDataSet.put(sPrefix + ssOBISdet[1], sVals[0]);
                    } else if (ssOBISdet[2].equals("tr") ) { //Index
                        pDataSet.put(sPrefix + ssOBISdet[1], Double.toString(Double.parseDouble(sVals[0])
                                * Double.parseDouble(ssOBISdet[3])));
                    } else if (ssOBISdet[2].equals("rr")) { //Value
                        pDataSet.put(sPrefix + ssOBISdet[1], Double.toString(Double.parseDouble(sVals[0])
                                * Double.parseDouble(ssOBISdet[3])));
                    } else if (ssOBISdet[2].equals("dtf")) { //Date Time 
                        dtMeter = sdfDT.parse(sVals[0]);
                        pDataSet.put(sPrefix + ssOBISdet[1], sdfUS.format(dtMeter));
                    } else if (ssOBISdet[2].equals("df")) { //Date  
                        dtMeter = sdfD.parse(sVals[0]);
                        pDataSet.put(sPrefix + ssOBISdet[1], sdfUSd.format(dtMeter));
                    } else if (ssOBISdet[2].equals("tf")) { // Time 
                        dtMeter = sdfT.parse(sVals[0]);
                        pDataSet.put(sPrefix + ssOBISdet[1], sdfUSt.format(dtMeter));
                    }else if (ssOBISdet[2].equals("sh")) { // String hex 
                        sTemp=util.ConvertUtil.HexStr2Str(sVals[0]);;
                        pDataSet.put(sPrefix + ssOBISdet[1], sTemp);
                    } else if (ssOBISdet[2].equals("d")) { //Date
                        if (sDateSep.equals(".")) {
                            sDateSep = "\\.";
                        }

                        sVals[0] = sVals[0].replaceAll(",", sIECDateSep);//fix some Date Format
                        sVals[0] = sVals[0].replaceAll(sDateSep, sIECDateSep);
                        ssTemp = sVals[0].split(sIECDateSep);
                        sVals[0] = ssTemp[0] + sIECDateSep + ssTemp[1] + sIECDateSep + ssTemp[2];
                        pDataSet.put(sPrefix + ssOBISdet[1], sVals[0]);
                    } else if (ssOBISdet[2].equals("t")) { //Time
                        ssTemp = sVals[0].split(sTimeSep);
                        sVals[0] = ssTemp[0] + sIECTimeSep + ssTemp[1] + sIECTimeSep + ssTemp[2];
                        pDataSet.put(sPrefix + ssOBISdet[1], sVals[0]);
                    }
                } catch (Exception exp) {
                    continue;
                }
                if (sVals.length > 1) {
                    pDataSet.put(sPrefix + ssOBISdet[1] + "-UM", sVals[1]);

                }
                for (iPar = 2; iPar < sVals.length; iPar++) {
                    pDataSet.put(sPrefix + ssOBISdet[1] + "-P" + Integer.toString(iPar - 1), sVals[iPar]);
                }
                //PropUtil.PrintProp(pDataSet);
                pDataSet.put(sPrefix + ssOBISdet[1] + "-ts", sWriteMeterDate);
            }

            if ((pDataSet.get(sPrefix + "mDate") != null) && (pDataSet.get(sPrefix + "mTime") != null)) {
                sVal = (String) pDataSet.get(sPrefix + "mDate") + " " + (String) pDataSet.get(sPrefix + "mTime");
                try {
                    if (sdfDateFormat != null) {
                        dtMeter = sdfDateFormat.parse(sVal);
                    } else {
                        dtMeter = sdfIec.parse(sVal);
                    }

                    pDataSet.put(sPrefix + "Date", sdfUS.format(dtMeter));

                } catch (Exception exd) {

                }

            } else {

                pDataSet.put(sPrefix + "Date", sDate);
            }
            pDataSet.put(sPrefix + "Date-ts", sDate);
            if (iDebug == 1) {
                util.PropUtil.PrintProp(pDataSet);
            }

        } catch (Exception e) {
            if (iDebug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }
    private final SimpleDateFormat sdfDSMR = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
    private final SimpleDateFormat sdfIec = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
    private final SimpleDateFormat sdfUS = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    private final SimpleDateFormat sdfUSd = new SimpleDateFormat("MM/dd/yyyy");
    private final SimpleDateFormat sdfUSt = new SimpleDateFormat("HH:mm:ss");
    public SimpleDateFormat sdfDateFormat = null;

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
            if (iDebug == 1) {
                System.out.println(e.getMessage());
            }
        }
        return sResult;
    }

}
