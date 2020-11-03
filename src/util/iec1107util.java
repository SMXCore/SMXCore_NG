/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author cristi
 */
public class iec1107util {

    public static String sHexACK = "06";
    public static String sHexSTX = "02";
    public static String sHexETX = "03";
    public static String sHexE0T = "04";
    public static String sHexSOH = "01";
    public static String sHexNAK = "15";

    public static String sACK = "" + (char) 0X06;
    public static String sSTX = "" + (char) 0X02;
    public static String sETX = "" + (char) 0X03;
    public static String sE0T = "" + (char) 0X04;
    public static String sSOH = "" + (char) 0X01;
    public static String sNAK = "" + (char) 0X15;

    public byte bACK = 0X06;
    public byte bSTX = 0X02;
    public byte bETX = 0X03;
    public byte bE0T = 0X04;
    public byte bSOH = 0X01;
    public byte bNAK = 0X15;

    static String[][] IecParams = {{"1-1-0.9.1", "mTime", "t","1"}, {"1-1-0.9.2", "mDate", "d","1"},
    {"1-1-F.F.0", "ErrReg", "s","1"}, {"1-1-0.0.0", "MeterSN", "s","1"},
    {"1-1-1.8.0", "Ap", "tr","1"}, {"1-1-2.8.0", "Am", "tr","1"}, {"1-1-3.8.0", "Rp", "tr","1"}, {"1-1-4.8.0", "Rm", "tr","1"},
    {"1-1-5.8.0", "Rip", "tr","1"}, {"1-1-6.8.0", "Rim", "tr","1"}, {"1-1-7.8.0", "Rcp", "tr","1"}, {"1-1-8.8.0", "Rcm", "tr","1"},
    {"1-1-32.7.0", "U1", "rr","1"}, {"1-1-52.7.0", "U2", "rr","1"}, {"1-1-72.7.0", "U3", "rr","1"},
    {"1-1-31.7.0", "I1", "rr","1"}, {"1-1-51.7.0", "I2", "rr","1"}, {"1-1-71.7.0", "I3", "rr","1"},
    {"1-1-14.7.0", "f", "rr","1"}, {"1-1-13.7.0", "PF", "rr","1"},
    {"1-1-33.7.0", "PF1", "rr","1"}, {"1-1-53.7.0", "PF2", "rr","1"}, {"1-1-73.7.0", "PF3", "rr","1"},
    {"1-1-36.7.0", "P1", "rr","1"}, {"1-1-56.7.0", "P2", "rr","1"}, {"1-1-76.7.0", "P3", "rr","1"},
    {"1-1-151.7.0", "Q1", "rr","1"}, {"1-1-171.7.0", "Q2", "rr","1"}, {"1-1-191.7.0", "Q3", "rr","1"},
    {"1-1-16.7.0", "P", "rr","1"}, {"1-1-131.7.0", "Q", "rr","1"},
    //Iskra MT174
    {"1-0-0.9.1", "mTime", "t","1"}, {"1-0-0.9.2", "mDate", "d","1"},
    {"1-0-1.8.0", "Ap", "tr","1"}, {"1-0-1.8.1", "Rate1_Ap","tr","1"}, {"1-0-1.8.2", "Rate2_Ap","tr","1"},
    {"1-0-2.8.0", "Am", "tr","1"}, {"1-0-2.8.1", "Rate1_Am", "tr","1"}, {"1-0-2.8.2", "Rate2_Am", "tr","1"},
    
    };

    public Map<String, String[]> mpIecParams = null;

    public void InitMap() {
        int iLine;

        mpIecParams = new HashMap<String, String[]>();

        for (iLine = 0; iLine < IecParams.length; iLine++) {
            mpIecParams.put(IecParams[iLine][0], IecParams[iLine]);

        }
    }

    public String[] getObisDet(String sOBIS) {
        if (mpIecParams == null) {
            InitMap();
        }

        return mpIecParams.get(sOBIS);
    }
    public String[] getObisDet(String sOBIS,Properties pAssoc) {
        String sRet="";
        if(pAssoc==null){
            
        }else{
            sRet=(String)pAssoc.get(sOBIS);
            if((sRet!=null)&&(sRet.length()>1))
            return sRet.split(",");
        }
        
        if (mpIecParams == null) {
            InitMap();
        }
        
        return mpIecParams.get(sOBIS);
    }
    public static String IecRespClean(String sResp) {
        String strResult = "";
        int iIndexOf = 0;

        for (char c : sResp.toCharArray()) {
            c = (char) (c & 0x7f);
            if (c > 0) {
                strResult += c;
            }
        }
        iIndexOf = strResult.indexOf(sACK);
        if (iIndexOf > 0) {
            strResult = strResult.substring(iIndexOf);
        }
        iIndexOf = strResult.indexOf(sSOH);
        if (iIndexOf > -1) {
            strResult = strResult.substring(iIndexOf);

        } else {
            iIndexOf = strResult.indexOf(sSTX);
            if (iIndexOf > 0) {
                strResult = strResult.substring(iIndexOf);
            }
        }
        return strResult;
    }

    public static byte[] IecRespClean(byte[] bbResp, int iStart, int iLen) {
        int i;

        for (i = iStart; i < iStart + iLen; i++) {
            bbResp[i] = (byte) (bbResp[i] & 0x7f);

        }
        return bbResp;
    }

    public static String getInitString(String sAddr) {
        String strResult = "";

        if (sAddr.length() > 1) {
            strResult = "/?" + sAddr + "!";
        } else {
            strResult = "/?!";
        }

        return strResult + "\r\n";
    }

    public static String getCloseString() {
        //<SOH>B0<ETX>q
        return sSOH + "B0" + sETX +"q";
    }

    public static String getReadoutString() {
        return sACK + "050" + "\r\n";
    }

    public static String getReadoutString(String sSpeed) {
        return sACK + "0" + sSpeed + "0" + "\r\n";
    }

    public static String getParamString(String sSpeed) {
        return sACK + "0" + sSpeed + "1" + "\r\n";
    }

}
