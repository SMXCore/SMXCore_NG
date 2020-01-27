/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Properties;



/**
 *
 * @author Vlad
 */
public class SmartProperties extends Properties {
    public class Metadata {
        public Date timestamp;
        public String type;
    }
    Hashtable<String, Metadata> meta;
    public interface Listener {
        public void wakeup(Object key, Object value);
    }
    ArrayList<Listener> lstnrs;
    
    public void listen(Listener l) {
        lstnrs.add(l);
    }
    
    @Override
    public Object put(Object key, Object value) {
        Metadata m = meta.get(key);
        if(m == null) {
            m = new Metadata();
        }
        m.timestamp = new Date();
        meta.put((String) key, m);
        Object result = super.put(key, value);
        for(Listener l : lstnrs) {
            l.wakeup(key, value);
        }
        return result;
    }
    
    public Object putNoLstnr(Object key, Object value) {
        Metadata m = meta.get(key);
        if(m == null) {
            m = new Metadata();
        }
        m.timestamp = new Date();
        meta.put((String) key, m);
        Object result = super.put(key, value);
        return result;
    }
    
    public Metadata getmeta(String key) {
        return meta.get(key);
    }
            
    public SmartProperties() {
        super();
        this.meta = new Hashtable();
        this.lstnrs = new ArrayList();
    }
    public SmartProperties(Properties defaults) {
        super(defaults);
        this.meta = new Hashtable();
    }
}
