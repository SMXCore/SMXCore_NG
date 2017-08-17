/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.util.Properties;

/**
 *
 * @author cristi
 */
public class Module extends Thread{

    Properties pAttributes = new Properties(); // The attributes of the current module, loaded from a file.
    String sAttributesFile=""; // The file it is loaded from
    String sName = ""; // The name of the instance of the module
    ModulesManager mmManager=null; // The module manager

    public void setAttributes(Properties pSetAttributes) {
        pAttributes = pSetAttributes;
    }

    public void Initialize() {
        
    }

    public void Start() {

    }

    public void QueueTime() {

    }
}
