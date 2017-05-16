/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import gurux.GXCommunicate;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;

import gurux.common.IGXMedia;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.enums.Authentication;
import gurux.dlms.manufacturersettings.GXManufacturer;
import gurux.dlms.manufacturersettings.GXManufacturerCollection;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSObjectCollection;
import gurux.io.Parity;
import gurux.io.StopBits;
import gurux.net.GXNet;
import gurux.net.enums.NetworkType;
import gurux.serial.GXSerial;
import gurux.terminal.GXTerminal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;

import java.util.Properties;
import java.util.Vector;
import util.FileUtil;
import util.GuruxUtil;
import util.PropUtil;

/**
 *
 * @author cristi
 */
public class MeterDLMSClient extends Module {

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

        sProf1File = PropUtil.GetString(pAttributes, "sProf1File", "");

        iBlockRead = PropUtil.GetInt(pAttributes, "iBlockRead", 0);
        iProfilesRead = PropUtil.GetInt(pAttributes, "iProfilesRead", 0);
    }
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

    String sProf1File = "";

    int iBlockRead = 0;
    int iProfilesRead = 0;

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

    public void QueryMeter() {
        // TODO code application logic here

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
                if (com == null) {
                    return;
                }
                com.initializeConnection();
                try {
                   // com.readAndPrintAllObjects(logFile);
                    
                    
                    objectDate = objectDate.load(sObjDateFile);
                    com.readClockAttr(objectDate.get(0));
                    //com.readScalerAndUnits(objectDate, logFile);
                    objects1 = objects1.load(sObj1File);
                    com.readScalerAndUnits(objects1, logFile);
                    
                   // GuruxUtil.GXDLMSObjectSaveWithVals(objects1, "tstObjSave.txt");
                    
                    objects2 = objects2.load(sObj2File);
                    com.readScalerAndUnits(objects2, logFile);
                    objects3 = objects3.load(sObj3File);
                    com.readScalerAndUnits(objects3, logFile);

                    if (iProfilesRead == 1) {
                        vProfilesDetail.clear();

                        profiles1 = profiles1.load(sProf1File);
                        com.readProfileGenericColumnsString(profiles1, logFile);

                        com.readProfileGenericsAttr(profiles1, logFile);
                        iNoOfProfiles = profiles1.size();

                        for (iCrtProfileNo = 1; iCrtProfileNo < iNoOfProfiles + 1; iCrtProfileNo++) {
                            htProfileDetail = new Hashtable<>();
                            it = profiles1.get(iCrtProfileNo - 1);
                            s = com.getProfileGenericColumnsString(it);
                            htProfileDetail.put("ColumnsString", s);
                            FileUtil.SaveToFile(sProfilePath + "/" + it.getLogicalName().replace('.', '-') + ".txt",
                                    s + "\r\n");
                            com.updateProfileGenericsDetails(htProfileDetail, it, 8, logFile);
                            vProfilesDetail.add(htProfileDetail);
                        }
                        iCrtProfileNo = 1;

                        // com.readProfileGenericsLast5Rows(profiles1, logFile);
                    }
                } catch (Exception exc) {
                }

            }
            //Read Date
            if (objectDate != null) {
                s = com.readDate(objectDate.get(0), pos);
                if (s.length() > 1) {
                    sOBISPath = sPrefix + objectDate.get(0).getLogicalName().replace('.', '-');
                    pDataSet.put(sOBISPath + "/" + "-2", s);
                }
                GuruxUtil.GXDLMSObjectSaveWithVals(objectDate, "tstObjDSave.txt");
            }

            if (iBlockRead == 1) {
                //com.readAllObjects(logFile);
                if (objects1 != null) {

                    com.readRegisters(objects1, logFile);

                    WriteVals(objects1);
                    GuruxUtil.GXDLMSObjectSaveWithVals(objects1, "tstObj1Save.txt");
                }
                if (objects2 != null) {

                    com.readRegisters(objects2, logFile);

                    WriteVals(objects2);
                    GuruxUtil.GXDLMSObjectSaveWithVals(objects2, "tstObj2Save.txt");
                }
                if (objects3 != null) {

                    com.readRegisters(objects3, logFile);

                    WriteVals(objects3);
                    GuruxUtil.GXDLMSObjectSaveWithVals(objects3, "tstObj3Save.txt");
                }
            } else {
                if (objects1 != null) {
                    com.readValues2(objects1, logFile);
                    WriteVals(objects1);
                }
                if (objects2 != null) {
                    com.readValues2(objects2, logFile);
                    WriteVals(objects2);
                }
                if (objects3 != null) {
                    com.readValues2(objects3, logFile);
                    WriteVals(objects3);
                }
            }
            if (iProfilesRead == 1) {

                if (++iCrtProfileNo > iNoOfProfiles) {
                    iCrtProfileNo = 1;
                }
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
            System.out.println(e.toString());
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
    }
    private final SimpleDateFormat sdfDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    String sDate = "";
    String sOBISPath = "";

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
            try {
                pDataSet.put(sOBISPath + "/" + "-2", it.getValues()[1].toString());
            } catch (Exception e) {
                System.out.println(e.getMessage());
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
        System.out.println(sb.toString());
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
            System.out.println(ex.toString());
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
                serial.setBaudRate(300);
                serial.setDataBits(7);
                serial.setParity(Parity.EVEN);
                serial.setStopBits(StopBits.ONE);
            } else {
                serial.setBaudRate(startBaudRate);
                serial.setDataBits(8);
                serial.setParity(Parity.NONE);
                serial.setStopBits(StopBits.ONE);
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
        com.Trace = trace;
        return com;
    }
}
