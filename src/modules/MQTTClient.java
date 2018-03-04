/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.io.File;
import java.text.SimpleDateFormat;
import java.io.FileInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import util.PropUtil;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import util.SmartProperties;

/**
 *
 * @author cristi, vlad
 */
public class MQTTClient extends Module {

    private class TimestampCfg {
        boolean only_once;
        String ts_suffix;
        String val_suffix;
        String ts_type;
        String timezone;
    }
    
    class ReplaceDescriptor {
        Pattern pattern;
        String replacement;
    }
    
    class RescaleDescriptor {
        Pattern pattern;
        double multiplier;
        double added;
    }
    
    class AddItemDescriptor {
        String item;
        String value;
    }
    
    class UnitRescaleDescriptor {
        Pattern unit_pattern;
        String value_pattern;
        String new_unit;
    }
    
    private class Association {
        String mqttTopic;
        String internalName;
        boolean isClassic; // Backwards compatibility mode
        boolean readJson;
        boolean hasTsCfg;
        TimestampCfg tsCfg;
        ArrayList<ReplaceDescriptor> replace_list;
        ArrayList<RescaleDescriptor> rescale_list;
        ArrayList<AddItemDescriptor> add_item_list;
        ArrayList<String> list_apply_order;
        Date begin, end;
    }
    
    @Override
    public void Initialize() {
        sBroker = PropUtil.GetString(pAttributes, "sBroker", "tcp://localhost:18159");
        logger.config("MQTT Broker is at " + sBroker);
        sUserName = PropUtil.GetString(pAttributes, "sUserName", "");
        sUserPass = PropUtil.GetString(pAttributes, "sUserPass", "");
        sClientID = PropUtil.GetString(pAttributes, "sClientID", "");
        logger.config("Connecting to MQTT Broker with client ID" + sClientID);

       // PropUtil.LoadFromFile(pPubAssociation, PropUtil.GetString(pAttributes, "pPubAssociation", ""));
       // PropUtil.LoadFromFile(pSubAssociation, PropUtil.GetString(pAttributes, "pSubAssociation", ""));

        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        logger.config("Using dataset" + pDataSet);

        sPubPrefix = PropUtil.GetString(pAttributes, "sPubPrefix", "SMX/");
        sSubPrefix = PropUtil.GetString(pAttributes, "sSubPrefix", "SMX/");
        sIntPrefix = PropUtil.GetString(pAttributes, "sIntPrefix", "SMX/");
        logger.config("Publication prefix: \"" + sPubPrefix + "\"");
        logger.config("Subscription prefix: \"" + sSubPrefix + "\"");
        logger.config("Internal prefix: \"" + sIntPrefix + "\"");

//        sSubTopics = PropUtil.GetString(pAttributes, "sSubTopics", "");
//        ssSubTopics = sSubTopics.split(",");

        iPubQos = PropUtil.GetInt(pAttributes, "iReadQos", 1);
        iSubQos = PropUtil.GetInt(pAttributes, "iSubQos", 1);

        lPeriod = PropUtil.GetLong(pAttributes, "lPeriod", 5000);

        //insert in database metadata related to the Modbus module 
        //String sTSDate;
        //sTSDate = sdf.format(new Date());
        //pDataSet.put("Module/FileStorage/"+sName+"/StartDateTime", sTSDate); // DateTime
        String s1;
        s1 = String.valueOf(lPeriod); pDataSet.put("Module/MQTTClient/"+sName+"/lPeriod", s1); // 
    }
    
    public void LoadConfig() {
        if(sAttributesFile.endsWith("json")) {
            LoadJsonConfig();
        } else {
            LoadTxtConfig();
        }
    }
    
    void LoadJsonConfig() {
        JsonObject jso = read_jso_from_file(sAttributesFile);
        JsonObject prefix = jso.getJsonObject("prefix");
        JsonObject connection = jso.getJsonObject("connection");
        pAttributes.put("pDataSet", jso.getString("dataSet", ""));
        pAttributes.put("lPeriod", jso.getInt("period", 5000));
        if(connection != null) {
            pAttributes.put("sBroker", connection.getString("broker", "tcp://localhost:18159"));
            JsonObject credentials = connection.getJsonObject("credentials");
            if(credentials != null) {
                pAttributes.put("sUserName", credentials.getString("username", ""));
                pAttributes.put("sUserPass", credentials.getString("password", ""));
                pAttributes.put("sClientID", credentials.getString("clientID", ""));
            }
            JsonObject qos = connection.getJsonObject("qos");
            if(qos != null) {
                pAttributes.put("iPubQos", qos.getString("publish", ""));
                pAttributes.put("iSubQos", qos.getString("subscribe", ""));
            }
        }
        if(prefix != null) {
            pAttributes.put("sPubPrefix", prefix.getString("publish", ""));
            pAttributes.put("sSubPrefix", prefix.getString("subscribe", ""));
            pAttributes.put("sIntPrefix", prefix.getString("internal", ""));
        }
        SubAssoc = new HashMap();
        if(jso.get("pubAssociation").getValueType() == JsonValue.ValueType.STRING) {
            PubAssoc = loadAssoc(jso.getString("pubAssociation", ""), true);
        } else {
            PubAssoc = loadAssocJson(jso.getJsonObject("pubAssociation"), true);
        }
        ArrayList<Association> SubAssocs;
        if(jso.get("subAssociation").getValueType() == JsonValue.ValueType.STRING) {
            SubAssocs = loadAssoc(jso.getString("subAssociation", ""), false);
        } else {
            SubAssocs = loadAssocJson(jso.getJsonObject("subAssociation"), false);
        }
        for(Association a: SubAssocs) {
            SubAssoc.put(a.mqttTopic, a);
        }
    }
    
    void LoadTxtConfig() {
        PropUtil.LoadFromFile(pAttributes,  sAttributesFile);
        SubAssoc = new HashMap();
        
        logger.config("Loading subscribe associations");
        ArrayList<Association> SubAssocs = loadAssoc(PropUtil.GetString(pAttributes, "pSubAssociation", ""), false);
        for(Association a: SubAssocs) {
            SubAssoc.put(a.mqttTopic, a);
        }
        logger.config("Loading public associations");
        PubAssoc = loadAssoc(PropUtil.GetString(pAttributes, "pPubAssociation", ""), true);
    }
    
    ArrayList<Association> loadAssoc(String file, boolean isPublish) {
        if(file.endsWith("json")) {
            logger.config("MQTT Association config file «" + file + "» is a json");
            return loadAssocJson(read_jso_from_file(file), isPublish);
        } else {
            logger.config("MQTT Association config file «" + file + "» is plain text or XML");
            return loadAssocTxtXml(file, isPublish);
        }
    }
    
    ArrayList<Association> loadAssocTxtXml(String file, boolean isPublish) {
        Properties prop = new Properties();
        PropUtil.LoadFromFile(prop, file);
        eKeys = prop.keys();
        ArrayList<Association> list = new ArrayList();
        while (eKeys.hasMoreElements()) {
            try {
                String e = (String) eKeys.nextElement();
                Association assoc = new Association();
                assoc.isClassic = true;
                assoc.readJson = false;
                assoc.hasTsCfg = true;
                assoc.replace_list = new ArrayList();
                assoc.rescale_list = new ArrayList();
                if(isPublish) {
                    assoc.internalName = e;
                    assoc.mqttTopic = (String) prop.getProperty(e, "");
                } else {
                    assoc.internalName = (String) prop.getProperty(e, "");
                    assoc.mqttTopic = e;
                }
                logger.config("Added association between internal name " + assoc.internalName + " and mqtt topic " + assoc.mqttTopic + " running with classic mode: " + assoc.isClassic);
                list.add(assoc);
            }  catch (Exception ex) {
//                if (Debug == 1) {
                logger.warning(ex.getMessage());
                    //System.out.println(ex.getMessage());
//                }
            }
        }
        return list;
    }
    
    JsonObject read_jso_from_file(String file) {
        JsonObject jso = null;
        try {
            String file_content = new Scanner(new File(file)).useDelimiter("\\Z").next();
            // comment processing

            String comment_pattern = "(^|\\n)(#|//)[^\\n]*(\\n|$)";
            int max_iterations = 1000;
            int ijk = 0;
            while(true) {
                ijk++;
                String new_file_content = file_content.replaceAll(comment_pattern, "\n");
                if(new_file_content.equals(file_content) || ijk == max_iterations) {
                    break;
                } else file_content = new_file_content;
            }

            JsonReader jsr = Json.createReader(new StringReader(file_content));
            jso = jsr.readObject();
        } catch(Exception ex) {
            logger.warning(ex.getMessage());
        }
        return jso;
    }
    
    ArrayList<Association> loadAssocJson(JsonObject jso, boolean isPublish) {
        ArrayList<Association> list = new ArrayList();
        try {
            Iterator it = jso.entrySet().iterator();
            while(it.hasNext()) {
                try {
                    Map.Entry crt = (Map.Entry) it.next();
                    String name = (String) crt.getKey();
                    Association assoc = new Association();
                    JsonValue value = (JsonValue) crt.getValue();
                    assoc.replace_list = new ArrayList();
                    assoc.rescale_list = new ArrayList();
                    assoc.add_item_list = new ArrayList();
                    assoc.list_apply_order = new ArrayList();
                    if(value.getValueType() == JsonValue.ValueType.STRING) {
                        JsonString ss = (JsonString) value;
                        assoc.isClassic = true;
                        assoc.readJson = false;
                        assoc.hasTsCfg = false;
                        if(!isPublish) {
                            assoc.internalName = ss.getString();
                            assoc.mqttTopic = name;
                        } else {
                            assoc.internalName = name;
                            assoc.mqttTopic = ss.getString();
                        }
                    } else if(value.getValueType() == JsonValue.ValueType.OBJECT) {
                        JsonObject ass = (JsonObject) value;
                        assoc.isClassic = ass.getBoolean("isClassic", false);
                        assoc.readJson = ass.getBoolean("TreatAsJsonObject", ass.getBoolean("readJson", false));
                        assoc.internalName = ass.getString("internalName", ass.getString("regexSelection", name));
                        assoc.mqttTopic = ass.getString("mqttTopic");
                        
                        String begindate = ass.getString("StartTime", "");
                        String enddate = ass.getString("EndTime", "");
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        if(!begindate.equals("")) {
                            try {
                                assoc.begin = formatter.parse(begindate);
                            } catch(Exception ex) {
                                logger.warning(ex.getMessage());
                            }
                        }
                        if(!enddate.equals("")) {
                            try {
                                assoc.end = formatter.parse(enddate);
                            } catch(Exception ex) {
                                logger.warning(ex.getMessage());
                            }
                        }
                        
                        JsonObject ts = ass.getJsonObject("timestamp");
                        if(ts != null) {
                            assoc.hasTsCfg = true;
                            assoc.tsCfg = new TimestampCfg();
                            assoc.tsCfg.only_once = ts.getBoolean("only-once", false);
                            assoc.tsCfg.ts_suffix = ts.getString("timestamp-suffix", "/timestamp");
                            assoc.tsCfg.val_suffix = ts.getString("value-suffix", "/value");
                            assoc.tsCfg.ts_type = ts.getString("timestamp-type", "US-style SMXCore Timestamp"); // mm/dd/yyyy hh:mm:ss
                            assoc.tsCfg.timezone = ts.getString("timezone", "Local");
                            //tipuri:
                            // US-style SMXCore Timestamp: mm/dd/yyyy hh:mm:ss
                            // UNIX: sssssssss (since 1970)
                            // ISO 8601: yyyy-mm-ddThh:mm:ssZ
                        } else assoc.hasTsCfg = false;
                        
                        Iterator it2 = ass.entrySet().iterator();
                        while(it2.hasNext()) {
                            try {
                                Map.Entry crt2 = (Map.Entry) it2.next();
                                String name2 = (String) crt2.getKey();
                                JsonValue value2 = (JsonValue) crt2.getValue();
                                if(name2.equals("replace_list") || name2.equals("renameRules")) {
                                    JsonArray replace_list = value2.asJsonArray();
                                    for(int i = 0; i < replace_list.size(); i++) {
                                        JsonArray descriptor = replace_list.getJsonArray(i);
                                        ReplaceDescriptor rd = new ReplaceDescriptor();
                                        String pat = descriptor.getString(0);
                                        rd.pattern = Pattern.compile(pat);
                                        rd.replacement = descriptor.getString(1);
                                        assoc.replace_list.add(rd);
                                    }
                                    assoc.list_apply_order.add("replace");
                                } else if(name2.equals("rescale_list") || name2.equals("rescaleRules")) {
                                    JsonArray replace_list = value2.asJsonArray();
                                    for(int i = 0; i < replace_list.size(); i++) {
                                        JsonArray descriptor = replace_list.getJsonArray(i);
                                        RescaleDescriptor rd = new RescaleDescriptor();
                                        String pat = descriptor.getString(0);
                                        rd.pattern = Pattern.compile(pat);
                                        rd.multiplier = descriptor.getJsonNumber(1).doubleValue();
                                        if(descriptor.size() > 2) {
                                            rd.added = descriptor.getJsonNumber(2).doubleValue();
                                        } else rd.added = 0;
                                        assoc.rescale_list.add(rd);
                                    }
                                    assoc.list_apply_order.add("rescale");
                                } else if(name2.equals("additem_list") || name2.equals("addItemRules")) {
                                    JsonArray replace_list = value2.asJsonArray();
                                    for(int i = 0; i < replace_list.size(); i++) {
                                        JsonArray descriptor = replace_list.getJsonArray(i);
                                        AddItemDescriptor rd = new AddItemDescriptor();
                                        rd.item = descriptor.getString(0);
                                        rd.value = descriptor.getString(1);
                                        assoc.add_item_list.add(rd);
                                    }
                                    assoc.list_apply_order.add("additem");
                                }
                            } catch(Exception ex) {
                                logger.warning(ex.getMessage());
                            }
                        }
                    }
                    list.add(assoc);
                    logger.config("Added association between internal name " + assoc.internalName + " and mqtt topic " + assoc.mqttTopic + " running with classic mode: " + assoc.isClassic);
                } catch(Exception ex) {
//                    if (Debug == 1) {
//                        System.out.println(ex.getMessage());
//                    }
                    logger.warning(ex.getMessage());
                }
            }
        } catch(Exception ex) {
//            if (Debug == 1) {
//                System.out.println(ex.getMessage());
//            }
            logger.warning(ex.getMessage());
        }
        return list;
    }

//    String sSubTopics = "";
//    String[] ssSubTopics = null;

    Properties pDataSet = null;

    String sPubPrefix = "SMX/";
    String sSubPrefix = "SMX/";
    String sIntPrefix = "SMX/";

    //Properties pPubAssociation = new Properties();
    //Properties pSubAssociation = new Properties();
    
    ArrayList<Association> PubAssoc = new ArrayList();
    HashMap<String, Association> SubAssoc;

    String sKey;
    String sValue;
    Enumeration eKeys;
    String sInternalAttr;
    String sMQTTAttr;

    String greatestCommonPrefix(String a, String b) {
        int minLength = Math.min(a.length(), b.length());
        for(int i = 0; i < minLength; i++) {
            if(a.charAt(i) != b.charAt(i))  {
                return a.substring(0, i);
            }
        }
        return a.substring(0, minLength);
    }
    
    @SuppressWarnings("empty-statement")
    String getNextSubObject(int start, String thing) {
        int i;
        for(i = start; i != thing.length() && thing.charAt(i) != '/'; i++);
        if(i != thing.length()) {
            return thing.substring(start, i + 1);
        } else {
            return thing.substring(start, i);
        }
    }
    
    class ValueNameCouple {
        String name;
        String value;
    }
    
    JsonObject makeObjects(String prefix, ValueNameCouple[] things) {
        JsonObjectBuilder b = Json.createObjectBuilder();
        int last = 0;
        logger.finer("Making an object from prefix " + prefix + " from a string array of " + things.length + " objects");
        for(int i = 1; i <= things.length; i++) {
            if(i == things.length || !getNextSubObject(prefix.length(), things[last].name).equals(getNextSubObject(prefix.length(), things[i].name))) {
                String next = getNextSubObject(prefix.length(), things[last].name);
//                System.out.println(next);
                if(next.charAt(next.length() - 1) == '/') {
//                    System.out.println("!");
                    b.add(next.substring(0, next.length() - 1), makeObjects(prefix + next, Arrays.copyOfRange(things, last, i)));
                } else {
                    logger.finer("Found simple value: " + things[last]);
                    b.add(next, things[last].value);
                }
                last = i;
            }
        }
        return b.build();
    }
    
    void decomposeObject(String prefix, JsonObject obj, Association assoc, List<ValueNameCouple> a) {
        try {
            Iterator it = obj.entrySet().iterator();
            while(it.hasNext()) {
                try {                    
                    Map.Entry crt = (Map.Entry) it.next();
                    String name = (String) crt.getKey();
                    JsonValue value = (JsonValue) crt.getValue();
                    if(value.getValueType() == JsonValue.ValueType.OBJECT) {
                        JsonObject objj = (JsonObject) value;
                        decomposeObject(prefix + '/' + name, objj, assoc, a);
                        logger.finest("Found inner object " + prefix + '/' + name);
                    } else {
                        String ss = "";
                        if(value.getValueType() == JsonValue.ValueType.STRING) {
                            JsonString s = (JsonString) value;
                            ss = s.getString();
                        } else if(value.getValueType() == JsonValue.ValueType.NUMBER) {
                            JsonNumber n = (JsonNumber) value;
                            if(n.isIntegral()) {
                                long nr = n.longValue();
                                ss = String.valueOf(nr);
                            } else {
                                double nr = n.doubleValue();
                                ss = String.valueOf(nr);
                            }
                        } else {
                            ss = value.toString();
                        }
                        String internal_name = prefix + '/' + name;
                        for(int i = 0; i < assoc.replace_list.size(); i++) {
                            Matcher match = assoc.replace_list.get(i).pattern.matcher(internal_name);
                            internal_name = match.replaceAll(assoc.replace_list.get(i).replacement);
                        }
                        ValueNameCouple c = new ValueNameCouple();
                        c.name = internal_name;
                        c.value = ss;
                        a.add(c);
                        logger.finest("Found value for " + internal_name + ": " + ss);
                    }
                    logger.finest("Decomposed object " + prefix);
                } catch(Exception ex) {
                    logger.warning(ex.getMessage());
                }
            }
        } catch(Exception ex) {
            logger.warning(ex.getMessage());
        }
    }
    
    public void MQTTPublishLoop() {
//        String[] test = {"SMX/LD01/v1/value", "SMX/LD01/v1/unit", "SMX/LD01/v2/value", "SMX/LD01/v3/value"};
//        String s = makeObjects("SMX/LD01/", test).toString();
//        System.out.println(s);
        try {
            while (bStop == 0) {
                try {
                    if (lPeriod > 0) {
                        //lIniSysTimeMs = System.currentTimeMillis();
                        lDelay = lPeriod - (System.currentTimeMillis() % lPeriod);
                        Thread.sleep(lDelay);
                    } else {
                        Thread.sleep(1000);
                    }
                    if (lMemSysTimeMs == 0) {
                        lMemSysTimeMs = System.currentTimeMillis();
                        //  continue;
                    }
                    if(bReinitialize) {
                        logger.info("Received reinitialization command");
                        MQTTDisconnect();
                        //pPubAssociation = new Properties();
                        //pSubAssociation = new Properties();
                        Initialize();
                    }
                    
                    if (iConnected == 0) {
                        logger.info("Connecting or reconnecting to the MQTT Broker");
                        MQTTConnect();
                    }
                    if (iConnected == 0) {
                        logger.warning("Failed to connect to the MQTT Broker - skipping loop step");
                        continue;
                    }
                    for(Association e: PubAssoc) {
                        try {
                           // if (iConnected == 0) {
                           //     break;
                           // }
                            sInternalAttr = e.internalName;
                            sMQTTAttr = sPubPrefix + e.mqttTopic;
                            if (sMQTTAttr.length() > sPubPrefix.length()) {
                                String reg = "";
                                if(e.isClassic) {
                                    reg = "^" + sIntPrefix + sInternalAttr + ".*$";
                                } else {
                                    if(reg.startsWith("^")) {
                                        reg = "^" + sIntPrefix + sInternalAttr.substring(1);
                                    } else {
                                        reg = sIntPrefix + sInternalAttr;
                                    }
                                }
                                reg = reg.replaceAll("/", "\\/");
                                logger.fine("Publishing to broker topic " + sMQTTAttr + " with regex rule " + reg);
                                Enumeration eKeys2 = pDataSet.keys();
                                List<ValueNameCouple> a = new ArrayList();
                                Date commondate = new Date(0);
                                while(eKeys2.hasMoreElements()) {
                                    String crt = (String) eKeys2.nextElement();
                                    ValueNameCouple coup = new ValueNameCouple();
                                    if(crt.matches(reg)) {
                                        coup.name = crt;
                                        coup.value = (String) pDataSet.getProperty(coup.name, "");
                                        a.add(coup);
                                        Date crtd = ((SmartProperties) pDataSet).getmeta(crt).timestamp;
                                        if(crtd.after(commondate)) {
                                            commondate = crtd;
                                        }
                                        logger.finer("Matched topic " + sMQTTAttr + " with element " + crt);
                                    }
                                }
                                for(int ijk = 0; ijk < e.list_apply_order.size(); ijk++) {
                                    if(e.list_apply_order.get(ijk).equals("replace")) {
                                        for(int k = 0; k < e.replace_list.size(); k++) {
                                            for(int ik = 0; ik < a.size(); ik++) {
                                                Matcher match = e.replace_list.get(k).pattern.matcher(a.get(ik).name);
                                                a.get(ik).name = match.replaceAll(e.replace_list.get(k).replacement);
                                            }
                                        }
                                    } else if(e.list_apply_order.get(ijk).equals("rescale")) {
                                        for(int k = 0; k < e.rescale_list.size(); k++) {
                                            for(int ik = 0; ik < a.size(); ik++) {
                                                Matcher match = e.rescale_list.get(k).pattern.matcher(a.get(ik).name);
                                                if(match.matches()) {
                                                    double value = Double.parseDouble(a.get(ik).value);
                                                    value *= e.rescale_list.get(k).multiplier;
                                                    value += e.rescale_list.get(k).added;
                                                    a.get(ik).value = String.valueOf(value);
                                                }
                                            }
                                        }
                                    } else if(e.list_apply_order.get(ijk).equals("additem")) {
                                        for(int k = 0; k < e.add_item_list.size(); k++) {
                                            boolean found = false;
                                            String defaultformat = "yyyy-MM-dd HH:mm:ss";
                                            Pattern pattern_custom = Pattern.compile("^!Timestamp(\\{Custom ([^}]*)\\})(\\{([^}]*)\\})?");
                                            Pattern pattern_otherwise = Pattern.compile("^!Timestamp(\\{([^}]*)\\})?(\\{([^}]*)\\})?");
                                            for(int ik = 0; ik < a.size(); ik++) {
                                                if(a.get(ik).name.equals(sIntPrefix + e.add_item_list.get(k).item)) {
                                                    found = true;
                                                    if(e.add_item_list.get(k).value.startsWith("!Timestamp")) {
                                                        Matcher matcher = pattern_custom.matcher(e.add_item_list.get(k).value);
                                                        Matcher matcher2 = pattern_otherwise.matcher(e.add_item_list.get(k).value);
                                                        SimpleDateFormat formatter = new SimpleDateFormat();
                                                        String format = defaultformat;
                                                        if(matcher.matches()) {
                                                            matcher.find();   
                                                            if(matcher.groupCount() > 0) {
                                                                format = matcher.group(2);
                                                            }
                                                            formatter = new SimpleDateFormat(format);
                                                            if(matcher.groupCount() > 2) {
                                                                formatter.setTimeZone(TimeZone.getTimeZone(matcher.group(4)));
                                                            }
                                                        } else if(matcher2.matches()) {
                                                            format = "US-style SMXCore Timestamp";
                                                            if(matcher.groupCount() > 0) {
                                                                format = matcher.group(2);
                                                            }
                                                            //tipuri:
                                                            // US-style SMXCore Timestamp: mm/dd/yyyy hh:mm:ss
                                                            // UNIX: sssssssss (since 1970)
                                                            // ISO 8601: yyyy-mm-ddThh:mm:ssZ
                                                            if(format.equals("US-style SMXCore Timestamp")) {
                                                                formatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
                                                            } else if(format.equals("ISO 8601")) {
                                                                formatter = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss\\Z");
                                                            } else {
                                                                formatter = new SimpleDateFormat("Invalid dateformat: " + format);
                                                            }
                                                        }
                                                        a.get(ik).value = formatter.format(commondate);
                                                    } else {
                                                        a.get(ik).value = e.add_item_list.get(k).value;
                                                    }
                                                    break;
                                                }
                                            }
                                            if(!found) {
                                                ValueNameCouple e2 = new ValueNameCouple();
                                                e2.name = sIntPrefix + e.add_item_list.get(k).item;
                                                String t;
                                                if(e.add_item_list.get(k).value.startsWith("!Timestamp")) {
                                                    Matcher matcher = pattern_custom.matcher(e.add_item_list.get(k).value);
                                                    Matcher matcher2 = pattern_otherwise.matcher(e.add_item_list.get(k).value);
                                                    SimpleDateFormat formatter = new SimpleDateFormat();
                                                    String format = defaultformat;
                                                    if(matcher.matches()) {
                                                        matcher.find();   
                                                        if(matcher.groupCount() > 0) {
                                                            format = matcher.group(2);
                                                        }
                                                        formatter = new SimpleDateFormat(format);
                                                        if(matcher.groupCount() > 2) {
                                                            formatter.setTimeZone(TimeZone.getTimeZone(matcher.group(4)));
                                                        }
                                                    } else if(matcher2.matches()) {
                                                        format = "US-style SMXCore Timestamp";
                                                        if(matcher.groupCount() > 0) {
                                                            format = matcher.group(2);
                                                        }
                                                        //tipuri:
                                                        // US-style SMXCore Timestamp: mm/dd/yyyy hh:mm:ss
                                                        // UNIX: sssssssss (since 1970)
                                                        // ISO 8601: yyyy-mm-ddThh:mm:ssZ
                                                        if(format.equals("US-style SMXCore Timestamp")) {
                                                            formatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
                                                        } else if(format.equals("ISO 8601")) {
                                                            formatter = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss\\Z");
                                                        } else {
                                                            formatter = new SimpleDateFormat("Invalid dateformat: " + format);
                                                        }
                                                    }
                                                    e2.value = formatter.format(commondate);
                                                } else {
                                                    e2.value = e.add_item_list.get(k).value;
                                                }
                                                a.add(e2);
                                            }
                                        }
                                    }
                                }
                                ValueNameCouple[] list = new ValueNameCouple[a.size()];
                                list = a.toArray(list);
                                Arrays.sort(list, new Comparator<ValueNameCouple>() {
                                    @Override
                                    public int compare(ValueNameCouple lhs, ValueNameCouple rhs) {
                                        // -1 - less than, 1 - greater than, 0 - equal, all inversed for descending
                                        return lhs.name.compareTo(rhs.name);
                                    }
                                });
                                if(list.length == 0) {
                                    sValue = "";
                                } else if(list.length == 1) {
//                                    System.out.println("Only found one");
                                    sValue = list[0].value;
                                } else {
//                                    System.out.println("Found more than one");
                                    String pref = list[0].name;
                                    for(int i = 1; i < list.length; i++) {
                                        pref = greatestCommonPrefix(list[i].name, pref);
                                    }
                                    
                                    int i;
                                    for(i = pref.length() - 1; i >= 0 && pref.charAt(i) != '/'; i--);
                                    pref = pref.substring(0, i + 1);
//                                    System.out.println(pref);
                                    JsonObject j = makeObjects(pref, list);
                                    sValue = j.toString();
                                }
                                Date now = new Date();
                                if((e.begin == null || now.after(e.begin)) && (e.end == null || now.before(e.end))) {
                                    mqttMessage = new MqttMessage(sValue.getBytes());
                                    mqttMessage.setQos(iPubQos);

                                    mqttClient.publish(sMQTTAttr, mqttMessage);
                                }
                                iConnected = 1;

                            }
                        } catch (Exception ex) {

                            //MQTTDisconnect();
                            iConnected = 0;
//                            if (Debug == 1) {
//                                System.out.println(ex.getMessage());
//                            }
                            logger.warning(ex.getMessage());
                        }
                    }

                } catch (Exception e) {
//                    if (Debug == 1) {
//                        System.out.println(e.getMessage());
//                    }
                    logger.warning(e.getMessage());
                }

            }
        } catch (Exception e) {

        }
    }
    public int Pause = 0;
    public int memPause = 0;
    public int bStop = 0;

    public int Debug = 1;

    public long lPeriod = 0;
    public long lIniSysTimeMs = 0;
    public long lMemSysTimeMs = 0;
    public long lDelay = 0;

    MqttClient mqttClient = null;
    MqttMessage mqttMessage;
    String sTopic = "";
    String sContent = "";
    int iSubQos = 1;
    int iPubQos = 1;
    String sBroker = "";
    String sUserName = "";
    String sUserPass = "";
    String[] sTopics = {"#", "#"};

    //MQTT client id to use for the device. "" will generate a client id automatically
    String sClientID = "ClientId";
    MemoryPersistence persistence = new MemoryPersistence();
    MqttConnectOptions connOpts = new MqttConnectOptions();

    int iConnected = 0;
    String sSubAssocAttr = "";

    public void MQTTConnect() {
        try {

            mqttClient = new MqttClient(sBroker, sClientID + Long.toString(System.currentTimeMillis()), persistence);
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void messageArrived(String topic, MqttMessage msg)
                        throws Exception {
                    
                    Date date = new Date();

                    logger.fine("Message arrived on topic " + topic + " with message " + msg);
                    if (sSubPrefix.length() > 0) {
                        if (topic.startsWith(sSubPrefix)) {
                            topic = topic.substring(sSubPrefix.length());
                            logger.finer("Stripped prefix " + sSubPrefix + " from topic " + topic);
                        } else {
                            return;
                        }
                    }
                    Association assoc = SubAssoc.get(topic);
                    if (assoc == null) {
                        logger.info("Got topic " + topic + " that was not subscribed - problem in MQTT broker?");
                        return;
                    }
                    sSubAssocAttr = assoc.internalName;
                    if (sSubAssocAttr == null) {
                        logger.info("Got topic " + topic + " that was not subscribed - problem in MQTT broker?");
                        return;
                    }
                    String timestamp = "";
                    if(assoc.hasTsCfg) {
                        TimeZone timezone;
                        if(assoc.tsCfg.timezone.equals("Local")) {
                            Calendar c = Calendar.getInstance();
                            timezone = c.getTimeZone();
                        } else {
                            timezone = TimeZone.getTimeZone(assoc.tsCfg.timezone);
                        }
                        //tipuri:
                        // US-style SMXCore Timestamp: mm/dd/yyyy hh:mm:ss
                        // UNIX: sssssssss (since 1970)
                        // ISO 8601: yyyy-mm-ddThh:mm:ssZ
                        if(assoc.tsCfg.ts_type.equals("US-style SMXCore Timestamp")) {
                            DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
                            df.setTimeZone(timezone);
                            timestamp = df.format(date);
                        } else if(assoc.tsCfg.ts_type.equals("UNIX")) {
                            long unixTime = System.currentTimeMillis() / 1000L;
                            timestamp = String.valueOf(unixTime);
                        } else if(assoc.tsCfg.ts_type.equals("ISO 8601")) {
                            DateFormat df = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss\\Z");
                            df.setTimeZone(timezone);
                            timestamp = df.format(date);
                        } else {
                            timestamp = "Invalid dateformat: " + assoc.tsCfg.ts_type;
                        }
                    }
                    boolean sep_value = assoc.hasTsCfg && assoc.tsCfg.only_once;
                    String added = "";
                    if(sep_value) {
                        added = assoc.tsCfg.val_suffix;
                        System.out.println(added);
                    }
                    if(sSubAssocAttr.contains("[")) {
                        String[] sSAA = sSubAssocAttr.split("\\[");
                        sSAA[0] = sSAA[0].trim();
                        sSAA[1] = sSAA[1].trim();
                        sSAA[1] = sSAA[1].substring(0, sSAA[1].length() - 1);
                        String[] req = sSAA[1].split(",");
                        for(int i = 0; i < req.length; i++) {
                            req[i] = req[i].trim();
                        }
                        String smsg = msg.toString().trim();
                        if(smsg.startsWith("[")) {
                            smsg = smsg.substring(1, smsg.length() - 1);
                            String[] ms = smsg.split(",");
                            for(int i = 0; i < ms.length; i++) {
                                ms[i] = ms[i].trim();
                                if(ms[i].endsWith("]")) {
                                    ms[i] = ms[i].substring(0, ms[i].length() - 1);
                                }
                            }
                            logger.finer("Found json array of " + req.length + " elements (real found: " + ms.length + ")");
                            for(int i = 0; i < req.length; i++) {
                                if(i < ms.length) {
                                    if(req[i].startsWith("\"") && ms[i].startsWith("\"")) {
                                        ms[i] = ms[i].substring(1, ms[i].length() - 1);
                                    }
                                    if(req[i].startsWith("\"")) {
                                        req[i] = req[i].substring(1, req[i].length() - 1);
                                    }
                                    if(sSAA[0].equals("")) {
                                        pDataSet.put(sIntPrefix + req[i] + added, ms[i]);
                                        logger.finest("Set " + sIntPrefix + req[i] + added + " to " + ms[i]);
                                    } else {
                                        logger.finest("Set " + sIntPrefix + sSAA[0] + "/" + req[i] + added + " to " + ms[i]);
                                        pDataSet.put(sIntPrefix + sSAA[0] + "/" + req[i], ms[i]);
                                    }
                                    if(assoc.hasTsCfg && !assoc.tsCfg.only_once) {
                                        pDataSet.put(sIntPrefix + sSAA[0] + "/" + req[i] + assoc.tsCfg.ts_suffix, timestamp);
                                    }
                                } else {
                                    if(sSAA[0].equals("")) {
                                        logger.finest("Did not get a value for json array at index " + i + "(" + sIntPrefix + req[i] + added + ")");
                                        pDataSet.put(sIntPrefix + req[i] + added, "");
                                    } else {
                                        logger.finest("Did not get a value for json array at index " + i + "(" + sIntPrefix + sSAA[0] + added + "/" + req[i] + ")");
                                        pDataSet.put(sIntPrefix + sSAA[0] + "/" + req[i] + added, "");
                                    }
                                }
                            }
                        } else {
                            if(sSAA[0].equals("")) {
                                logger.info("Expected array, got value " + msg.toString() + " for unnamed array with topic " + topic); // Should this be Warning, info or Fine/Finer/Finest?
                                pDataSet.put(sIntPrefix + req[0] + "/" + "ERR", msg.toString());
                            } else {
                                logger.info("Expected array, got value " + msg.toString() + " for array " + sIntPrefix + sSAA[0] + " with topic " + topic); // Should this be Warning, info or Fine/Finer/Finest?
                                pDataSet.put(sIntPrefix + sSAA[0], msg.toString());
                            }
                        }
                        if(assoc.hasTsCfg && assoc.tsCfg.only_once) {
                            pDataSet.put(sIntPrefix + sSAA[0] + assoc.tsCfg.ts_suffix, timestamp);
                        }
                    } else if(assoc.readJson) {
                        JsonReader jsonReader = Json.createReader(new StringReader(msg.toString()));
                        JsonObject object = jsonReader.readObject();
                        jsonReader.close();
                        logger.fine("Found json object");
                        List<ValueNameCouple> a = new ArrayList();
                        decomposeObject(sIntPrefix + sSubAssocAttr, object, assoc, a);
                        
                        for(int ijk = 0; ijk < assoc.list_apply_order.size(); ijk++) {
                            if(assoc.list_apply_order.get(ijk).equals("replace")) {
                                for(int k = 0; k < assoc.replace_list.size(); k++) {
                                    for(int ik = 0; ik < a.size(); ik++) {
                                        Matcher match = assoc.replace_list.get(k).pattern.matcher(a.get(ik).name);
                                        a.get(ik).name = match.replaceAll(assoc.replace_list.get(k).replacement);
                                    }
                                }
                            } else if(assoc.list_apply_order.get(ijk).equals("rescale")) {
                                for(int k = 0; k < assoc.rescale_list.size(); k++) {
                                    for(int ik = 0; ik < a.size(); ik++) {
                                        Matcher match = assoc.rescale_list.get(k).pattern.matcher(a.get(ik).name);
                                        if(match.matches()) {
                                            double value = Double.parseDouble(a.get(ik).value);
                                            value *= assoc.rescale_list.get(k).multiplier;
                                            value += assoc.rescale_list.get(k).added;
                                            a.get(ik).value = String.valueOf(value);
                                        }
                                    }
                                }
                            }
                        }
                        
                        for(int i = 0; i < a.size(); i++) {
                            pDataSet.put(a.get(i).name, a.get(i).value);
                        }
                        
                        if(assoc.hasTsCfg && assoc.tsCfg.only_once) {
                            pDataSet.put(sIntPrefix + sSubAssocAttr + assoc.tsCfg.ts_suffix, timestamp);
                        }
                    } else {
                        logger.finer("Got value" + msg.toString() + " for " + sSubAssocAttr + " as topic " + topic);
                        pDataSet.put(sIntPrefix + sSubAssocAttr + added, msg.toString());
                        if(assoc.hasTsCfg && !assoc.tsCfg.only_once) {
                            pDataSet.put(sIntPrefix + sSubAssocAttr + assoc.tsCfg.ts_suffix, timestamp);
                        }
                    }

                    //System.out.println("Recived:" + sTopic);
                    //System.out.println("Recived:" + new String(msg.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken arg0) {
                    //System.out.println("Delivary complete");
                    // Params.put("sLastSentMsgDate", sdfLogDate.format(new Date()));
                }

                @Override
                public void connectionLost(Throwable arg0) {
                    // TODO Auto-generated method stub
                    MQTTDisconnect();
                }
            });

            connOpts.setCleanSession(true);
            if (sUserName.length() > 0) {
                logger.info("Received new credentials for MQTT Broker");
                connOpts.setUserName(sUserName);
                connOpts.setPassword(sUserPass.toCharArray());
            }

            mqttClient.connect(connOpts);

            /*  for (String sSubTopic : ssSubTopics) {
             try {
             if (sSubTopic.length() > 0) {
             mqttClient.subscribe(sSubPrefix + sSubTopic, iSubQos);
             }
             } catch (Exception ex) {
             System.out.println(ex.getMessage());
             }
             }*/
            Iterator it = SubAssoc.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry crt = (Map.Entry) it.next();
                Association assoc = (Association) crt.getValue();
                sTopic = assoc.mqttTopic;
                mqttClient.subscribe(sSubPrefix + sTopic, iSubQos);
                logger.config("Subscribed to topic " + sSubPrefix + sTopic);
                //System.out.println(sKey + " : " + sValue);
            }

            iConnected = 1;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void MQTTDisconnect() {
        try {
            logger.info("Disconnected from MQTT Broker");
            iConnected = 0;
            mqttClient.disconnect();
            mqttClient.close();
            mqttClient = null;
        } catch (Exception e) {
            logger.warning(e.getMessage());
        }
    }

    Thread tPublishLoop = null;

    @Override
    public void Start() {
        try {
            tPublishLoop = new Thread(new Runnable() {

                @Override
                public void run() {
                    MQTTPublishLoop();
                }
            });
            tPublishLoop.start();

        } catch (Exception e) {
        }
    }
}
