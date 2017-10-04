/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.util.ArrayList;
import java.util.Date;
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
    
    public void Initialize() {
        PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
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
