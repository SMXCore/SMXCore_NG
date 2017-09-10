/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Logger;

/**
 *
 * @author cristi
 */
public class Module extends Thread{

    volatile Properties pAttributes = new Properties(); // The attributes of the current module, loaded from a file.
    String sAttributesFile=""; // The file it is loaded from
    String sName = ""; // The name of the instance of the module
    ModulesManager mmManager = null; // The module manager
    Logger logger = null; // The logger used
    volatile boolean bReinitialize = false;

    public void setAttributes(Properties pSetAttributes) {
        pAttributes = pSetAttributes;
    }

    public void Initialize() {
        
    }

    public void Start() {

    }

    public void QueueTime() {

    }
    
//    void logSevere(String message) {
//        DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS : ");
//        Date date = new Date();
//        logger.severe(df.format(date) + message);
//    }
//    void logWarning(String message) {
//        DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS : ");
//        Date date = new Date();
//        logger.warning(df.format(date) + message);
//    }
//    void logInfo(String message) {
//        DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS : ");
//        Date date = new Date();
//        logger.info(df.format(date) + message);
//    }
//    void logConfig(String message) {
//        DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS : ");
//        Date date = new Date();
//        logger.config(df.format(date) + message);
//    }
//    void logDebug(String message) {
//        DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS : ");
//        Date date = new Date();
//        logger.fine(df.format(date) + message);
//    }
//    void logTrace(String message) {
//        DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS : ");
//        Date date = new Date();
//        logger.finer(df.format(date) + message);
//    }
}
