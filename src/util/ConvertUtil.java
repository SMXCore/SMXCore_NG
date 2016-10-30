/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

/**
 *
 * @author cristi
 */
public class ConvertUtil {

    public static String Short2Hex(short shVal, int iPlaces) {
        try {

            return Int2Hex(shVal & 0xffff, iPlaces);
        } catch (Exception e) {
            return "";
        }
    }

    public static String Int2Hex(int iVal, int iPlaces) {
        try {
            String s = Integer.toString(iVal, 16).toUpperCase();
            int j = s.length();
            if (j > iPlaces) {
                throw new Exception("Too big");
            }
            for (j = j; j < iPlaces; j++) {
                s = "0" + s;
            }
            return s;
        } catch (Exception e) {
            return "";
        }
    }

    public static String Dec2Hex(long iVal, int iPlaces) {
        try {
            String s = Long.toHexString(iVal).toUpperCase();
            int j = s.length();
            if (j > iPlaces) {
                throw new Exception("Too big");
            }
            for (j = j; j < iPlaces; j++) {
                s = "0" + s;
            }
            return s;
        } catch (Exception e) {
            return "";
        }
    }

    public static String Dec2Hex(int iVal, int iPlaces) {
        try {
            String s = Integer.toHexString(iVal).toUpperCase();
            int j = s.length();
            if (j > iPlaces) {
                throw new Exception("Too big");
            }
            for (j = j; j < iPlaces; j++) {
                s = "0" + s;
            }
            return s;
        } catch (Exception e) {
            return "";
        }
    }

    public static String Dec2Hex(byte iVal, int iPlaces) {
        return Dec2Hex((long) ((char) iVal) & 0xff, iPlaces);
    }

    public static long Hex2Dec(String strHexValue) {
        return Long.parseLong(strHexValue, 16);
    }

    public static int Hex2Int(String strHexValue) {
        return Integer.parseInt(strHexValue, 16);
    }

    public static String ByteStr2HexStr(byte[] bb) {
        return ByteStr2HexStr(bb, 0, bb.length);
    }

    public static String ByteStr2HexStr(byte[] bb, int iStart, int iLen) {
        String strResult = "";
        int ii = 0;
        byte b;
        for (ii = iStart; ii < iStart + iLen; ii++) {
            b = bb[ii];
            strResult += Dec2Hex(b, 2);
        }
        return strResult;
    }

    public static byte[] HexStr2ByteStr(String strHex) {
        try {
            int iLen = strHex.length() / 2;
            int i;
            byte[] strResult = new byte[iLen];

            for (i = 0; i < iLen; i++) {
                strResult[i] = (byte) Hex2Dec(strHex.substring(2 * i, 2 * i + 2));
            }
            return strResult;
        } catch (Exception e) {
            return null;
        }
    }

    public static String HexStr2Str(String strHex) {
        try {
            return new String(HexStr2ByteStr(strHex));
        } catch (Exception e) {
            return null;
        }
    }
}
