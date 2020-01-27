/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import util.PropUtil;
import smxcore.LogFormatter;
import java.util.logging.Logger;
import util.SmartProperties;
//import sun.util.logging.PlatformLogger;

/**
 *
 * @author cristi
 */
public class ModulesManager {
        
    static private FileHandler logFile;
    static private Formatter logFormatter;

    public void LoadModules() {
        int iNoOfModules = 0;
        String sModuleName = "";
        String sClassName = "";
        String sAttributePrefix = "";
        Module mModule = null;
        Properties pModuleAttributes = new Properties();
        Hashtable<String, Class> htClasses = new Hashtable<String, Class>();
        Class cClass = null;
        Logger logger = Logger.getLogger("modules");
        
        if(PropUtil.GetInt(pAttributes, "enableConsoleLog", 1) == 0) {
            Logger rootLogger = Logger.getLogger("");
            Handler[] handlers = rootLogger.getHandlers();
            if (handlers[0] instanceof ConsoleHandler) {
                rootLogger.removeHandler(handlers[0]);
            }
        }
        
        // SEVERE WARNING, INFO, CONFIG, FINE, FINER, FINEST, OFF , ALL 
        // https://docs.oracle.com/javase/7/docs/api/java/util/logging/Level.html
        logger.setLevel(Level.parse(PropUtil.GetString(pAttributes, "logLevel", "CONFIG")));
        if(PropUtil.GetInt(pAttributes, "enableFileLog", 0) != 0) {
            try {
                logFile = new FileHandler(PropUtil.GetString(pAttributes, "logFile", "Log/Log.txt"), PropUtil.GetInt(pAttributes, "maxBytes", 10485760), PropUtil.GetInt(pAttributes, "maxFiles", 1), true);
                logFormatter = new LogFormatter();
                logFile.setFormatter(logFormatter);
                logger.addHandler(logFile);
            } catch(Exception ex) {
                System.out.println(ex);
            }
        }
        
        try {
            iNoOfModules = PropUtil.GetInt(pAttributes, "iNoOfModules", 0);
            for (int i = 0; i <= iNoOfModules; i++) {
                try {
                    sAttributePrefix = "M" + Integer.toString(i+1) + "-";
                    sModuleName = pAttributes.getProperty(sAttributePrefix + "Name", sAttributePrefix);
                    sClassName = pAttributes.getProperty(sAttributePrefix + "ClassName", "");
                    if (sClassName.length() < 2) {
                        continue;
                    }
                    cClass = htClasses.get(sClassName);
                    if (cClass == null) {
                        cClass = Class.forName(sClassName);
                    }
                    mModule = (Module) cClass.newInstance();
                    mModule.pAttributes.clear();
                    mModule.sAttributesFile=pAttributes.getProperty(sAttributePrefix + "AttributesFile", "");
//                    PropUtil.LoadFromFile(mModule.pAttributes,  mModule.sAttributesFile);
                    mModule.logger = Logger.getLogger(sClassName + "." + sModuleName);
                    mModule.LoadConfig();
                    if (mModule.pAttributes == null) {
                        System.out.println(sClassName + "." + sModuleName + " FAILED to load");
                        continue;
                    }
                    mModule.sName=sModuleName;
                    mModule.mmManager=this;
                    if(!pAttributes.getProperty(sAttributePrefix + "LogLevel", "").equals("")) {
                        mModule.logger.setLevel(Level.parse(pAttributes.getProperty(sAttributePrefix + "LogLevel", "WARNING")));
                    }
                    mModule.Initialize();
                    htModulesData.put(sModuleName, mModule);
                    vModulesQueue.add(mModule);
                    
                } catch (Exception ex) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    ex.printStackTrace(pw);
                    String sStackTrace = sw.toString();
                    logger.severe(ex.getMessage() + "\n Stacktrace: " + sStackTrace);
                }
            }

            for (int i = 0; i < iNoOfModules; i++) {
                try {
                    vModulesQueue.elementAt(i).Start();
                } catch (Exception e) {

                }
            }

        } catch (Exception e) {

        }

    }

    public void setAttributes(Properties pSetAttributes) {
        pAttributes = pSetAttributes;
    }

    Hashtable<String, Module> htModulesData = new Hashtable<String, Module>();
    Vector<Module> vModulesQueue = new Vector<Module>();
    Properties pAttributes = null;
    Hashtable<String, Properties> htSharedData = new Hashtable<String, Properties>();
    Hashtable<String, Object> htSharedObjects = new Hashtable<String, Object>();

    public Properties getSharedData(String sDataName) {
        Properties pTmp = htSharedData.get(sDataName);
        if (pTmp == null) {
            pTmp = new SmartProperties();
            htSharedData.put(sDataName, pTmp);
        }
        return pTmp;
    }

    public void setSharedObject(String sObjectName, Object oObject) {
        htSharedObjects.put(sObjectName, oObject);
    }

    public Object getSharedObject(String sObjectName) {
        return htSharedObjects.get(sObjectName);
    }
    
    public Module getModuleDebug(String sModuleName) {
        return htModulesData.get(sModuleName);
    }

}
