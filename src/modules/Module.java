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

    Properties pAttributes = new Properties();
    String sAttributesFile="";
    String sName = "";
    ModulesManager mmManager=null;

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
