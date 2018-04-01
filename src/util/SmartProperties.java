/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

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
    
    @Override
    public Object put(Object key, Object value) {
        Metadata m = meta.get(key);
        if(m == null) {
            m = new Metadata();
        }
        m.timestamp = new Date();
        meta.put((String) key, m);
        return super.put(key, value);
    }
    
    public Metadata getmeta(String key) {
        return meta.get(key);
    }
            
    public SmartProperties() {
        super();
        this.meta = new Hashtable();
    }
    public SmartProperties(Properties defaults) {
        super(defaults);
        this.meta = new Hashtable();
    }
}
