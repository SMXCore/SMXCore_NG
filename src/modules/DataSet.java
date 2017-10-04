/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import util.PropUtil;

/**
 *
 * @author cristi
 */
public class DataSet extends Module {

    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(sName);
        PropUtil.LoadFromFile(pDataSet, sAttributesFile);
        sSYSPrefix = PropUtil.GetString(pAttributes, "sSYSPrefix", "");
    }
    Properties pDataSet = null;
    String sSYSPrefix = "";
    Thread tLoop = null;

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
    SimpleDateFormat sdfUTC = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

    public void Loop() {
        sdfUTC.setTimeZone(TimeZone.getTimeZone("UTC"));
        UpdateFirst();
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

                dt = new Date();
                pDataSet.put(sSYSPrefix + "SysDateTime", sdfDT.format(dt));
                pDataSet.put(sSYSPrefix + "SysDateTimeUTC", sdfUTC.format(dt));
                pDataSet.put(sSYSPrefix + "SysDate", sdfD.format(dt));
                pDataSet.put(sSYSPrefix + "SysTime", sdfT.format(dt));
                
                pDataSet.put(sSYSPrefix + "ProcPID", ManagementFactory.getRuntimeMXBean().getName());

                pDataSet.put(sSYSPrefix + "SysCpuLoad", Double.toString(osBean.getSystemCpuLoad() * 100));
                pDataSet.put(sSYSPrefix + "SysMemoryLoad", Double.toString(
                        100 - (osBean.getFreePhysicalMemorySize() * 100.0) / osBean.getTotalPhysicalMemorySize()));


            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }

    public void UpdateFirst() {
        try {

            dt = new Date();
            pDataSet.put(sSYSPrefix + "SysDateTime", sdfDT.format(dt));
            pDataSet.put(sSYSPrefix + "SysDateTimeUTC", sdfUTC.format(dt));
            pDataSet.put(sSYSPrefix + "SysDate", sdfD.format(dt));
            pDataSet.put(sSYSPrefix + "SysTime", sdfT.format(dt));

            pDataSet.put(sSYSPrefix + "SysCpuLoad", Double.toString(osBean.getSystemCpuLoad() * 100));
            pDataSet.put(sSYSPrefix + "SysMemoryLoad", Double.toString(
                    100 - (osBean.getFreePhysicalMemorySize() * 100.0) / osBean.getTotalPhysicalMemorySize()));

            ip = InetAddress.getLocalHost();

            NetworkInterface network = NetworkInterface.getByIndex(2);
            ip = network.getInterfaceAddresses().get(0).getAddress();
            pDataSet.put(sSYSPrefix + "SysIP", ip.getHostAddress());
            pDataSet.put(sSYSPrefix + "SysHostName", ip.getHostName());

            byte[] mac = network.getHardwareAddress();
            sSN = "";
            for (int i = 0; i < mac.length; i++) {
                sSN += Integer.toHexString(mac[i] & 0XFF);
            }
            pDataSet.put(sSYSPrefix + "SysSN", sSN);

        } catch (Exception e) {
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }
    String sSN = "";
    InetAddress ip;
    OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    SimpleDateFormat sdfDT = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    SimpleDateFormat sdfD = new SimpleDateFormat("MM/dd/yyyy");
    SimpleDateFormat sdfT = new SimpleDateFormat("HH:mm:ss");
    public int Pause = 0;
    public int memPause = 0;
    public int bStop = 0;
    Date dt;

    public int Debug = 0;

    public long lPeriod = 1000;
    public long lIniSysTimeMs = 0;
    public long lMemSysTimeMs = 0;
    public long ldt = 0;
    public double ddt = 0.0;
    public long lDelay = 0;

}
