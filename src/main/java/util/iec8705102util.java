/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 *
 * @author cristi
 */
public class iec8705102util {

    //             0    1    2    3   4   5 
    // Fixed len 10H Ctrl Addr Addr CkSum 16H
    public static byte bFixLenStart = 0x10;
    public static byte bFixLenEnd = 0x16;
    public static byte iFixLenPosCtrl = 2;

    //               0    1    2    3    4    5    6   7..len-3     len-2  len-1
    // Variable len 68H  Len  Len  68H  Ctrl Addr Addr Data         CkSum   16H
    public static byte bVarLenStart = 0x68;
    public static byte bVarLenEnd = 0x16;
    public static byte iVarLenPosLen = 1;
    public static byte iVarLenPosCtrl = 4;

    public static SimpleDateFormat df1 = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

    public static int DecodeDateASDU192(Properties Params, byte[] bbBuff, int iStart, int iLen) {

        /*      
         C0 - ASDU 192 2.5.1.4 Elem. Info.: Totalizadores de energías (Agrup: V / Dir. Obj.: 192)

         UI30[1..30]<0, 999999999>:= KWh - Activa importación
         BS1[31]<0> No Usado
         BS1[32] <0, 1>:= <0>:= Medida Válida
         <1>:= Medida Invalida
         32 bits / 8 = 4 bytes

         07 00 00 00    A+
         01 00 00 00    A-
         00 00 00 00    Ri+    Q1
         00 00 00 00    Rc+    Q2
         00 00 00 00    Ri-    Q3
         04 00 00 00    Rc-    Q4

         CP40[193..232]:= Etiqueta de tiempo. Hora RM.
         06 94 2B 05 0F
         06 94 0B 05 0F - filtrat
         06 20 11 05 15 2015-05-11 20:06
        
         */
        long lTmp = 0;
        Date dtTmp = new Date(1900, 1, 1);
        String sDate = "";
        String[] sNames = {"Ap", "Am", "Rip", "Rcp", "Rim", "Rcm"};
        int i;
        int iPos = iStart;
        for (i = 0; i < 6; i++) {
            lTmp = (long) (bbBuff[iPos + 3] & 0x3F);
            lTmp = GetLongValue(lTmp, bbBuff, iPos, 3);
            Params.put(sNames[i], Long.toString(lTmp));

            lTmp = (long) (bbBuff[iPos + 3] & 0x80);
            if (lTmp == 0) {
                Params.put(sNames[i] + "-Invalid", "0");
            } else {
                Params.put(sNames[i] + "-Invalid", "1");
            }
            iPos += 4;
        }
        dtTmp = DecodeDateValInst(Params, bbBuff, iPos, iLen);
        sDate = df1.format(dtTmp);
        Params.put("InstRegReadDate", sDate);
        sDate = df1.format(new Date());
        for (i = 0; i < 6; i++) {
            Params.put(sNames[i] + "-ts", sDate);
        }
        iPos += 5;
        return iPos;
    }

    public static int DecodeDateASDU193(Properties Params, byte[] bbBuff, int iStart, int iLen) {

        /*      
         C1 - ASDU 193 2.5.1.5 Elem. Info.: Potencias activas (Agrup: V / Dir. Obj.: 193)

         UI24[1..24]<0, 9999999>:= P. Activa Total (KW).
         01 00 00 
         UI24[25..48]<0, 9999999>:= P. Reactiva Total (KVAR).
         00 00 00

         UI10[49..58]<0, 1000>:= Factor de Potencia Total (en milésimas).Total power factor ( in milliseconds ) 
         BS1[59]<0, 1>:= <0>:= P. Activa Total es importada.
         <1>:= P. Activa Total es exportada.
         BS1[60]<0, 1>:= <0>:= P. Reactiva Total es Q1/Q2.
         <1>:= P. Reactiva Total es Q3/Q4.
         BS3[61..63]<0>:= No Usados
         BS1[64] <0, 1>:= <0>:= P. Total. Medidas Válidas
         <1>:= P. Total. Medidas Invalidas


         7A 07 - orig
         07 7A - decoded
         0000 1011 1011 11010
         ________1 1011 11010=890 factor putere = 37A
         _______1 P. Activa Total es exportada.
         ______0 P. Reactiva Total es Q1/Q2
         _000 No Usados
         0 P. Total. Medidas Válidas

        
         */
        long lTmp = 0;
        Date dtTmp = new Date(1900, 1, 1);
        String[] sNames = {"PikW", "QikW", "PFikW",
            "P1ikW", "Q1ikW", "PF1ikW",
            "P2ikW", "Q2ikW", "PF2ikW",
            "P3ikW", "Q3ikW", "PF3ikW"};
        int i;
        int iPos = iStart;
        String sPF = "";

        for (i = 0; i < 4; i++) {
            //P
            Params.put(sNames[i * 3 + 0], Long.toString(GetLongValue(0, bbBuff, iPos, 3)));
            iPos += 3;
            //Q
            Params.put(sNames[i * 3 + 1], Long.toString(GetLongValue(0, bbBuff, iPos, 3)));
            iPos += 3;
            //PF
            lTmp = bbBuff[iPos + 1] & 0x3;
            lTmp = GetLongValue(lTmp, bbBuff, iPos, 1);
            sPF = Double.toString(lTmp / 1000.0);
            Params.put(sNames[i * 3 + 2], sPF);
            //Valid
            lTmp = bbBuff[iPos + 1] & 0x80;
            if (lTmp == 0) {
                Params.put(sNames[i * 3 + 0] + "-Invalid", "0");
            } else {
                Params.put(sNames[i * 3 + 0] + "-Invalid", "1");
            }
            //P sign
            lTmp = (long) (bbBuff[iPos + 1] & 0x4);
            if (lTmp == 0) {
                Params.put(sNames[i * 3 + 0] + "-Sign", "1");
                Params.put(sNames[i * 3 + 2].replace("ikW", ""), sPF);
            } else {
                Params.put(sNames[i * 3 + 0] + "-Sign", "-1");
                Params.put(sNames[i * 3 + 2].replace("ikW", ""), "-" + sPF);
            }
            //Q sign
            lTmp = (long) (bbBuff[iPos + 1] & 0x8);
            if (lTmp == 0) {
                Params.put(sNames[i * 3 + 1] + "-Sign", "1");
            } else {
                Params.put(sNames[i * 3 + 1] + "-Sign", "-1");
            }

            iPos += 2;
        }
        dtTmp = DecodeDateValInst(Params, bbBuff, iPos, iLen);
        Params.put("InstPowReadDate", df1.format(dtTmp));
        iPos += 5;
        return iPos;
    }

    public static int DecodeDateASDU194(Properties Params, byte[] bbBuff, int iStart, int iLen) {

        /*      
         C2 ASDU 194 2.5.1.6 Elem. Info.: V_I (Agrup: V / Dir. Obj.: 194)
         UI24[1..24]<0, 9999999>:= Intensidad Fase I (décimas de amperio).
         37 00 00 -  55 5.5 V
         UI30[25..54]<0, 999999999>:= Tensión Fase I (décimas de volt).
         BS1[55]<0>:= No Usados
         BS1[56] <0, 1>:= <0>:= Fase I. Medidas Válidas
         <1>:= Fase I. Medidas Invalidas
        
         */
        long lTmp = 0;
        String sDate;
        Date dtTmp = new Date(1900, 1, 1);
        String[] sNames = {"I1", "U1",
            "I2", "U2",
            "I3", "U3"};
        int i;
        int iPos = iStart;

        for (i = 0; i < 3; i++) {
            //I
            Params.put(sNames[i * 2 + 0], Double.toString(GetLongValue(0, bbBuff, iPos, 3) / 10.0));
            iPos += 3;
            //U
            lTmp = (long) (bbBuff[iPos + 3] & 0x3F);
            lTmp = GetLongValue(lTmp, bbBuff, iPos, 3);
            Params.put(sNames[i * 2 + 1], Double.toString(lTmp / 10.0));
            //Valid
            lTmp = (long) (bbBuff[iPos + 3] & 0x80);
            if (lTmp == 0) {
                Params.put(sNames[i * 2 + 1] + "-Invalid", "0");
            } else {
                Params.put(sNames[i * 2 + 1] + "-Invalid", "1");
            }
            iPos += 4;
        }
        dtTmp = DecodeDateValInst(Params, bbBuff, iPos, iLen);
        sDate = df1.format(dtTmp);
        Params.put("InstValReadDate", sDate);
        sDate = df1.format(new Date());
        for (i = 0; i < 6; i++) {
            Params.put(sNames[i] + "-ts", sDate);
        }
        iPos += 5;
        return iPos;
    }

    public static Date DecodeDateValInst(Properties Params, byte[] bbBuff, int iStart, int iLen) {
        Date dtRetDate = new Date(1900, 1, 1);
        try {
            dtRetDate = new Date(2000 - 1900 + Byte2UInt(bbBuff[iStart + 4]),
                    (Byte2UInt(bbBuff[iStart + 3]) & 0x0F) - 1, Byte2UInt(bbBuff[iStart + 2]) & 0x1F,
                    Byte2UInt(bbBuff[iStart + 1]) & 0x1F, Byte2UInt(bbBuff[iStart + 0]) & 0x3F, 0);
        } catch (Exception e) {

        }
        return dtRetDate;
    }

    public static Date DecodeDateASDU103(Properties Params, byte[] bbBuff, int iStart, int iLen) {
        Date dtRetDate = new Date(1900, 1, 1);
        try {
            dtRetDate = new Date(2000 - 1900 + Byte2UInt(bbBuff[iStart + 5]),
                    (Byte2UInt(bbBuff[iStart + 4]) & 0x0F) - 1, Byte2UInt(bbBuff[iStart + 3]) & 0x1F,
                    Byte2UInt(bbBuff[iStart + 2]) & 0x1F, Byte2UInt(bbBuff[iStart + 1]),
                    (Byte2UInt(bbBuff[iStart + 0]) / 4) & 0x3F);
            if (Byte2UInt(bbBuff[iStart + 0]) / 4 > 59) {
                System.out.println(Integer.toString(Byte2UInt(bbBuff[iStart + 0]) / 4));
            }
        } catch (Exception e) {

        }
        return dtRetDate;
    }

    public static String[] RespCheck(byte[] bbBuff, int iStart, int iEnd) {
        String sResult[] = {"Error unknown", "", "", ""};

        try {
            if (bbBuff[iStart] == bFixLenStart) {
                //Fixed len
                if (iEnd - iStart != 6) {
                    sResult[0] = "Len error ";
                } else if (bbBuff[iEnd - 1] != bFixLenEnd) {
                    sResult[0] = "End char error ";
                } else if (bbBuff[iEnd - 2] != CalcFCS(bbBuff, iStart + 1, iStart + 4)) {
                    sResult[0] = "CRC error ";
                } else {
                    sResult[0] = "OK";
                    sResult[1] = Byte.toString(bbBuff[iStart + 1]);
                }

                return sResult;
            } else if (bbBuff[iStart] == bVarLenStart) {
                //Variable len
                if ((iEnd - iStart < 6) || (bbBuff[iStart + 1] != bbBuff[iStart + 2])
                        || (bbBuff[iStart + 1] != iEnd - iStart - 6)) {
                    sResult[0] = "Len error ";
                } else if (bbBuff[iStart + 3] != bVarLenStart) {
                    sResult[0] = "Start char error ";
                } else if (bbBuff[iEnd - 1] != bVarLenEnd) {
                    sResult[0] = "End char error ";
                } else if (bbBuff[iEnd - 2] != CalcFCS(bbBuff, iStart + 4, iEnd - 2)) {
                    sResult[0] = "CRC error ";
                } else {
                    sResult[0] = "OK";
                    sResult[1] = Byte.toString(bbBuff[iStart + 4]);
                }

                return sResult;
            } else {
                sResult[0] = "Start char error ";
                return sResult;
            }

        } catch (Exception e) {
            sResult[0] = "Error: " + e.getMessage();
        }
        return sResult;
    }

    public static int Byte2UInt(byte b) {
        return (int) (b & 0xFF);
    }

    public static boolean IsEqual(byte[] bBuff1, byte[] bBuff2) {
        try {
            int i;
            for (i = 0; i < bBuff1.length; i++) {
                if (bBuff1[i] != bBuff2[i]) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static byte[] GetAddr(int iAddr) {
        byte[] bRes = new byte[2];

        bRes[0] = (byte) (iAddr % 256);
        bRes[1] = (byte) (iAddr / 256);

        return bRes;

    }

    public static byte[] GetPassQry(byte[] bbAddr, String sPass) {
        byte[] bRes = {(byte) 0x68, (byte) 0X0D, 0X0D, (byte) 0X68, (byte) 0X73, 0X01, 0X00, (byte) 0XB7,
            0X01, 0X06, 0X01, 0X00, 0X00, 0X01, 0X00, 0X00, 0X00, 0X34, 0X16};
        try {
            bRes[5] = bbAddr[0];
            bRes[6] = bbAddr[1];
            long lPass = Long.parseLong(sPass);
            InsertValue(lPass, bRes, 13, 4);
            bRes[17] = CalcFCS(bRes, 4, bRes.length - 2);
        } catch (Exception e) {
            return null;
        }
        return bRes;
    }

    public static byte[] GetPassRsp(byte[] bbAddr, String sPass) {
        byte[] bRes = {(byte) 0x68, (byte) 0X0D, 0X0D, (byte) 0X68, (byte) 0X08, 0X01, 0X00, (byte) 0XB7,
            0X01, 0X07, 0X01, 0X00, 0X00, 0X01, 0X00, 0X00, 0X00, (byte) 0XCA, 0X16};
        try {
            bRes[5] = bbAddr[0];
            bRes[6] = bbAddr[1];
            long lPass = Long.parseLong(sPass);
            InsertValue(lPass, bRes, 13, 4);
            bRes[17] = CalcFCS(bRes, 4, bRes.length - 2);
        } catch (Exception e) {
            return null;
        }
        return bRes;
    }

    public static byte[] GetCloseQry(byte[] bbAddr, byte bASDU) {
        byte[] bRes = {(byte) 0x68, (byte) 0X09, 0X09, (byte) 0X68, (byte) 0X73, 0X01, 0X00, (byte) 0XBB,
            0X00, 0X06, 0X01, 0X00, 0X00, (byte) 0XE1, 0X16};
        try {
            bRes[5] = bbAddr[0];
            bRes[6] = bbAddr[1];
            bRes[7] = bASDU;

            bRes[13] = CalcFCS(bRes, 4, bRes.length - 2);
        } catch (Exception e) {
            return null;
        }
        return bRes;
    }

    public static byte[] GetCloseRsp(byte[] bbAddr, byte bASDU) {
        byte[] bRes = {(byte) 0x68, (byte) 0X09, 0X09, (byte) 0X68, (byte) 0X08, 0X01, 0X00, (byte) 0XBB,
            0X00, 0X07, 0X01, 0X00, 0X00, (byte) 0XCC, 0X16};
        try {
            bRes[5] = bbAddr[0];
            bRes[6] = bbAddr[1];
            bRes[7] = bASDU;

            bRes[13] = CalcFCS(bRes, 4, bRes.length - 2);
        } catch (Exception e) {
            return null;
        }
        return bRes;
    }

    public static byte[] GetDateQry(byte[] bbAddr, byte bASDU) {
        byte[] bRes = {(byte) 0x68, (byte) 0X09, 0X09, (byte) 0X68, (byte) 0X73, 0X01, 0X00, (byte) 0X67,
            0X00, 0X05, 0X01, 0X00, 0X00, (byte) 0XE1, 0X16};
        try {
            bRes[5] = bbAddr[0];
            bRes[6] = bbAddr[1];
            bRes[7] = bASDU;

            bRes[13] = CalcFCS(bRes, 4, bRes.length - 2);
        } catch (Exception e) {
            return null;
        }
        return bRes;
    }

    public static byte[] GetInstValQry(byte[] bbAddr, byte bASDU) {
        byte[] bRes = {(byte) 0x68, (byte) 0X0C, 0X0C, (byte) 0X68, (byte) 0X73, 0X01, 0X00, (byte) 0XA2,
            0X03, 0X05, 0X01, 0X00, 0X00, (byte) 0XC0, (byte) 0XC1, (byte) 0XC2, (byte) 0X62, 0X16};
        try {
            bRes[5] = bbAddr[0];
            bRes[6] = bbAddr[1];
            bRes[7] = bASDU;

            bRes[16] = CalcFCS(bRes, 4, bRes.length - 2);
        } catch (Exception e) {
            return null;
        }
        return bRes;
    }

    public static byte[] GetFixedLen(byte[] bbAddr, byte bCtrl) {
        byte[] bRes = {0x10, 0x40, 0x01, 0x00, 0x41, 0x16};

        bRes[1] = bCtrl;
        bRes[2] = bbAddr[0];
        bRes[3] = bbAddr[1];
        bRes[4] = CalcFCS(bRes, 1, 4);

        return bRes;
    }

    public static byte CalcFCS(byte[] bbMsg, int iStart, int iStop) {
        byte bRes = 0;
        int i;
        try {
            for (i = iStart; i < iStop; i++) {
                bRes = (byte) (bRes + bbMsg[i]);
            }
            return bRes;
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean InsertValue(long Value, byte[] bBuffer, int iStart, int iLen) {
        try {
            int i;

            for (i = 0; i < iLen; i++) {
                bBuffer[iStart + i] = (byte) (Value % 256);
                Value /= 256;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static long GetLongValue(long lIniVal, byte[] bBuffer, int iStart, int iLen) {
        try {

            long lRes = lIniVal;

            int i;

            for (i = iLen - 1; i >= 0; i--) {

                lRes = lRes * 256 + (long) (bBuffer[iStart + i] & 0xFF);
            }
            return lRes;
        } catch (Exception e) {
            return 0;
        }
    }
}
