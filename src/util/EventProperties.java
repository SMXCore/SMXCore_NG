/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import java.util.Date;
import java.util.Properties;
import modules.DataSetEventHandler;

/**
 *
 * @author vlad
 */
public class EventProperties extends Properties {
    DataSetEventHandler dseh;
    @Override
    public synchronized Object put(Object key, Object value) {
        if(dseh != null) {
            dseh.ThrowEvent((String) key, (String) value, new Date());
        }
        return super.put(key, value);
    }
    
    public void setDseh(DataSetEventHandler ds) {
        dseh = ds;
    }
}
