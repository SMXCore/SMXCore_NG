/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import util.PropUtil;

/**
 *
 * @author Stela
 */
public class DataSetEventHandler extends Module {
    List<Module> listeners = new ArrayList();
    
    Properties pAssociation = new Properties();
    Enumeration eKeys;
    
    public void Initialize() {
        PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
        eKeys = pAssociation.keys();
        while (eKeys.hasMoreElements()) {
            try {
                String e = (String) eKeys.nextElement();
                String val = (String) pAssociation.getProperty(e, "");
                Module m = (Module) mmManager.getModuleDebug(val);
                listeners.add(m);
            }  catch (Exception ex) {
                logger.warning(ex.getMessage());
            }
        }
    }
    
    public void ThrowEvent(String key, String value, Date date) {
        for(Module l: listeners) {
            l.receiveEvent(key, value, date);
        }
    }
    
    public void HookListener(Module module) {
        listeners.add(module);
    }
}
