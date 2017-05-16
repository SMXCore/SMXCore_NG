/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import util.PropUtil;

/**
 *
 * @author cristi
 */
public class ModulesManager {

    public void LoadModules() {
        int iNoOfModules = 0;
        String sModuleName = "";
        String sClassName = "";
        String sAttributePrefix = "";
        Module mModule = null;
        Properties pModuleAttributes = new Properties();
        Hashtable<String, Class> htClasses = new Hashtable<String, Class>();
        Class cClass = null;
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
                    PropUtil.LoadFromFile(mModule.pAttributes,  mModule.sAttributesFile);
                    if (mModule.pAttributes == null) {
                        continue;
                    }
                    mModule.sName=sModuleName;
                    mModule.mmManager=this;
                    mModule.Initialize();
                    htModulesData.put(sModuleName, mModule);
                    vModulesQueue.add(mModule);
                    
                } catch (Exception ex) {

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
            pTmp = new Properties();
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

}
