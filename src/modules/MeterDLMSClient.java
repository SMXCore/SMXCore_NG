/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;


import gurux.GXCommunicate;
import static gurux.GXCommunicate.traceLn;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;

import gurux.common.IGXMedia;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.enums.Authentication;
import gurux.dlms.manufacturersettings.GXManufacturer;
import gurux.dlms.manufacturersettings.GXManufacturerCollection;
import gurux.dlms.objects.GXDLMSClock;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSObjectCollection;
import gurux.io.BaudRate;
import gurux.io.Parity;
import gurux.io.StopBits;
import gurux.net.GXNet;
import gurux.net.enums.NetworkType;
import gurux.serial.GXSerial;
import gurux.terminal.GXTerminal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;

import java.util.Properties;
import java.util.TimeZone;
import java.util.Vector;
import util.FileUtil;
import util.GuruxUtil;
import util.PropUtil;

/**
 *
 * @author cristi, mihai, vlad
 */
public class MeterDLMSClient extends Module {

    DateFormat df1 = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    DateFormat df2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public void Initialize() {

        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);
        sCmdLineArgs = PropUtil.GetString(pAttributes, "sCmdLineArgs", "");
        ssCmdLineArgs = sCmdLineArgs.split(" ");
        sLogFile = PropUtil.GetString(pAttributes, "sLogFile", "");
        sLogFile += "-" + sName + ".txt";
        sPath = PropUtil.GetString(pAttributes, "sPath", "");
        sProfilePath = PropUtil.GetString(pAttributes, "sProfilePath", "");

        sObjDateFile = PropUtil.GetString(pAttributes, "sObjDateFile", "");
        sObj1File = PropUtil.GetString(pAttributes, "sObj1File", "");
        sObj2File = PropUtil.GetString(pAttributes, "sObj2File", "");
        sObj3File = PropUtil.GetString(pAttributes, "sObj3File", "");
        sObj4File = PropUtil.GetString(pAttributes, "sObj4File", "");

        iObisFilesArrayEnable = PropUtil.GetInt(pAttributes, "iObisFilesArrayEnable", 0);
        iObisFilesArrayNumber = PropUtil.GetInt(pAttributes, "iObisFilesArrayNumber", 4);
        if(iObisFilesArrayNumber>=120) iObisFilesArrayNumber = 120;
        if(iObisFilesArrayEnable==1) for(int i1=0; i1<iObisFilesArrayNumber; i1++) {
            // Up to 20 files can be read
            sObjFiles[i1] = PropUtil.GetString(pAttributes, "sObjFiles"+i1, "");
        }

        sProf1File = PropUtil.GetString(pAttributes, "sProf1File", "");

        iBlockRead = PropUtil.GetInt(pAttributes, "iBlockRead", 0);
        iProfilesRead = PropUtil.GetInt(pAttributes, "iProfilesRead", 0);

        iMultiSingleOBIS = PropUtil.GetInt(pAttributes, "iMultiSingleOBIS", 0);
        iObisFilesNumber = PropUtil.GetInt(pAttributes, "iObisFilesNumber", 3);

        iDebugModelDLMS = PropUtil.GetInt(pAttributes, "iDebugModelDLMS", 0);
        //com.iDebugModelDLMS_local = iDebugModelDLMS; // debug mode is set also for GXCommunicate module

        iDelayBetweenMessages = PropUtil.GetInt(pAttributes, "iDelayBetweenMessages", 1);
        iPrintlnMessages = PropUtil.GetInt(pAttributes, "iPrintlnMessages", 1);

        df1.setTimeZone(TimeZone.getTimeZone("UTC"));
        df2.setTimeZone(TimeZone.getTimeZone("UTC"));

        sReadDisableParam = PropUtil.GetString(pAttributes, "sReadDisableParam", "");
        if (sReadDisableParam.length() < 2) {
            sReadDisableParam = this.sName + "sReadDisableParam";
            pDataSet.put(sReadDisableParam, "0");
        }

        sReadDisabledParam = PropUtil.GetString(pAttributes, "sReadDisabledParam", "");
        if (sReadDisabledParam.length() < 2) {
            sReadDisabledParam = this.sName + "sReadDisabledParam";
            pDataSet.put(sReadDisabledParam, "0");
        }

        //insert in database metadata 
        String sTSDate;
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        sTSDate = sdf.format(new Date());
        pDataSet.put("Module/MeterDLMSClient/"+sName+"/StartDateTime", sTSDate); // DateTime
        String s1;
        s1 = sCmdLineArgs; pDataSet.put("Module/MeterDLMSClient/"+sName+"/sCmdLineArgs", s1); // 
        s1 = String.valueOf(iObisFilesArrayEnable); pDataSet.put("Module/MeterDLMSClient/"+sName+"/iObisFilesArrayEnable", s1); // 
        s1 = String.valueOf(iObisFilesArrayNumber); pDataSet.put("Module/MeterDLMSClient/"+sName+"/iObisFilesArrayNumber", s1); // 
        //pDataSet.put("Module/MeterDLMSClient/"+sName+"/iObisFilesNumber", iObisFilesNumber); // DateTime
        s1 = String.valueOf(iDelayBetweenMessages); pDataSet.put("Module/MeterDLMSClient/"+sName+"/iDelayBetweenMessages", s1); // 
    }

    int iReadDisable = 0;
    int iReadDisabledParam = 1;
    String sReadDisableParam = "";
    String sReadDisabledParam = "";
    public static int iPrintlnMessages = 0; // 64 means all (111111); 0 means nothing (000000)

    Properties pDataSet = null;
    String sPrefix = "";
    String sCmdLineArgs = "";
    String[] ssCmdLineArgs;
    String sLogFile = "";
    String sPath = "";
    String sProfilePath = "";

    String sObjDateFile = "";
    String sObj1File = "";
    String sObj2File = "";
    String sObj3File = "";
    String sObj4File = "";
    String[] sObjFiles = {"", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                          "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", 
                          "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", 
                          "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", 
                          "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", 
                          "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", 
                          "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""}; // a number of 140 files is initialized

    String sProf1File = "";

    int iBlockRead = 0;
    int iProfilesRead = 0;

    int iMultiSingleOBIS = 0;
    int iObisFilesNumber = 3;

    int iObisFilesArrayEnable = 0; // Default it is not enabled the array of Obis files
    int iObisFilesArrayNumber = 0; // If iObisFilesArrayEnable is "1", then the default number of Obis files is defined here
    

    public int iDebugModelDLMS = 0; // Bit 0:  0=no debug; 1=debug // Bit 1: 0=on the run debug; 1=buffered debug // bit 2: level of debug
    
    Thread tMeterCalc = null;

    @Override
    public void Start() {
        try {
            tMeterCalc = new Thread(new Runnable() {

                @Override
                public void run() {
                    Calculate();
                }
            });
            tMeterCalc.start();

        } catch (Exception e) {

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

    public boolean CheckDisableRead() {

        iReadDisable = PropUtil.GetInt(pDataSet, sReadDisableParam, 0);
        if (iReadDisable == 0) {
            pDataSet.put(sReadDisabledParam, "0");
            return false;
        }
        iReadDisabledParam = PropUtil.GetInt(pDataSet, sReadDisabledParam, 0);
        if (iReadDisabledParam == 1) {
            return true;
        }

        try {
            com.close();
        } catch (Exception e) {
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
        pDataSet.put(sReadDisabledParam, "1");
        return true;

    }

    public void Calculate() {

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
                    /*   ReadIniVals();
                     Calc();
                     WritePrimaryVals();
                     WriteVals(); */
                    continue;
                }

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
                if (CheckDisableRead()) {
                    continue;
                }
                QueryMeter();

            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }

    GXCommunicate com = null;
    PrintWriter logFile = null;

    GXDLMSObjectCollection objectDate = new GXDLMSObjectCollection();
    GXDLMSObjectCollection objects1 = new GXDLMSObjectCollection();
    GXDLMSObjectCollection objects2 = new GXDLMSObjectCollection();
    GXDLMSObjectCollection objects3 = new GXDLMSObjectCollection();
    GXDLMSObjectCollection objects4 = new GXDLMSObjectCollection();
    // A collection of 24 objects is initialized for the array of OBIS files
    GXDLMSObjectCollection[] objects_arr  = new GXDLMSObjectCollection[121];
    //objects_arr[1] = new GXDLMSObjectCollection();
    
    //Carinfo[] mycars = new Carinfo[1];
    //mycars[0] = new Carinfo("Lada", 9000);

//
    GXDLMSObjectCollection profiles1 = new GXDLMSObjectCollection();
    int iNoOfProfiles = 0;
    int iCrtProfileNo = 1;
    GXDLMSObject it = null;
    int pos = 2;

    Vector<Hashtable> vProfilesDetail = new Vector<>();
    Hashtable<String, Object> htProfileDetail;
    long iCrtProfilePos = 0;
    long iNewProfilePos = 0;
    String s = "";
    
    boolean isInit=false;
    
    int iDelayBetweenMessages = 50;

    public void QueryMeter() {
        // TODO code application logic here
        if(iPrintlnMessages==64) System.out.println("QueryMeter()");
        try {
            if (com == null) {
                if (logFile == null) {
                    logFile = new PrintWriter(
                            new BufferedWriter(new FileWriter(sLogFile)));
                }
                // Gurux example server parameters.
                // /m=lgz /h=localhost /p=4060
                // /m=grx /h=localhost /p=4061
                com = getManufactureSettings(ssCmdLineArgs);
                // If help is shown.
                if(iPrintlnMessages==64) System.out.println("com = getManufactureSettings: " + ssCmdLineArgs.length);
                if (com == null) {
                    if(iPrintlnMessages==64) System.out.println("com == null");
                    return;
                }
                if (CheckDisableRead()) {
                    if(iPrintlnMessages==64) System.out.println("CheckDisableRead()");
                    return;
                }

                if(iPrintlnMessages==64) System.out.println("com.initializeConnection() to start");
                com.initializeConnection();
                if(iPrintlnMessages==64) System.out.println("com.initializeConnection() done");
                try {
                    // com.readAndPrintAllObjects(logFile);
                    if(!isInit){
                    if (sObjDateFile.length() > 0) {
                        objectDate = objectDate.load(sObjDateFile);
                    } else {
                        objectDate.add(new GXDLMSClock());
                    }
                    com.readClockAttr(objectDate.get(0));
                    //com.readScalerAndUnits(objectDate, logFile);
                    if (CheckDisableRead()) {
                        return;
                    }
                    traceLn(logFile, "iObisFilesNumber="+iObisFilesNumber);

                    traceLn(logFile, "sObj1File="+sObj1File);
                    objects1 = objects1.load(sObj1File);
                    if(iPrintlnMessages==64) System.out.println("Rsau1a");
                    try { com.readScalerAndUnits(objects1, logFile, "O1");} catch(Exception exc) {System.out.println("Rsau1exc: " + exc.getMessage());};
                    if(iPrintlnMessages==64) System.out.println("Rsau1b");
                    //traceLn(logFile, "rsc1: " + exc.getMessage());
                    if (CheckDisableRead()) {     
                        return;
                    }
                    // GuruxUtil.GXDLMSObjectSaveWithVals(objects1, "tstObjSave.txt");
                    objects2 = objects2.load(sObj2File);
                    traceLn(logFile, "sObj2File="+sObj2File);
                    traceLn(logFile, "Rsau2a");
                    try { com.readScalerAndUnits(objects2, logFile, "O2");} catch(Exception exc) {
                        traceLn(logFile, "Rsau2exc: " + exc.getMessage());};
                        traceLn(logFile, "Rsau2b");
                        if (CheckDisableRead()) {
                            return;
                        }
                    // Object no. 3
                    objects3 = objects3.load(sObj3File);
                    traceLn(logFile, "sObj3File="+sObj3File);
                    traceLn(logFile, "readScalerAndUnits_3start");
                    try { com.readScalerAndUnits(objects3, logFile, "O3");} catch(Exception exc) {traceLn(logFile, "Rsau3exc: " + exc.getMessage());};
                    traceLn(logFile, "readScalerAndUnits_3stop");
                    
                    if(iObisFilesNumber>3) {
                        objects4 = objects4.load(sObj4File);
                        traceLn(logFile, ">sObj4File="+sObj4File);
                        traceLn(logFile, "readScalerAndUnits_4start");
                        try { com.readScalerAndUnits(objects4, logFile, "O4");} catch(Exception exc) {traceLn(logFile, "Rsau4exc: " + exc.getMessage());};
                        traceLn(logFile, "readScalerAndUnits_4stop");
                    }

                    
                    if(iObisFilesArrayEnable==1) {
                        traceLn(logFile, "iObisFilesArrayEnable="+iObisFilesArrayEnable+"\n>>readScalerAndUnits");
                        for(int i1=0; i1<iObisFilesArrayNumber; i1++) {
                            objects_arr[i1] = objects_arr[i1].load(sObjFiles[i1]);
                            traceLn(logFile, ">sObjFiles["+i1+"]="+sObjFiles[i1]);
                            traceLn(logFile, "readScalerAndUnits_Arr["+i1+"]start");
                            try { com.readScalerAndUnits(objects_arr[i1], logFile, "OArr"+i1);} catch(Exception exc) {traceLn(logFile, "RsaArr"+i1+": " + exc.getMessage());};
                            traceLn(logFile, "readScalerAndUnits_Arr["+i1+"]stop");
                        }
                    }

                    if (iProfilesRead == 1) {
                        vProfilesDetail.clear();
                        if (CheckDisableRead()) {
                            return;
                        }
                        profiles1 = profiles1.load(sProf1File);
                        com.readProfileGenericColumnsString(profiles1, logFile);
                        if (CheckDisableRead()) {
                            return;
                        }
                        com.readProfileGenericsAttr(profiles1, logFile);
                        iNoOfProfiles = profiles1.size();

                        for (iCrtProfileNo = 1; iCrtProfileNo < iNoOfProfiles + 1; iCrtProfileNo++) {
                            htProfileDetail = new Hashtable<>();
                            it = profiles1.get(iCrtProfileNo - 1);
                            s = com.getProfileGenericColumnsString(it);
                            htProfileDetail.put("ColumnsString", s);
                            FileUtil.SaveToFile(sProfilePath + "/" + it.getLogicalName().replace('.', '-') + ".txt",
                                    s + "\r\n");
                            if (CheckDisableRead()) {
                                return;
                            }
                            com.updateProfileGenericsDetails(htProfileDetail, it, 8, logFile);
                            vProfilesDetail.add(htProfileDetail);
                        }
                        iCrtProfileNo = 1;
                        isInit=true;
                    }
                        // com.readProfileGenericsLast5Rows(profiles1, logFile);
                    }
                } catch (Exception exc) {
                }

            }
            //Read Date
            if (objectDate != null) {
                if (CheckDisableRead()) {
                    return;
                }
                Calendar c_smx = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                s = com.readDate(objectDate.get(0), pos);
                if (s.length() > 1) {
                    sOBISPath = sPrefix + objectDate.get(0).getLogicalName().replace('.', '-');
                    pDataSet.put(sOBISPath + "/" + "-2", s);
                }
                GuruxUtil.GXDLMSObjectSaveWithVals(objectDate, "tstObjDSave.txt");

                //If this is requesting mutiple single OBIS requests, such is the case with SLAM V1.0, handle time
                if (iMultiSingleOBIS > 0) {
                    if (c_smx.get(Calendar.YEAR) < 2015) {
                        traceLn(logFile, "Setting SMX time...");
                        //SMX is assumed not to have the correct time. Retrieve time from SMM and set SMX. NTP will perform adjustments afterwards.
                        Date d = df1.parse(s);
                        String strDateTimeToSet = df2.format(d);
                        Runtime.getRuntime().exec("date -u -s " + strDateTimeToSet);
                    } else {
                        //SMX is assumed to have a correct time, check if SMM clock differs and set it if necessary
                        String s_smx = df1.format(c_smx.getTime());
                        traceLn(logFile, "SMM time " + s);
                        traceLn(logFile, "SMX time " + s_smx);
                        if (!s_smx.equals(s)) {
                            traceLn(logFile, "Setting SMM time...");
                            //Set SMM clock. Next second is sent. SMM will apply this time the next time time_sync signal gets activated
                            GXDLMSClock clock = new GXDLMSClock();
                            Date now = new Date();
                            Date nowPlus1Sec = new Date(now.getTime() + 1000);
                            clock.setTime(new GXDateTime(nowPlus1Sec));
                            com.writeObject(clock, 2);
                        }
                    }
                }

            }

            if (iBlockRead == 1) {
                com.Debug_String1="";
                //com.readAllObjects(logFile);
                if (objects1 != null) {
                    if (CheckDisableRead()) {
                        return;
                    }
                    Thread.sleep(iDelayBetweenMessages); // a delay between each two queries, such that the meter can recover
                    com.readRegisters(objects1, logFile);

                    WriteVals(objects1);
                    GuruxUtil.GXDLMSObjectSaveWithVals(objects1, "tstObj1Save.txt");
                }
                if (objects2 != null) {
                    if (CheckDisableRead()) {
                        return;
                    }
                    Thread.sleep(iDelayBetweenMessages); // a delay between each two queries, such that the meter can recover
                    com.readRegisters(objects2, logFile);

                    WriteVals(objects2);
                    GuruxUtil.GXDLMSObjectSaveWithVals(objects2, "tstObj2Save.txt");
                }
                if (objects3 != null) {
                    if (CheckDisableRead()) {
                        return;
                    }
                    Thread.sleep(iDelayBetweenMessages); // a delay between each two queries, such that the meter can recover
                    com.readRegisters(objects3, logFile);

                    WriteVals(objects3);
                    GuruxUtil.GXDLMSObjectSaveWithVals(objects3, "tstObj3Save.txt");
                }
                if(iObisFilesNumber>3) {
                    if (objects4 != null) {
                        if (CheckDisableRead()) {
                            return;
                        }
                        Thread.sleep(iDelayBetweenMessages); // a delay between each two queries, such that the meter can recover
                        com.readRegisters(objects4, logFile);

                        WriteVals(objects4);
                        GuruxUtil.GXDLMSObjectSaveWithVals(objects4, "tstObj4Save.txt");
                    }
                }
                // Array of OBIS files
                if(iObisFilesArrayEnable==1) {
                    traceLn(logFile, "iObisFilesArrayEnable="+iObisFilesArrayEnable+"\n>>readRegisters");
                    for(int i1=0; i1<iObisFilesArrayNumber; i1++) {
                        if (objects_arr[i1] != null) {
                            if (CheckDisableRead()) {
                                return;
                            }
                            com.ObjFileCrt = sObjFiles[i1];
                            com.readRegisters(objects_arr[i1], logFile);

                            WriteVals(objects_arr[i1]);
                            GuruxUtil.GXDLMSObjectSaveWithVals(objects_arr[i1], "tstObjArray"+i1+"Save.txt");
                        }
                    }
                }

            } else {
                if (objects1 != null) {
                    if (CheckDisableRead()) {
                        return;
                    }
                    com.ObjFileCrt = sObj1File;
                    com.readValues2(objects1, logFile);
                    WriteVals(objects1);
                }
                if (objects2 != null) {
                    if (CheckDisableRead()) {
                        return;
                    }
                    com.ObjFileCrt = sObj2File;
                    com.readValues2(objects2, logFile);
                    WriteVals(objects2);
                }
                if (objects3 != null) {
                    if (CheckDisableRead()) {
                        return;
                    }
                    com.ObjFileCrt = sObj3File;
                    com.readValues2(objects3, logFile);
                    WriteVals(objects3);
                }
                if(iObisFilesNumber>3) if (objects4 != null) {
                    if (CheckDisableRead()) {
                        return;
                    }
                    com.ObjFileCrt = sObj4File;
                    com.readValues2(objects4, logFile);
                    WriteVals(objects4);
                }
            }
                // Array of OBIS files
                if(iObisFilesArrayEnable==1) {
                    traceLn(logFile, "iObisFilesArrayEnable="+iObisFilesArrayEnable+"\n>>readValues2");
                    for(int i1=0; i1<iObisFilesArrayNumber; i1++) {
                        if (objects_arr[i1] != null) {
                            if (CheckDisableRead()) {
                                return;
                            }
                            com.ObjFileCrt = sObjFiles[i1];
                            com.readValues2(objects_arr[i1], logFile);
                            WriteVals(objects_arr[i1]);
                        }                    
                    }
                }
            if (iProfilesRead == 1) {
                if (CheckDisableRead()) {
                    return;
                }
                if (++iCrtProfileNo > iNoOfProfiles) {
                    iCrtProfileNo = 1;
                }
                try {
                    it = profiles1.get(iCrtProfileNo - 1);
                    htProfileDetail = vProfilesDetail.elementAt(iCrtProfileNo - 1);
                    iCrtProfilePos = ((Date) htProfileDetail.get("lLastDate")).getTime();
                    com.readProfileGenericsCurrentRows(htProfileDetail, it, 0, logFile);
                    //com.readProfileGenericsLastNRows(it, 10, logFile);
                    //com.readProfileGenericsLast5Rows(profiles1, logFile);

                    iNewProfilePos = ((Date) htProfileDetail.get("lLastDate")).getTime();
                    if (iCrtProfilePos == iNewProfilePos) {
                    } else {
                        sOBISPath = sPrefix + it.getLogicalName().replace('.', '-');
                        pDataSet.put(sOBISPath + "/" + "-2", (String) htProfileDetail.get("sLastRow"));
                        if (sProfilePath.length() > 1) {
                            FileUtil.SaveToFile(sProfilePath + "/" + it.getLogicalName().replace('.', '-') + ".txt",
                                    (String) htProfileDetail.get("sLastRow") + "\r\n");
                        }
                    }
                } catch (Exception ex3) {
                    if(iPrintlnMessages==64) System.out.println(ex3.toString());
                }

            }
        } catch (Exception e) {
            //  if (logFile != null) {
            //      logFile.close();
            //  }
            try {
                // Disconnect.
                if (com != null) {
                    com.close();
                }
            } catch (Exception Ex2) {
                System.out.println(Ex2.toString());
            }
            com = null;
            if(iPrintlnMessages==64) System.out.println(e.toString());
        } finally {
            /*   if (logFile != null) {
             logFile.close();
             }
             try {
             ///////////////////////////////////////////////////////////////
             // Disconnect.
             if (com != null) {
             com.close();
             }
             } catch (Exception Ex2) {
             System.out.println(Ex2.toString());
             }
             }
             System.out.println("Done!");*/
        }
        String sTSDate="None";
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        sTSDate = sdf.format(new Date());
        if((com.iDebugModelDLMS_local & 0x01)==1) com.writeTrace("Buffered Debug: "+sTSDate+" ; iObisFilesNumber="+iObisFilesNumber+com.Debug_String1);

        //com.writeTrace("\n**"+sTSDate+" Debug:"+com.iDebugModelDLMS_local+" ; iDebugModelDLMS="+ iDebugModelDLMS +" ; iObisFilesNumber="+iObisFilesNumber);
    }
    private final SimpleDateFormat sdfDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    String sDate = "";
    String sOBISPath = "";
    Date dtDate;

    public void WriteVals(GXDLMSObjectCollection objects) {
        sDate = sdfDate.format(new Date());
        StringBuilder sb = new StringBuilder();
        int iNoA = 0;
        int i;
        sb.append('[');
        for (GXDLMSObject it : objects) {
            if (sb.length() != 1) {
                //sb.append(", ");
                sb.append("\r\n");
            }
            sOBISPath = sPrefix + it.getLogicalName().replace('.', '-');
            pDataSet.put(sOBISPath + "/" + "-5", sDate);
            if (it instanceof GXDLMSClock) {
                try {
                    dtDate = ((GXDLMSClock) it).getTime().toMeterTime();
                    pDataSet.put(sOBISPath + "/" + "-2", df1.format(dtDate).toString());
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            } else {
                try {
                    pDataSet.put(sOBISPath + "/" + "-2", it.getValues()[1].toString());
                } catch (Exception e) {
                    if(iPrintlnMessages==64) System.out.println(e.getMessage());
                }
            }
            // GXDLMSAttributeSettings gas=it.getAttributes().find(1);
            iNoA = it.getValues().length;

            for (i = 0; i < iNoA; i++) {
                try {
                    pDataSet.put(sOBISPath + "/" + Integer.toString(i + 1),
                            it.getValues()[i].toString());
                    sb.append(it.getValues()[i].toString() + "~");
                } catch (Exception e) {
                    //System.out.println(e.getMessage());
                }
            }
        }
        sb.append(']');
        if(iPrintlnMessages==64) System.out.println(sb.toString());
    }

    /**
     * Show help.
     */
    static void ShowHelp() {
        System.out.println(
                "GuruxDlmsSample reads data from the DLMS/COSEM device.");
        System.out.println("");
        System.out.println(
                "GuruxDlmsSample /m=lgz /h=www.gurux.org /p=1000 [/s=] [/u]");
        System.out.println(" /m=\t manufacturer identifier.");
        System.out.println(" /sp=\t serial port. (Example: COM1)");
        System.out.println(" /n=\t Phone number.");
        System.out.println(" /b=\t Serial port baud rate. 9600 is default.");
        System.out.println(" /h=\t host name.");
        System.out.println(" /p=\t port number or name (Example: 1000).");
        System.out.println(" /s=\t start protocol (IEC or DLMS).");
        System.out.println(" /a=\t Authentication (None, Low, High).");
        System.out.println(" /pw=\t Password for authentication.");
        System.out.println(" /t\t Trace messages.");
        System.out
                .println(" /u\t Update meter settings from Gurux web portal.");
        System.out.println("Example:");
        System.out.println("Read LG device using TCP/IP connection.");
        System.out.println("GuruxDlmsSample /m=lgz /h=www.gurux.org /p=1000");
        System.out.println("Read LG device using serial port connection.");
        System.out.println("GuruxDlmsSample /m=lgz /sp=COM1 /s=DLMS");
    }

    /**
     * Get manufacturer settings from Gurux web service if not installed yet.
     * This is something that you do not necessary seed. You can // hard code
     * the settings. This is only for demonstration. Use hard coded settings
     * like this:
     * <p/>
     * GXDLMSClient cl = new GXDLMSClient(true, 16, 1, Authentication.NONE,
     * null, InterfaceType.HDLC);
     *
     * @param args Command line argumens.
     * @return
     * @throws Exception
     */
    GXCommunicate getManufactureSettings(String[] args)
            throws Exception {
        IGXMedia media = null;
        GXCommunicate com;
        String path = sPath;
        try {
            if (GXManufacturerCollection.isFirstRun(path)) {
                GXManufacturerCollection.updateManufactureSettings(path);
            }
        } catch (Exception ex) {
            if(iPrintlnMessages==64) System.out.println(ex.toString());
        }
        // 4059 is Official DLMS port.
        String id = "", host = "", port = "4059", pw = "";
        boolean trace = false, iec = true;
        Authentication auth = Authentication.NONE;
        int startBaudRate = 9600;
        String number = null;
        for (String it : args) {
            String item = it.trim();
            if (item.compareToIgnoreCase("/u") == 0) {
                // Update
                // Get latest manufacturer settings from Gurux web server.
                GXManufacturerCollection.updateManufactureSettings(path);
            } else if (item.startsWith("/m=")) {
                // Manufacturer
                id = item.replaceFirst("/m=", "");
            } else if (item.startsWith("/h=")) {
                // Host
                host = item.replaceFirst("/h=", "");
            } else if (item.startsWith("/p=")) {
                // TCP/IP Port
                media = new gurux.net.GXNet();
                port = item.replaceFirst("/p=", "");
            } else if (item.startsWith("/sp=")) {
                // Serial Port
                if (media == null) {
                    media = new gurux.serial.GXSerial();
                }
                port = item.replaceFirst("/sp=", "");
            } else if (item.startsWith("/n=")) {
                // Phone number for terminal.
                media = new GXTerminal();
                number = item.replaceFirst("/n=", "");
            } else if (item.startsWith("/b=")) {
                // Baud rate
                startBaudRate = Integer.parseInt(item.replaceFirst("/b=", ""));
            } else if (item.startsWith("/t")) {
                // Are messages traced.
                trace = true;
            } else if (item.startsWith("/s=")) {
                // Start
                String tmp = item.replaceFirst("/s=", "");
                iec = !tmp.toLowerCase().equals("dlms");
            } else if (item.startsWith("/a=")) {
                // Authentication
                auth = Authentication.valueOf(
                        it.trim().replaceFirst("/a=", "").toUpperCase());
            } else if (item.startsWith("/pw=")) {
                // Password
                pw = it.trim().replaceFirst("/pw=", "");
            } else {
                ShowHelp();
                return null;
            }
        }
        if (id.isEmpty() || port.isEmpty()
                || (media instanceof gurux.net.GXNet && host.isEmpty())) {
            ShowHelp();
            return null;
        }
        ////////////////////////////////////////
        // Initialize connection settings.
        if (media instanceof GXSerial) {
            GXSerial serial = (GXSerial) media;
            serial.setPortName(port);
            if (iec) {
                serial.setBaudRate(BaudRate.BAUD_RATE_300);
                serial.setDataBits(7);
                serial.setParity(Parity.EVEN);
                serial.setStopBits(StopBits.ONE);
            } else {
                serial.setBaudRate(BaudRate.forValue(startBaudRate));
                serial.setDataBits(8);
                serial.setParity(Parity.NONE);
                serial.setStopBits(StopBits.ONE);
                //serial.setHandshake(Handshake.NONE);
            }
        } else if (media instanceof GXNet) {
            GXNet net = (GXNet) media;
            net.setPort(Integer.parseInt(port));
            net.setHostName(host);
            net.setProtocol(NetworkType.TCP);
        } else if (media instanceof GXTerminal) {
            GXTerminal terminal = (GXTerminal) media;
            terminal.setPortName(port);
            terminal.setBaudRate(startBaudRate);
            terminal.setDataBits(8);
            terminal.setParity(gurux.io.Parity.NONE);
            terminal.setStopBits(gurux.io.StopBits.ONE);
            terminal.setPhoneNumber(number);
        } else {
            throw new Exception("Unknown media type.");
        }
        GXDLMSClient dlms = new GXDLMSClient();
        GXManufacturerCollection items = new GXManufacturerCollection();
        GXManufacturerCollection.readManufacturerSettings(items, path);
        GXManufacturer man = items.findByIdentification(id);
        if (man == null) {
            throw new RuntimeException("Invalid manufacturer.");
        }
        dlms.setObisCodes(man.getObisCodes());
        com = new GXCommunicate(5000, dlms, man, iec, auth, pw, media);
        if(iPrintlnMessages==64) System.out.println("COM("+man.getName()+")");
        com.Trace = trace;
        com.iDebugModelDLMS_local = iDebugModelDLMS; // debug mode is set also for GXCommunicate module
        return com;
    }
}
