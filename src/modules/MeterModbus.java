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
 * @author cristi, mihai
 */

public class MeterModbus extends Module {

    public boolean Modbus_type = true; // of direct RS485, value = true; if TCP-Modbus, vaule = false;
    public int ModbusType = 0; // of direct RS485, value = true; if TCP-Modbus, vaule = false;

    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);
        iDebug = PropUtil.GetInt(pAttributes, "iDebug", 0);
        // readout of Modbus type
        ModbusType = PropUtil.GetInt(pAttributes, "bModbusType", 0);
        if(ModbusType==0) Modbus_type = true; else Modbus_type = false;

        iNoOfPos = PropUtil.GetInt(pAssociation, "iNoOfPos", 0);

        //insert in database metadata related to the Modbus module 
        String sTSDate;
        sTSDate = sdf.format(new Date());
        pDataSet.put("Module/MeterModbus/"+sName+"/StartDateTime", sTSDate); // DateTime
        String s1;
        if(ModbusType==0) s1="RS485"; else s1 = "TCP"; 
        pDataSet.put("Module/MeterModbus/"+sName+"/ModbusType", s1); // ModbusType
        s1 = sPrefix; pDataSet.put("Module/MeterModbus/"+sName+"/sPrefix", s1); // 
        s1 = String.valueOf(lPeriod); pDataSet.put("Module/MeterModbus/"+sName+"/lPeriod", s1); // 
        /*
        */
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
                    System.out.println("Modb_E1="+e.getMessage());
                }
            }

        }
    }

    public int Pause = 0;
    public int memPause = 0;
    public int bStop = 0;

    public int Debug = 1;

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
            if (iDebug == 1) System.out.println("Modb_M1=");
/*
            Site: http://www.windmill.co.uk/modbussettings.html
Bytes 1 & 2	are a transaction identifier, usually 0 0, but may be different depending on how the system has been set up.
Bytes 3 & 4	are a protocol identifier, always 0 0.
Bytes 5 & 6	identify the number of bytes to follow. Byte 5 is always 0 as messages are shorter than 256 bytes
Byte 7	identifies a unit. It is used when the Modbus device is actually several devices behind a gateway or bridge, and specifies the Slave address of one of those devices.
Byte 8	is the Modbus function code, eg: 
03 (read holding register) 
04 (read input registers)
06 (write holding register)
Byte 9	is the msb of register: starting address
Byte 10	is the lsb of register: starting address
Byte 11	is the msb of number of bytes to read or write: normally 0
Byte 12	is the lsb of number of bytes to read or write: for example 2            
*/
            /*
             01 00 00 00 00 06 07 04 00 00 00 28 
             01 00
             00 00 version
             00 06 length
             // From here are the bytes used only in direct RS485 interface with Modbus
             07      addr device
             04      fct
             00 00 start addr
             00 28 data len
                    
             Read input registers
             Reference: 0. Word count: 40. UID: 7
             */
            if (Modbus_type==false) { // Modbus over TCP
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
            }
            else { // classic RS485 for Modbus
                //07      addr device
                bSend[0] = bAddress;
                //04      fct
                bSend[1] = bFunction;
                //00 00 start addr
                bSend[2] = lStartAddrH;
                bSend[3] = lStartAddrL;
                //00 28 data len
                bSend[4] = bLenH;
                bSend[5] = bLenL;
                //bSend[5] = -107;//149;//0x95;
                //bSend[6] = -34;//222;//0xde;

                //calculateCRC(byte[] data, int offset, int len);
                int[] crc = {0xFF, 0xFF};
                crc = calculateCRC(bSend, 0, 6);
                bSend[6] = (byte)crc[0];  bSend[7] = (byte)crc[1];
//                bSend[5] = (byte)crc[0];  bSend[6] = (byte)crc[1];

                iSend = 8;
            }

            //s.setSoTimeout(1b000);
        } catch (Exception e) {
            // e.printStackTrace();
            if (Debug == 1) {
                System.out.println("Modb_Send1="+e.getMessage());
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
                    //Thread.sleep(200);
                    outToServer.flush();

                    Thread.sleep(300);
                    iRec = inFromServer.read(bRec);
                    if (iDebug == 1) System.out.println("Modb_In="+util.ConvertUtil.ByteStr2HexStr(bRec, 0, iRec));
                    bRecOK = true;
                    //Response decode
/* 02 03 04 xx yy zz uu crc crc
Byte 1 = device address
Byte 2 = function code
Byte 3 = number of bytes read
Byte 4 = 1st word, msb
Byte 5 = 1st word, lsb
Byte 6 = 2nd word, msb
Byte 7 = 2nd word, lsb
   :
   :
Byte n = CRC
*/        
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
/*
Send: 02 03 C5 58 00 24 F8 FD                            ..ÅX.$øý
Receive:  02 03 48 00 00 56 4B 00 00 55 AD 00 00 55 06 00   ..H..VK..U­..U..
 00 C3 64 00 00 03 02 00 00 00 C2 00 00 00 EC 00   .Ãd.......Â...ì.
 00 03 44 00 00 00 0F FF FF FF FD 00 00 00 1A FF   ..D....ÿÿÿý....ÿ
 FF FD C4 00 00 00 0A 00 00 00 02 00 00 00 02 FF   ÿýÄ............ÿ
 FF FF FF FF FF FF FF 00 00 00 00 7B F5            ÿÿÿÿÿÿÿ....{õ

*/
            if (Modbus_type==false) { // Modbus over TCP
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
                            System.out.println("Modb_E2="+sdf.format(new Date()) + " " + sRecHex);
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
            } else {
                    if (iRec != 77) {
                        bRecOK = false;
                    }
                    if(bRecOK==true) {
                        sRecHex = util.ConvertUtil.ByteStr2HexStr(bRec, 36, iRec);
                        DecodeVals_SOCOMEC();
                    }
            
            }

                } catch (Exception e) {
                    Thread.sleep(10);
                    try {
                        if (iDebug == 1) System.out.println("Modb_E3=");
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

    public int hex2decimal(String s) {
             String digits = "0123456789ABCDEF";
             s = s.toUpperCase();
             int val = 0;
             for (int i = 0; i < s.length(); i++) {
                 char c = s.charAt(i);
                 int d = digits.indexOf(c);
                 val = 16*val + d;
             }
             return val;
         }
    
    public void DecodeVals_SOCOMEC() {
        try {
            iCrtRecPos = 0;
            sTSDate = sdf.format(new Date());
            sDateIntVar = PropUtil.GetString(pAssociation, "sDateIntVar", "");
            int val_crt1, val_crt2, val_crt3, val_crt4;
            int offs;
            float f1, f2, f3,f4;
            String s1,s2,s3,s4;
            //if (sDateIntVar.length() > 0) {
            //   pDataSet.put(sPrefix + sDateIntVar, sTSDate);
            //} else {
            //    pDataSet.put(sPrefix + sName + "-ReadDate", sTSDate);
            //}
            //for (int iCrtPosNo = 1; iCrtPosNo <= iNoOfPos; iCrtPosNo++) {
                try {
                    offs=3; s1 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt1 = hex2decimal(s1); f1=val_crt1; f1=f1/100; s1= String.valueOf(f1);
                    pDataSet.put(sPrefix + "1-1-32-7-0-255/-2", s1); // U1
                    offs=3+4; s2 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt2 = hex2decimal(s2); f2=val_crt2; f2=f2/100; s2= String.valueOf(f2);
                    pDataSet.put(sPrefix + "1-1-52-7-0-255/-2", s2); // U2
                    offs=3+8; s3 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt3 = hex2decimal(s3); f3=val_crt3; f3=f3/100; s3= String.valueOf(f3);
                    pDataSet.put(sPrefix + "1-1-72-7-0-255/-2", s3); // U3
                    offs=3+12; s4 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt4 = hex2decimal(s4); f4=val_crt4; f4=f4/1000; s4= String.valueOf(f4);
                    pDataSet.put(sPrefix + "1-1-14-7-0-255/-2", s4); // frequency f
                    System.out.println("U1="+s1 + "\tU2="+s2 + "\tU3="+s3 + "\tf="+s4);

                    offs=3+16; s1 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt1 = hex2decimal(s1); f1=val_crt1; f1=f1/1000; s1= String.valueOf(f1);
                    pDataSet.put(sPrefix + "1-1-31-7-0-255/-2", s1); // I1
                    offs=3+20; s2 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt2 = hex2decimal(s2); f2=val_crt2; f2=f2/1000; s2= String.valueOf(f2);
                    pDataSet.put(sPrefix + "1-1-51-7-0-255/-2", s2); // I2
                    offs=3+24; s3 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt3 = hex2decimal(s3); f3=val_crt3; f3=f3/1000; s3= String.valueOf(f3);
                    pDataSet.put(sPrefix + "1-1-71-7-0-255/-2", s3); // I3
                    offs=3+28; s4 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt4 = hex2decimal(s4); f4=val_crt4; f4=f4/1000; s4= String.valueOf(f4);
                    pDataSet.put(sPrefix + "1-1-7x-7-0-255/-2", s4); // I0
                    System.out.println("I1="+s1 + "\tI2="+s2 + "\tI3="+s3 + "\tI0="+s4);

                    offs=3+32; s1 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt1 = hex2decimal(s1); f1=val_crt1; f1=f1*10; s1= String.valueOf(f1);
                    pDataSet.put(sPrefix + "1-1-16-7-0-255/-2", s1); // Ptot
                    offs=3+36; s2 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt2 = hex2decimal(s2); f2=val_crt2; f2=f2*10; s2= String.valueOf(f2);
                    pDataSet.put(sPrefix + "1-1-131-7-0-255/-2", s2); // Qtot
                    offs=3+40; s3 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt3 = hex2decimal(s3); f3=val_crt3; f3=f3*10; s3= String.valueOf(f3);
                    pDataSet.put(sPrefix + "1-1-7z1-7-0-255/-2", s3); // Stot
                    offs=3+44; s4 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt4 = hex2decimal(s4); f4=val_crt4; f4=f4/1000; s4= String.valueOf(f4);
                    pDataSet.put(sPrefix + "1-1-7z2-7-0-255/-2", s4); // PF
                    System.out.println("P="+s1 + "\tQ="+s2 + "\tS="+s3 + "\tPF="+s4);

                    offs=3+48; s1 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt1 = hex2decimal(s1); f1=val_crt1; f1=f1*10; s1= String.valueOf(f1);
                    pDataSet.put(sPrefix + "1-1-36-7-0-255/-2", s1); // Ptot
                    offs=3+52; s2 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt2 = hex2decimal(s2); f2=val_crt2; f2=f2*10; s2= String.valueOf(f2);
                    pDataSet.put(sPrefix + "1-1-56-7-0-255/-2", s2); // Qtot
                    offs=3+56; s3 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt3 = hex2decimal(s3); f3=val_crt3; f3=f3*10; s3= String.valueOf(f3);
                    pDataSet.put(sPrefix + "1-1-76-7-0-255/-2", s3); // Stot
                    System.out.println("P1="+s1 + "\tP2="+s2 + "\tP3="+s3);

                    offs=3+60; s1 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt1 = hex2decimal(s1); f1=val_crt1; f1=f1*10; s1= String.valueOf(f1);
                    pDataSet.put(sPrefix + "1-1-151-7-0-255/-2", s1); // Ptot
                    offs=3+64; s2 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt2 = hex2decimal(s2); f2=val_crt2; f2=f2*10; s2= String.valueOf(f2);
                    pDataSet.put(sPrefix + "1-1-171-7-0-255/-2", s2); // Qtot
                    offs=3+68; s3 =  util.ConvertUtil.ByteStr2HexStr(bRec, offs, 4); val_crt3 = hex2decimal(s3); f3=val_crt3; f3=f3*10; s3= String.valueOf(f3);
                    pDataSet.put(sPrefix + "1-1-191-7-0-255/-2", s3); // Stot
                    pDataSet.put(sPrefix + "0-0-1-0-0-255/DateTime", sTSDate); // Stot
                    System.out.println("Q1="+s1 + "\tQ2="+s2 + "\tQ3="+s3 + "\tTime="+sTSDate);

                    //pDataSet.put(sPrefix + sIntName + "-ts", sTSDate);
                } catch (Exception ex) {
                }
            //}
        } catch (Exception e) {
        }
    }
    
/* Table of CRC values for high-order byte */
      private final static short[] auchCRCHi = {
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0,
        0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41,
        0x00, 0xC1, 0x81, 0x40, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0,
        0x80, 0x41, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40,
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1,
        0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0, 0x80, 0x41,
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1,
        0x81, 0x40, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41,
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0,
        0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x00, 0xC1, 0x81, 0x40,
        0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1,
        0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40,
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0,
        0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x00, 0xC1, 0x81, 0x40,
        0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0,
        0x80, 0x41, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40,
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0,
        0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41,
        0x00, 0xC1, 0x81, 0x40, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0,
        0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41,
        0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0,
        0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x00, 0xC1, 0x81, 0x40,
        0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1,
        0x81, 0x40, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41,
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0,
        0x80, 0x41, 0x00, 0xC1, 0x81, 0x40
      };    
      /* Table of CRC values for low-order byte */
      private final static short[] auchCRCLo = {
        0x00, 0xC0, 0xC1, 0x01, 0xC3, 0x03, 0x02, 0xC2, 0xC6, 0x06,
        0x07, 0xC7, 0x05, 0xC5, 0xC4, 0x04, 0xCC, 0x0C, 0x0D, 0xCD,
        0x0F, 0xCF, 0xCE, 0x0E, 0x0A, 0xCA, 0xCB, 0x0B, 0xC9, 0x09,
        0x08, 0xC8, 0xD8, 0x18, 0x19, 0xD9, 0x1B, 0xDB, 0xDA, 0x1A,
        0x1E, 0xDE, 0xDF, 0x1F, 0xDD, 0x1D, 0x1C, 0xDC, 0x14, 0xD4,
        0xD5, 0x15, 0xD7, 0x17, 0x16, 0xD6, 0xD2, 0x12, 0x13, 0xD3,
        0x11, 0xD1, 0xD0, 0x10, 0xF0, 0x30, 0x31, 0xF1, 0x33, 0xF3,
        0xF2, 0x32, 0x36, 0xF6, 0xF7, 0x37, 0xF5, 0x35, 0x34, 0xF4,
        0x3C, 0xFC, 0xFD, 0x3D, 0xFF, 0x3F, 0x3E, 0xFE, 0xFA, 0x3A,
        0x3B, 0xFB, 0x39, 0xF9, 0xF8, 0x38, 0x28, 0xE8, 0xE9, 0x29,
        0xEB, 0x2B, 0x2A, 0xEA, 0xEE, 0x2E, 0x2F, 0xEF, 0x2D, 0xED,
        0xEC, 0x2C, 0xE4, 0x24, 0x25, 0xE5, 0x27, 0xE7, 0xE6, 0x26,
        0x22, 0xE2, 0xE3, 0x23, 0xE1, 0x21, 0x20, 0xE0, 0xA0, 0x60,
        0x61, 0xA1, 0x63, 0xA3, 0xA2, 0x62, 0x66, 0xA6, 0xA7, 0x67,
        0xA5, 0x65, 0x64, 0xA4, 0x6C, 0xAC, 0xAD, 0x6D, 0xAF, 0x6F,
        0x6E, 0xAE, 0xAA, 0x6A, 0x6B, 0xAB, 0x69, 0xA9, 0xA8, 0x68,
        0x78, 0xB8, 0xB9, 0x79, 0xBB, 0x7B, 0x7A, 0xBA, 0xBE, 0x7E,
        0x7F, 0xBF, 0x7D, 0xBD, 0xBC, 0x7C, 0xB4, 0x74, 0x75, 0xB5,
        0x77, 0xB7, 0xB6, 0x76, 0x72, 0xB2, 0xB3, 0x73, 0xB1, 0x71,
        0x70, 0xB0, 0x50, 0x90, 0x91, 0x51, 0x93, 0x53, 0x52, 0x92,
        0x96, 0x56, 0x57, 0x97, 0x55, 0x95, 0x94, 0x54, 0x9C, 0x5C,
        0x5D, 0x9D, 0x5F, 0x9F, 0x9E, 0x5E, 0x5A, 0x9A, 0x9B, 0x5B,
        0x99, 0x59, 0x58, 0x98, 0x88, 0x48, 0x49, 0x89, 0x4B, 0x8B,
        0x8A, 0x4A, 0x4E, 0x8E, 0x8F, 0x4F, 0x8D, 0x4D, 0x4C, 0x8C,
        0x44, 0x84, 0x85, 0x45, 0x87, 0x47, 0x46, 0x86, 0x82, 0x42,
        0x43, 0x83, 0x41, 0x81, 0x80, 0x40
      };
      
    public static final int[] calculateCRC(byte[] data, int offset, int len) {
        int[] crc = {0xFF, 0xFF};
        int nextByte = 0;
        int uIndex; /* will index into CRC lookup*/ /* table */
        /* pass through message buffer */
        for (int i = offset; i < len && i < data.length; i++) {
          nextByte = 0xFF & ((int) data[i]);
          uIndex = crc[0] ^ nextByte; //*puchMsg++; /* calculate the CRC */
          crc[0] = crc[1] ^ auchCRCHi[uIndex];
          crc[1] = auchCRCLo[uIndex];
        }

        return crc;
      }      
}
