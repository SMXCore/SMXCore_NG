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

    protected volatile Properties pAttributes = new Properties(); // The attributes of the current module, loaded from a file.
    protected String sAttributesFile=""; // The file it is loaded from
    protected String sName = ""; // The name of the instance of the module
    protected ModulesManager mmManager = null; // The module manager
    protected Logger logger = null; // The logger used
    protected volatile boolean bReinitialize = false;

    public void setAttributes(Properties pSetAttributes) {
        pAttributes = pSetAttributes;
    }
    
    public void receiveEvent(String key, String value, Date date) {
        
    }

    public void Initialize() {
        
    }

    public void Start() {

    }

    public void QueueTime() {

    }
    
}
