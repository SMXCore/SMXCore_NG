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
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 *
 * @author cristi
 */
public class MeterVirtualLP extends Module {

    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        PropUtil.LoadFromFile(pStoreAssociation, PropUtil.GetString(pAttributes, "pStoreAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);
        lServePeriod = PropUtil.GetInt(pAttributes, "lServePeriod", 1000);
        iNoOfBufferDays = PropUtil.GetInt(pAttributes, "iNoOfBufferDays", 5);
        iCommpress = PropUtil.GetInt(pAttributes, "iCommpress ", 1);
        iMove = PropUtil.GetInt(pAttributes, "iMove ", 1);
        sFileName = PropUtil.GetString(pAttributes, "sFileName", sName);
        sFileExt = PropUtil.GetString(pAttributes, "sFileExt", "");
        sSeparator = PropUtil.GetString(pAttributes, "sSeparator", "\t");

    }

    int iCommpress;
    int iMove;
    int iNoOfBufferDays;

    Properties pStoreAssociation = new Properties();

    Properties pDataSet = null;
    String sPrefix = "";
    Thread tStoreLoop = null;
    Thread tServeLoop = null;

    @Override
    public void Start() {
        try {
            tStoreLoop = new Thread(new Runnable() {

                @Override
                public void run() {
                    StoreLoop();
                    // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }
            });
            tStoreLoop.start();
            tServeLoop = new Thread(new Runnable() {

                @Override
                public void run() {
                    ServeLoop();
                    // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }
            });
            tServeLoop.start();
        } catch (Exception e) {
            // Log(Name + "-Open-" + e.getMessage() + "-" + e.getCause());
        }
    }
    BufferedWriter bwStorage = null;
    String sCrtFileName = "";
    String sLastFileName = "";
    String sFileExt = "";
    String sFileName = "";
    String sRow = "";
    String sSeparator = "";
    String sTodayLP = "";
    String sHeader = "";

    public void StoreLoop() {
        dCrtDate = new Date();
        sCrtFileName = sFileName + "-" + sdf.format(dCrtDate) + sFileExt;
        sTodayLP = util.FileUtil.LoadFromFile(sCrtFileName);
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
                dCrtDate = new Date();
                sDate = sdfLogDate.format(dCrtDate);

                lIniSysTimeMs = System.currentTimeMillis();
                ldt = lIniSysTimeMs - lMemSysTimeMs;
                lMemSysTimeMs = lIniSysTimeMs;

                if (dLastDate != null) {
                    if (dLastDate.getDay() != dCrtDate.getDay()) {
                        // if (dLastDate.getMinutes() != dCrtDate.getMinutes()) {
                        try {
                            bwStorage.close();
                        } catch (Exception ex) {
                        }
                        bwStorage = null;
                        try {
                            PutLPDayBuffer(sdf.format(dLastDate), sTodayLP);
                            sTodayLP = "";
                        } catch (Exception ex) {
                        }

                        if (iCommpress == 1) {
                            if (iMove == 1) {
                                util.ZipFiles.getInstance().Compress(sCrtFileName, sCrtFileName + ".zip", "move");
                            } else {
                                util.ZipFiles.getInstance().Compress(sCrtFileName, sCrtFileName + ".zip", "null");
                            }
                        }

                    }
                }
                dLastDate = dCrtDate;

                if (bwStorage == null) {
                    sCrtFileName = sFileName + "-" + sdf.format(dCrtDate) + sFileExt;
                    bwStorage = new BufferedWriter(new FileWriter(sCrtFileName, true));
                    sHeader = GetHeader();
                    bwStorage.write(sHeader);
                    sTodayLP += sHeader;
                }
                sRow = GetValues();
                try {
                    bwStorage.write(sRow);
                    bwStorage.flush();
                } catch (Exception ex) {
                    try {
                        bwStorage.close();
                    } catch (Exception exc) {
                    }
                    bwStorage = null;
                }
                sTodayLP += sRow;
            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }

    SimpleDateFormat sdf = new SimpleDateFormat("YYYYMMdd");
    SimpleDateFormat sdfLogDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    SimpleDateFormat sdfQryDate = new SimpleDateFormat("MM/dd/yyyyHH:mm:ss");
    SimpleDateFormat sdfLogDateMs = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss SSS");
    SimpleDateFormat sdfLogMs = new SimpleDateFormat("SSS");

    public int Pause = 0;
    public int memPause = 0;
    public int bStop = 0;

    public int Debug = 0;

    public long lServePeriod = 0;
    public long lPeriod = 0;
    public long lIniSysTimeMs = 0;
    public long lMemSysTimeMs = 0;
    public long ldt = 0;
    public double ddt = 0.0;
    public long lDelay = 0;

    String sDate = "";
    Date dCrtDate = null;
    Date dLastDate = null;
    String sLastRow = "";

    String sKey;
    String sValue;
    Enumeration eKeys;
    String sInternalAttr;
    String sSameValues;

    public String GetValues() {
        String sRet = "";
        try {
            eKeys = pStoreAssociation.keys();
            while (eKeys.hasMoreElements()) {
                try {

                    sInternalAttr = sPrefix + (String) eKeys.nextElement();
                    sValue = (String) pDataSet.getProperty(sInternalAttr, "");
                    sRet += sValue + sSeparator;

                } catch (Exception ex) {
                    if (Debug == 1) {
                        System.out.println(ex.getMessage());
                    }
                }
            }
            if (sRet.equals(sLastRow)) {
                sSameValues = "1";
            } else {
                sSameValues = "0";
            }
            sLastRow = sRet;
            sRet = sSameValues + sSeparator + sRet;

            sRet = sdfLogDateMs.format(dCrtDate) + sSeparator
                    + sdfLogDate.format(dCrtDate) + sSeparator
                    + sdfLogMs.format(dCrtDate) + sSeparator
                    + sRet;

        } catch (Exception e) {
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
        return sRet + "\r\n";
    }

    public String GetHeader() {

        String sRet = "";
        try {
            eKeys = pStoreAssociation.keys();
            while (eKeys.hasMoreElements()) {
                try {

                    sInternalAttr = (String) eKeys.nextElement();
                    if (sInternalAttr.startsWith("#")) {
                        continue;
                    }
                    sValue = sPrefix + (String) pStoreAssociation.getProperty(sInternalAttr, "");
                    sRet += sValue + sSeparator;

                } catch (Exception ex) {

                    if (Debug == 1) {
                        System.out.println(ex.getMessage());
                    }
                }
            }

            sRet = "SysDate-ms" + sSeparator
                    + "SysDate" + sSeparator
                    + "SysTime-ms" + sSeparator
                    + "SameValues" + sSeparator + sRet;

        } catch (Exception e) {
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
        return sRet + "\r\n";

    }
    Vector<String> vLPDates = new Vector<>();
    Properties pLPBuffer = new Properties();

    public void PutLPDayBuffer(String sDay, String sDayBuffer) {
        try {
            vLPDates.add(sDay);
            pLPBuffer.put(sDay, sDayBuffer);
            if (vLPDates.size() > iNoOfBufferDays) {
                sDay = vLPDates.elementAt(0);
                pLPBuffer.remove(sDay);
                vLPDates.remove(0);
            }
        } catch (Exception e) {
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }

    }

    public String GetLPDayBuffer(String sDay) {
        String sResult = "";
        try {

            if (pLPBuffer.containsKey(sDay)) {
                return pLPBuffer.getProperty(sDay);
            }
            sResult = util.ZipFiles.GetZipFileContent(sFileName + "-" + sDay + sFileExt + ".zip");
            if ((sResult == null) || (sResult.length() < 1)) {
                sResult = util.FileUtil.LoadFromFile(sFileName + "-" + sDay + sFileExt);
            }
            if ((sResult != null) || (sResult.length() > 0)) {
                PutLPDayBuffer(sDay, sResult);
                return sResult;
            }
            return "";

        } catch (Exception e) {
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
        return sResult;
    }

    int iIter = 0;
    String[] sTemp;
    Vector<Hashtable> vRBACFiles = new Vector<Hashtable>();
    Hashtable<String, Object> htRBACFile;
    String sReq = "";

    Properties pRBACSettings;

    public void ServeLoop() {
        sTemp = PropUtil.GetString(pAttributes, "sRBACFiles", "").split(",");
        for (iIter = 0; iIter < sTemp.length; iIter++) {
            try {
                pRBACSettings = new Properties();
                PropUtil.LoadFromFile(pRBACSettings, sTemp[iIter]);
                htRBACFile = new Hashtable<String, Object>();
                //htRBACFile.put("pRBACSettings", pRBACSettings);
                htRBACFile.put("dtFrom",
                        PropUtil.GetDate(pRBACSettings, "dtFrom", new Date()));
                htRBACFile.put("dtTo",
                        PropUtil.GetDate(pRBACSettings, "dtTo", new Date()));
                htRBACFile.put("lMinPeriod",
                        PropUtil.GetInt(pRBACSettings, "lMinPeriod", 60));
                htRBACFile.put("lMaxRespLen",
                        PropUtil.GetInt(pRBACSettings, "lMaxRespLen", 1000));
                htRBACFile.put("lMaxRespDuration",
                        PropUtil.GetInt(pRBACSettings, "lMaxRespDuration", 60000));
                htRBACFile.put("sProfileContent",
                        PropUtil.GetString(pRBACSettings, "sProfileContent", "").toString());
                htRBACFile.put("sReqAttr",
                        PropUtil.GetString(pRBACSettings, "sReqAttr", ""));
                htRBACFile.put("sRespAttr",
                        PropUtil.GetString(pRBACSettings, "sRespAttr", ""));

                htRBACFile.put("lLastRespTime", (long) 0);
                pDataSet.put(htRBACFile.get("sReqAttr").toString(), " ");

                vRBACFiles.add(htRBACFile);
            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }
        }
        long lLastRespTime = 0;
        while (bStop == 0) {
            try {

                Thread.sleep(lServePeriod);
                for (iIter = 0; iIter < vRBACFiles.size(); iIter++) {
                    try {
                        htRBACFile = vRBACFiles.elementAt(iIter);
                        lLastRespTime = (long) htRBACFile.get("lLastRespTime");
                        if (lLastRespTime > 0) {
                            if ((new Date()).getTime() - lLastRespTime > (Integer) htRBACFile.get("lMaxRespDuration") * 1000) {
                                pDataSet.put(htRBACFile.get("sRespAttr").toString(), " ");
                                htRBACFile.put("lLastRespTime", (long) 0);
                            }
                        }
                        sReq = pDataSet.getProperty(htRBACFile.get("sReqAttr").toString());
                        if ((sReq != null) && (sReq.length() > 1)) {
                            if ((sReq.contains("reset")) || (sReq.contains("clear"))) {
                                pDataSet.put(htRBACFile.get("sRespAttr").toString(), " ");
                            } else {
                                pDataSet.put(htRBACFile.get("sReqAttr").toString(), " ");
                                pDataSet.put(htRBACFile.get("sRespAttr").toString(),
                                        "{\r\n\"Status\":\"" + "Processing request" + "\"" + "\r\n}");
                                pDataSet.put(htRBACFile.get("sRespAttr").toString(),
                                        GetLP(sReq, htRBACFile));
                                htRBACFile.put("lLastRespTime", (new Date()).getTime());
                            }
                        }
                    } catch (Exception ex) {
                        if (Debug == 1) {
                            System.out.println(ex.getMessage());
                        }
                    }
                }

            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }

    public String GetLP(String sReq, Hashtable<String, Object> htRBAC) {
        StringBuffer sbResult = new StringBuffer();
        StringBuffer sbLine = new StringBuffer();
        //Hashtable<String, Object> htReqParams = new Hashtable<String, Object>();
        Hashtable<String, Integer> htLPContentPos = new Hashtable<String, Integer>();
        Vector<String> vLPContent = new Vector<String>();
        String[] sLines;
        String[] sVals;
        String sRBACContent;

        Date dtFrom = new Date();
        Date dtTo = new Date();
        int iPeriod = 0;
        int iMaxRespLen = 30000;

        String sProfile = "";

        int i;
        int j;
        try {
            sRBACContent = (String) htRBAC.get("sProfileContent");
            sProfile = sRBACContent;
            sReq = sReq.replace(" ", "");
            sReq = sReq.replace("\"", "");
            sReq = sReq.replace("\r", "");
            sReq = sReq.replace("\n", "");
            sReq = sReq.replace("{", "");
            sReq = sReq.replace("}", "");
            sLines = sReq.split(",");
            for (i = 0; i < sLines.length; i++) {
                try {
                    if ((sLines[i] == null) || (sLines[i].length() < 1)) {
                        continue;
                    }
                    sLines[i] = sLines[i].replaceFirst(":", "\r");
                    sVals = sLines[i].split("\r");
                    if (sVals.length == 2) {
                        if (sVals[0].contains("From")) {
                            dtFrom = sdfQryDate.parse(sVals[1]);
                        } else if (sVals[0].contains("To")) {
                            dtTo = sdfQryDate.parse(sVals[1]);
                        } else if (sVals[0].contains("Period")) {
                            iPeriod = Integer.parseInt(sVals[1]);
                        } else if (sVals[0].contains("MaxRespLen")) {
                            iMaxRespLen = Integer.parseInt(sVals[1]);
                        } else if (sVals[0].contains("Profile")) {
                            sProfile = sVals[1];
                        }
                    }
                } catch (Exception ex) {
                }
            }
            //Check RBAC
            if (dtFrom.before((Date) htRBAC.get("dtFrom"))) {
                dtFrom = (Date) htRBAC.get("dtFrom");
            }
            if (dtTo.after((Date) htRBAC.get("dtTo"))) {
                dtTo = (Date) htRBAC.get("dtTo");
            }
            if (iPeriod < (int) htRBAC.get("lMinPeriod")) {
                iPeriod = (int) htRBAC.get("lMinPeriod");
            }
            if (iMaxRespLen > (int) htRBAC.get("lMaxRespLen")) {
                iMaxRespLen = (int) htRBAC.get("lMaxRespLen");
            }
            String[] sProfileContent;
            //  if((sProfile==null)||(sProfile.length()<1)){
            //      sProfile=sRBACContent;
            //  }
            sProfileContent = sProfile.split(";");
            for (String sProfileEntry : sProfileContent) {
                if (sRBACContent.contains(sProfileEntry)) {
                    htLPContentPos.put(sProfileEntry, -1);
                    vLPContent.add(sProfileEntry);
                }
            }
            if (vLPContent.size() < 1) {
                return "{\r\n\"Status\":\"Parameter ERROR\" \r\n}";
            }

            sbResult.append("{\r\n");
            sbResult.append("\"From\":\"" + sdfLogDate.format(dtFrom) + "\"," + "\r\n");
            sbResult.append("\"To\":\"" + sdfLogDate.format(dtTo) + "\"," + "\r\n");
            sbResult.append("\"Period\":\"" + Integer.toString(iPeriod) + "\"," + "\r\n");
            sbResult.append("\"Profile\":\"");
            for (j = 0; j < vLPContent.size(); j++) {
                if (vLPContent.get(j).length() < 1) {
                    continue;
                }
                if (j > 0) {
                    sbResult.append(";");
                }
                sbResult.append(vLPContent.get(j));
            }
            sbResult.append("\"," + "\r\n");
            sbResult.append("\"Buffer\":");
            sbResult.append("[" + "\r\n");
            //
            int iNoOfLines = 0;
            int iColNo = 0;
            Date dtToday = new Date();
            String sFromDate = sdf.format(dtFrom);
            String sTodayDate = sdf.format(dtToday);
            String sCrtDayLP = "";
            //String sCrtLine = "";

            Date dtLineDate = new Date();
            while ((dtFrom.getTime() <= dtTo.getTime()) && (dtToday.getTime() >= dtFrom.getTime())) {
                try {
                    sFromDate = sdf.format(dtFrom);
                    if (sFromDate.equals(sTodayDate)) {
                        sCrtDayLP = sTodayLP;
                    } else {
                        sCrtDayLP = GetLPDayBuffer(sFromDate);
                    }
                    sCrtDayLP = sCrtDayLP.replace("\r", "");
                    sLines = sCrtDayLP.split("\n");
                    for (i = 0; i < sLines.length; i++) {
                        try {
                            if ((sLines[i] == null) || (sLines[i].length() < 1)) {
                                continue;
                            }
                            //
                            sVals = sLines[i].split(sSeparator);
                            try {
                                dtLineDate = sdfLogDateMs.parse(sVals[0]);
                            } catch (Exception exce) {
                                dtLineDate = null;
                            }

                            if (dtLineDate == null) {
                                //Column names line
                                for (j = 0; j < vLPContent.size(); j++) {
                                    htLPContentPos.put(vLPContent.elementAt(j), -1);
                                }
                                for (j = 0; j < sVals.length; j++) {
                                    if (htLPContentPos.containsKey(sVals[j])) {
                                        htLPContentPos.put(sVals[j], j);
                                    }
                                }
                            } else if (((dtLineDate.getTime() / 1000) % iPeriod == 0)
                                    && (dtLineDate.getTime() >= dtFrom.getTime())
                                    && (dtLineDate.getTime() <= dtTo.getTime() + 999)) {
                                //sCrtLine = "";
                                sbLine.delete(0, sbLine.length());
                                if (iNoOfLines > 0) {
                                    sbLine.append(",");
                                    // sCrtLine += ",";
                                }
                                iNoOfLines++;
                                sbLine.append("\"");
                                //sCrtLine +="\"";
                                for (j = 0; j < vLPContent.size(); j++) {
                                    if (vLPContent.get(j).length() < 1) {
                                        continue;
                                    }
                                    if (j > 0) {
                                        sbLine.append(";");
                                        //sCrtLine += ";";
                                    }
                                    iColNo = (int) htLPContentPos.get(vLPContent.get(j));
                                    if (iColNo >= 0) {

                                        sbLine.append(sVals[iColNo]);
                                        //sCrtLine += sVals[iColNo];
                                    }
                                }
                                sbLine.append("\"" + "\r\n");
                                //sCrtLine += "\"" + "\r\n";
                                if (sbResult.length() + sbLine.length() > iMaxRespLen) {
                                    sbResult.append("],\r\n");
                                    sbResult.append("\"Status\":\"" + "INCOMPLETE" + "\"" + "\r\n");
                                    sbResult.append("}");
                                    return sbResult.toString();
                                }
                                sbResult.append(sbLine);
                            }

                        } catch (Exception exc) {
                            if (Debug == 1) {
                                System.out.println(exc.getMessage());
                            }
                        }
                    }
                } catch (Exception ex) {
                    if (Debug == 1) {
                        System.out.println(ex.getMessage());
                    }
                }
                //dtFrom.setTime((dtFrom.getTime() - dtFrom.getTime() % lNoOfDaySec) + lNoOfDaySec);
                dtFrom = new Date(dtFrom.getYear(), dtFrom.getMonth(), dtFrom.getDate(), 0, 0, 0);
                dtFrom = new Date(dtFrom.getTime() + lNoOfDaySec);
            }
        } catch (Exception e) {
        }
        sbResult.append("],\r\n");
        sbResult.append("\"Status\":\"" + "COMPLETE" + "\"" + "\r\n");
        sbResult.append("}");
        // sbResult.append("]\r\n}");
        return sbResult.toString();
    }
    public static long lNoOfDaySec = 24 * 3600 * 1000;

}
