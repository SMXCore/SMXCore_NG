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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Enumeration;

/**
 *
 * @author cristi
 */
public class MeterModbus extends Module {

    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);
        iDebug = PropUtil.GetInt(pAttributes, "iDebug", 0);

        iNoOfPos = PropUtil.GetInt(pAssociation, "iNoOfPos", 0);
    }

    Properties pAssociation = new Properties();

    Properties pDataSet = null;
    String sPrefix = "";
    Thread tLoop = null;
    int iDebug = 0;

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

                Read();

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

    private String sIP = "localhost";
    private int iPort = 502;
    public Socket s = null;
    public OutputStream outToServer = null;
    public InputStream inFromServer = null;

    String sRec = "";
    String[] sRecVals;
    String sSend = "";
    byte[] bRec = new byte[2550];
    int iRec = 0;
    int iSend = 0;
    byte[] bSend = new byte[2550];

    byte iQDataLen = 0;
    byte iRDataLen = 0;

    boolean bRecOK = false;

    public byte bFunction = 0;
    public long lStartAddr = 0;
    public byte lStartAddrH = 0;
    public byte lStartAddrL = 0;
    public int iLen = 0;
    public byte bLenH = 0;
    public byte bLenL = 0;
    public int iSuccesivErr = 0;
    public byte bAddress = 1;

    public int iConnTimeOut = 3000;
    public int iReadTimeOut = 2000;
    public int iReqPause = 100;
    public int iMaxSuccErr = 10;

    public void ReOpen() {
        try {
            try {
                if (s != null) {
                    s.close();
                }
            } catch (Exception e) {
            }
            iSuccesivErr = 0;

            bAddress = (byte) PropUtil.GetInt(pAttributes, "bAddress", 1);
            bFunction = (byte) PropUtil.GetInt(pAttributes, "bFunction", 4);

            lStartAddr = PropUtil.GetInt(pAttributes, "lStartAddr", 1);
            lStartAddrH = (byte) ((lStartAddr & 0xFF00) >> 8);
            lStartAddrL = (byte) (lStartAddr & 0xFF);

            iLen = PropUtil.GetInt(pAttributes, "iLen", 1);
            bLenH = (byte) ((iLen & 0xFF00) >> 8);
            bLenL = (byte) (iLen & 0xFF);

            sIP = PropUtil.GetString(pAttributes, "sIP", "");
            iPort = PropUtil.GetInt(pAttributes, "iPort", 502);

            iReadTimeOut = PropUtil.GetInt(pAttributes, "iReadTimeOut", 2000);
            iConnTimeOut = PropUtil.GetInt(pAttributes, "iConnTimeOut", 3000);

            s = new Socket();
            s.setReuseAddress(true);
            s.connect(new InetSocketAddress(sIP, iPort), iConnTimeOut);
            s.setSoTimeout(iReadTimeOut);
            outToServer = s.getOutputStream();
            inFromServer = s.getInputStream();

            /*
             01 00 00 00 00 06 07 04 00 00 00 28 
             01 00
             00 00 version
             00 06 length
             07      addr device
             04      fct
             00 00 start addr
             00 28 data len
                    
             Read input registers
             Reference: 0. Word count: 40. UID: 7
             */
            //01 00
            bSend[0] = 1;
            bSend[1] = 0;
            //00 00 version
            bSend[2] = 0;
            bSend[3] = 0;
            //00 06 length
            bSend[4] = 0;
            bSend[5] = 6;
            //07      addr device
            bSend[6] = bAddress;
            //04      fct
            bSend[7] = bFunction;
            //00 00 start addr
            bSend[8] = lStartAddrH;
            bSend[9] = lStartAddrL;
            //00 28 data len
            bSend[10] = bLenH;
            bSend[11] = bLenL;

            iSend = 12;

            //s.setSoTimeout(1000);
        } catch (Exception e) {
            // e.printStackTrace();
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }

    String sRecHex = "";
    String sRecHexTmp = "";

    public void Read() {
        try {

           // while (bStop == 0) {
                try {

                    if ((s == null) || (!s.isConnected()) || (s.isInputShutdown()) || (s.isOutputShutdown()) || (iSuccesivErr > iMaxSuccErr)) {
                        ReOpen();
                    }

                    outToServer.write(bSend, 0, iSend);
                    outToServer.flush();

                    iRec = inFromServer.read(bRec);
                    bRecOK = true;
                    //Response decode
                    //Check Modbus frame
/*  #1  0 ms  Connection: 1
                     Query: 

                    
                     01 00 00 00 00 53 07 04 50 05 EB
                     01 00
                     00 00 version
                     00 53 length
                     07      addr device
                     04      fct
                     50     data len
                     05 EB  data word 1

                     */
                    if (iRec < 8) {
                        bRecOK = false;
                    }
                    if ((bRec[2] != 0) || (bRec[3] != 0)) {
                        bRecOK = false;
                    }

                    if (bRec[6] != bAddress) {
                        bRecOK = false;
                    }

                    if (bRecOK) {
                        iSuccesivErr = 0;
                        sRecHex = util.ConvertUtil.ByteStr2HexStr(bRec, 9, iRec);
                        if (iDebug == 1) {
                            System.out.println(sdf.format(new Date()) + " " + sRecHex);
                        }
                        /*          sRecHexTmp="";
                         for(iCrtRecPos=0;iCrtRecPos<sRecHex.length();iCrtRecPos+=4){
                         sRecHexTmp+=sRecHex.substring(iCrtRecPos+2, iCrtRecPos+3)+
                         sRecHex.substring(iCrtRecPos+0, iCrtRecPos+1);
                         }
                         sRecHex=sRecHexTmp; */
                        DecodeVals();

                        //System.out.println(sdf.format(new Date()) + " " + sRecHex);
                    } else {
                        iSuccesivErr++;
                    }

                } catch (Exception e) {
                    Thread.sleep(10);
                    try {
                        iSuccesivErr++;

                    } catch (Exception ex) {
                    }
                }
          //  }

          //  s.close();

        } catch (Exception e) {

        }
    }
    public String sRecData1 = "";
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    int iNoOfPos = 0;
    int iCrtRecPos = 0;
    String[] sCrtPosDet;
    String sIntName = "";
    int iNoOfBytes = 0;
    int iNoOfHexChars = 0;
    float fScaleAdd = 0;
    float fScaleMul = 1;
    long lValue = 0;
    float fValue = 0;
    String sValue = "";
    String sTSDate;
    String sDateIntVar = "";

    public void DecodeVals() {
        try {
            iCrtRecPos = 0;
            sTSDate = sdf.format(new Date());
            sDateIntVar = PropUtil.GetString(pAssociation, "sDateIntVar", "");
            if (sDateIntVar.length() > 0) {
                pDataSet.put(sPrefix + sDateIntVar, sTSDate);
            } else {
                pDataSet.put(sPrefix + sName + "-ReadDate", sTSDate);
            }
            for (int iCrtPosNo = 1; iCrtPosNo <= iNoOfPos; iCrtPosNo++) {
                try {
                    if (iCrtRecPos > sRecHex.length()) {
                        break;
                    }
                    sCrtPosDet = PropUtil.GetString(pAssociation, Integer.toString(iCrtPosNo), "").split(",");
                    if (sCrtPosDet.length < 5) {
                        continue;
                    }
                    sValue = "";
                    sIntName = sCrtPosDet[0];
                    iNoOfBytes = Integer.parseInt(sCrtPosDet[2]);
                    iNoOfHexChars = iNoOfBytes * 2;
                    fScaleAdd = Float.parseFloat(sCrtPosDet[3]);
                    fScaleMul = Float.parseFloat(sCrtPosDet[4]);
                    if (sCrtPosDet[1].equals("int16")) {
                        lValue = Short.parseShort(sRecHex.substring(iCrtRecPos, iCrtRecPos + iNoOfHexChars), 16);
                        iCrtRecPos += iNoOfHexChars;
                        fValue = (lValue + fScaleAdd) * fScaleMul;
                        sValue = Float.toString(fValue);
                    } else if ((sCrtPosDet[1].equals("int32")) || (sCrtPosDet[0].equals("uint16"))) {
                        lValue = Integer.parseInt(sRecHex.substring(iCrtRecPos, iCrtRecPos + iNoOfHexChars), 16);
                        iCrtRecPos += iNoOfHexChars;
                        fValue = (lValue + fScaleAdd) * fScaleMul;
                        sValue = Float.toString(fValue);
                    } else if (sCrtPosDet[1].equals("floatIEC")) {
                        lValue = Integer.parseInt(sRecHex.substring(iCrtRecPos, iCrtRecPos + iNoOfHexChars), 16);
                        iCrtRecPos += iNoOfHexChars;
                        fValue = (Float.intBitsToFloat((int) lValue) + fScaleAdd) * fScaleMul;
                        sValue = Float.toString(fValue);
                    } else if ((sCrtPosDet[1].equals("int64")) || (sCrtPosDet[0].equals("uint32"))) {
                        lValue = Long.parseLong(sRecHex.substring(iCrtRecPos, iCrtRecPos + iNoOfHexChars), 16);
                        iCrtRecPos += iNoOfHexChars;
                        fValue = (lValue + fScaleAdd) * fScaleMul;
                        sValue = Float.toString(fValue);
                    } else if ((sCrtPosDet[1].equals("string")) || (sCrtPosDet[0].equals("str"))) {
                        iCrtRecPos += iNoOfHexChars;
                        sValue = sRecHex.substring(iCrtRecPos, iCrtRecPos + iNoOfHexChars);
                        sValue=util.ConvertUtil.HexStr2Str(sValue);
                    } else {
                        continue;
                    }
                    pDataSet.put(sPrefix + sIntName, sValue);
                    pDataSet.put(sPrefix + sIntName + "-ts", sTSDate);
                } catch (Exception ex) {
                }
            }
        } catch (Exception e) {
        }
    }

}
