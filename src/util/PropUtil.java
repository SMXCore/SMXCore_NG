/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.util.Enumeration;
import java.util.Properties;

/**
 *
 * @author cristi
 */
public class PropUtil {

    public static Properties LoadFromFile(Properties pProp, String sFile) {
        try {
            if (pProp == null) {
                pProp = new Properties();
            }
            if (sFile.endsWith(".xml")) {
                pProp.loadFromXML(new FileInputStream(sFile));
            } else {
                pProp.load(new FileInputStream(sFile));
            }

        } catch (Exception ex) {
            //ex.printStackTrace();
        }
        return pProp;
    }

    public static void SaveToFile(Properties pProp, String sFile, String sComment) {
        try {
            if (sFile.endsWith(".xml")) {
                pProp.storeToXML(new FileOutputStream(sFile), sComment);
            } else {
                pProp.store(new FileOutputStream(sFile), sComment);
            }

        } catch (Exception ex) {
            // ex.printStackTrace();
        }
    }

    public static int GetInt(Properties pProp, String sKey, int iDefault) {
        try {
            return Integer.parseInt(pProp.getProperty(sKey, "").trim());
        } catch (Exception ex) {

        }
        return iDefault;
    }

    public static long GetLong(Properties pProp, String sKey, long lDefault) {
        try {
            return Long.parseLong(pProp.getProperty(sKey, "").trim());
        } catch (Exception ex) {

        }
        return lDefault;
    }

    public static double GetDouble(Properties pProp, String sKey, double lDefault) {
        try {
            return Double.parseDouble(pProp.getProperty(sKey, "").trim());
        } catch (Exception ex) {

        }
        return lDefault;
    }

    public static String GetString(Properties pProp, String sKey, String sDefault) {
        try {
            return pProp.getProperty(sKey, sDefault);
        } catch (Exception ex) {
        }
        return sDefault;
    }
    public static SimpleDateFormat sdfDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

    public static Date GetDate(Properties pProp, String sKey, Date dtDefault) {
        Date dtRet;
        try {
            dtRet = sdfDate.parse(pProp.getProperty(sKey, ""));
        } catch (Exception ex) {
            dtRet = dtDefault;
        }
        return dtRet;
    }

    public static void PrintProp(Properties pProp) {

        Enumeration sKeys = pProp.keys();
        while (sKeys.hasMoreElements()) {
            try {
                String sKey = (String) sKeys.nextElement();
                String sValue = (String) pProp.get(sKey);
                System.out.println(sKey + " : " + sValue);

            } catch (Exception ex) {
                // System.out.println(ex.getMessage());
            }
        }
    }

    public static void SwitchKeyValue(Properties pInProp, Properties pOutProp) {
        try {
            if (pOutProp == null) {
                pOutProp = new Properties();
            }
            Enumeration sKeys = pInProp.keys();
            while (sKeys.hasMoreElements()) {
                String sKey = (String) sKeys.nextElement();
                String sValue = (String) pInProp.get(sKey);
                pOutProp.put(sValue, sKey);
                // System.out.println(sKey + " : " + sValue);
            }
        } catch (Exception ex) {
        }
    }
}
