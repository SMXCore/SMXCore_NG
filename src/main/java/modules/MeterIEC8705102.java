/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.text.DateFormat;
import jssc.SerialPort;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import util.ConvertUtil;
import util.PropUtil;
import util.StringSocketServer;
import util.iec1107util;
import util.iec8705102util;
import static util.iec8705102util.df1;

/**
 *
 * @author cristi
 */
public class MeterIEC8705102 extends Module {

    public void Initialize() {
        iDebug = PropUtil.GetInt(pAttributes, "iDebug", 0);

        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetLong(pAttributes, "lPeriod", 1000);

        sPortName = PropUtil.GetString(pAttributes, "sPortName", "");
        iPortSpeed = PropUtil.GetInt(pAttributes, "iPortSpeed", 300);
        sPortParam = PropUtil.GetString(pAttributes, "sPortParam", "7E1");

        iReadTimeout = PropUtil.GetInt(pAttributes, "iReadTimeout", 50000);
        iInterCharTimeout = PropUtil.GetInt(pAttributes, "iInterCharTimeout", 1000);

        sDateSep = PropUtil.GetString(pAttributes, "sDateSep", "\\.");
        sTimeSep = PropUtil.GetString(pAttributes, "sTimeSep", "\\:");

        sPassword = PropUtil.GetString(pAttributes, "sPassword", "");
        iAddr = PropUtil.GetInt(pAttributes, "sAddress", 0);
        bbAddr = iec8705102util.GetAddr(iAddr);

        htMsgs.put("bbInitQry", iec8705102util.GetFixedLen(bbAddr, (byte) 0x49));
        htMsgs.put("bbInitRsp", iec8705102util.GetFixedLen(bbAddr, (byte) 0x0B));

        htMsgs.put("bbLinkQry", iec8705102util.GetFixedLen(bbAddr, (byte) 0x40));
        htMsgs.put("bbAckRsp", iec8705102util.GetFixedLen(bbAddr, (byte) 0x00));

        htMsgs.put("bbPassQry", iec8705102util.GetPassQry(bbAddr, sPassword));
        htMsgs.put("bbPassRsp", iec8705102util.GetPassRsp(bbAddr, sPassword));

        htMsgs.put("bbCl2Qry1", iec8705102util.GetFixedLen(bbAddr, (byte) 0x5B));
        htMsgs.put("bbCl2Qry2", iec8705102util.GetFixedLen(bbAddr, (byte) 0x7B));

        htMsgs.put("bbDateQry", iec8705102util.GetDateQry(bbAddr, (byte) 0x67));
        htMsgs.put("bbInstValQry", iec8705102util.GetInstValQry(bbAddr, (byte) 0xA2));

        htMsgs.put("bbCloseQry", iec8705102util.GetCloseQry(bbAddr, (byte) 0xBB));
        htMsgs.put("bbCloseRsp", iec8705102util.GetCloseRsp(bbAddr, (byte) 0xBB));
    }

    Properties pDataSet = null;
    String sPrefix = "";
    Thread tMeterCalc = null;

    //String sAddress = "";
    String sPortName = "";
    int iPortSpeed = 300;
    String sPortParam = "";

    int iReadTimeout = 50000;
    int iInterCharTimeout = 1000;
    int iCheckEcho = 1;

    String sPassword = "";
    int iAddr = 0;
    byte[] bbAddr = new byte[2];
    Hashtable<String, byte[]> htMsgs = new Hashtable<String, byte[]>();
    boolean bCon = false;

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

                ReadVals();
                WriteIecVals();

            } catch (Exception e) {
                if (iDebug == 1) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }

    String sResp = "";
    String sCrtResp = "";
    long lIniSysTime = 0;
    int iAvailable = 0;
    int iMemAvailable = 0;

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
            // Execute("bbCloseQry");
            reConnect();
        } catch (Exception e) {
            bCon = false;
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

    public void reConnect() {
        try {
            //Close();
            if (iDebug == 1) {
                System.out.println("Connect: " + iAddr);
            }

            iRec = Execute("bbInitQry");

            if ((iRec < 1) || (!iec8705102util.IsEqual(htMsgs.get("bbInitRsp"), bRec))) {
                bCon = false;
                return;
            }

            iRec = Execute("bbLinkQry");

            if ((iRec < 1) || (!iec8705102util.IsEqual(htMsgs.get("bbAckRsp"), bRec))) {
                bCon = false;
                return;
            }
            iRec = Execute("bbPassQry");

            if ((iRec < 1) || (!iec8705102util.IsEqual(htMsgs.get("bbAckRsp"), bRec))) {

                bCon = false;
                return;
            }

            iRec = Execute("bbCl2Qry1");

            if ((iRec < 1) || (!iec8705102util.IsEqual(htMsgs.get("bbPassRsp"), bRec))) {
                bCon = false;
                return;
            }

            iRec = Execute("bbDateQry");

            if ((iRec < 1) || (!iec8705102util.IsEqual(htMsgs.get("bbAckRsp"), bRec))) {
                bCon = false;
                return;
            }
            dtCommDateRsp = new Date();
            iRec = Execute("bbCl2Qry1");

            if ((iRec < 1) || (!iec8705102util.RespCheck(bRec, 0, iRec)[0].equals("OK"))) {
                bCon = false;
                return;
            }

            dtMeterDate = iec8705102util.DecodeDateASDU103(Params, bRec, 14, 6);
            Params.put("MeterDate", df1.format(dtMeterDate));

//iLastControl = 0x10;
        } catch (Exception e) {
            bCon = false;
            return;
        }
        bCon = true;
        return;
    }

    String sRec = "";
    String sSpeed = "";
    int iNewBoud = 9600;
    int iStage = 0;

    public void ReadVals() {

        try {
            // ReOpen();
            // SetParam(iPortSpeed);

            if (!bCon) {
                ReOpen();
            }
            if (!bCon) {
                return;
            }

            ReadDate();
            //reConnect();
            if (!bCon) {
                return;
            }

            ReadValInst();
            //reConnect();
            if (!bCon) {
                return;
            }

        } catch (Exception e) {

            if (iDebug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }

    public int Execute(String sMsgID) {
        //sRec = Exchange(ByteArr2String(htMsgs.get(sMsgID)), -1);
        try {

            String sQuery = ConvertUtil.ByteStr2HexStr(htMsgs.get(sMsgID));
            sResp = "";
            iAvailable = 0;
            lIniSysTime = System.currentTimeMillis();
            serialPort.writeBytes(htMsgs.get(sMsgID));

            if (iDebug == 1) {
                System.out.println(System.currentTimeMillis() + " Send: " + sQuery);
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
                bRec = serialPort.readBytes(iAvailable, iInterCharTimeout);
                //sResp = serialPort.readString(iAvailable, iInterCharTimeout);
                sResp = ConvertUtil.ByteStr2HexStr(bRec);
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
                    bRec = ConvertUtil.HexStr2ByteStr(sResp);
                }
            }
        } catch (Exception e) {
            ReOpen();
        }

        // bRec = sRec.getBytes();
        iRec = bRec.length;
        return iRec;
    }

    int iRec = 0;
    byte[] bRec = null;
    //String sRec="";
    Date dtMeterDate = new Date(1900, 1, 1);
    Date dtCommDateRsp = new Date(1900, 1, 1);
    Date dtCommInstRsp = new Date(1900, 1, 1);

    Properties Params = new Properties();

    public void ReadValInst() {
        int iTmp = 0;
        try {
            iRec = Execute("bbCl2Qry1");

            if ((iRec < 1) || (!iec8705102util.RespCheck(bRec, 0, iRec)[0].equals("OK"))) {
                //if ((iRec < 1) || (!iec8705102util.IsEqual(htMsgs.get("bbPassRsp"), bRec))) {
                bCon = false;
                return;
            }

            iRec = Execute("bbInstValQry");

            if ((iRec < 1) || (!iec8705102util.IsEqual(htMsgs.get("bbAckRsp"), bRec))) {
                bCon = false;
                return;
            }
            dtCommInstRsp = new Date();
            iRec = Execute("bbCl2Qry1");

            if ((iRec < 1) || (!iec8705102util.RespCheck(bRec, 0, iRec)[0].equals("OK"))) {
                bCon = false;
                return;
            }

            iTmp = iec8705102util.DecodeDateASDU192(Params, bRec, 14, 6);
            iTmp = iec8705102util.DecodeDateASDU193(Params, bRec, iTmp + 1, 6);
            iTmp = iec8705102util.DecodeDateASDU194(Params, bRec, iTmp + 1, 6);

            CalcVals(Params, Params);

        } catch (Exception e) {
            bCon = false;
            return;
        }
        bCon = true;
        return;
    }

    public void CalcVals(Properties saMeterParams, Properties saBlockParams) {
        try {
            double dCosFi = 0.0;
            double dSinFi = 0.0;
            String sI = "";

            double U = 0.0;
            double I = 0.0;
            double P = 0.0;
            double Q = 0.0;

            double fiU = 0.0;
            double fiI = 0.0;

            int iPsign = 0;
            int iQsign = 0;
            int iUinvalid = 1;

            double Ptot = 0;
            double Qtot = 0;

            String sDate = saBlockParams.getProperty("InstPowReadDate", "1/1/1900");
            sDate = df1.format(new Date());
            
            int i = 0;
            for (i = 1; i < 4; i++) {
                sI = Integer.toString(i);
                dCosFi = Double.parseDouble((String) saMeterParams.get("PF"
                        + sI + "ikW"));
                dSinFi = Math.sqrt(1 - dCosFi * dCosFi);
                iPsign = Integer.parseInt((String) saMeterParams.get("P"
                        + sI + "ikW-Sign"));
                iQsign = Integer.parseInt((String) saMeterParams.get("Q"
                        + sI + "ikW-Sign"));
                iUinvalid = Integer.parseInt((String) saMeterParams.get("U"
                        + sI + "-Invalid"));
                U = Double.parseDouble((String) saMeterParams.get("U"
                        + sI));
                I = Double.parseDouble((String) saMeterParams.get("I"
                        + sI));
                P = iPsign * U * I * dCosFi / 1000.0;
                Q = iQsign * U * I * dSinFi / 1000.0;

                Ptot += P;
                Qtot += Q;

                saBlockParams.put("P" + sI, Double.toString(P));
                saBlockParams.put("P" + sI + "-ts", sDate);
                saBlockParams.put("Q" + sI, Double.toString(Q));
                saBlockParams.put("Q" + sI + "-ts", sDate);
                if (iUinvalid == 1) {
                    fiU = 0;
                    fiI = 0;
                } else {
                    fiU = (i - 1) * 120;
                    fiI = iQsign * Math.toDegrees(Math.acos(dCosFi)) + fiU;
                    if (iPsign < 0) {
                        fiI = fiI + 180;
                    }
                    if (fiI < 0) {
                        fiI += 360;
                    }
                    if (fiI > 360) {
                        fiI -= 360;
                    }
                }
                saBlockParams.put("fiU" + sI, Integer.toString((int) fiU));
                saBlockParams.put("fiU" + sI + "-ts", sDate);
                saBlockParams.put("fiI" + sI, Integer.toString((int) fiI));
                saBlockParams.put("fiI" + sI + "-ts", sDate);
            }
            saBlockParams.put("P", Double.toString(Ptot));
            saBlockParams.put("P" + "-ts", sDate);
            saBlockParams.put("Q", Double.toString(Qtot));
            saBlockParams.put("Q" + "-ts", sDate);

        } catch (Exception e) {
            return;
        }
    }

    public void ReadDate() {
        try {
            iRec = Execute("bbCl2Qry1");

            if ((iRec < 1) || (!iec8705102util.RespCheck(bRec, 0, iRec)[0].equals("OK"))) {
                //if ((iRec < 1) || (!iec8705102util.IsEqual(htMsgs.get("bbPassRsp"), bRec))) {
                bCon = false;
                return;
            }

            iRec = Execute("bbDateQry");

            if ((iRec < 1) || (!iec8705102util.IsEqual(htMsgs.get("bbAckRsp"), bRec))) {
                bCon = false;
                return;
            }
            dtCommDateRsp = new Date();
            iRec = Execute("bbCl2Qry1");

            if ((iRec < 1) || (!iec8705102util.RespCheck(bRec, 0, iRec)[0].equals("OK"))) {
                bCon = false;
                return;
            }

            dtMeterDate = iec8705102util.DecodeDateASDU103(Params, bRec, 14, 6);
            Params.put("MeterDate", df1.format(dtMeterDate));
            Params.put("MeterDate-ts", df1.format(dtCommDateRsp));

        } catch (Exception e) {
            bCon = false;
            return;
        }
        bCon = true;
        return;
    }

    DateFormat df1 = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

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

    String sKey = "";
    String sValue = "";

    public void WriteIecVals() {

        try {
            Enumeration sKeys = Params.keys();
            while (sKeys.hasMoreElements()) {
                sKey = (String) sKeys.nextElement();
                sValue = (String) Params.get(sKey);
                if (iDebug == 1) {
                    System.out.println(sKey + " : " + sValue);
                }
                pDataSet.put(sPrefix + sKey, sValue);
            }
        } catch (Exception ex) {
        }

    }

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
