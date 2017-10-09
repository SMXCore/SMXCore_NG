/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.io.FileInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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

/**
 *
 * @author cristi, vlad
 */
public class MQTTClient extends Module {

    private class Association {
        String mqttTopic;
        String internalName;
        boolean isClassic; // Backwards compatibility mode
        boolean readJson;
    }
    
    @Override
    public void Initialize() {
        sBroker = PropUtil.GetString(pAttributes, "sBroker", "tcp://localhost:18159");
        logger.config("MQTT Broker is at " + sBroker);
        sUserName = PropUtil.GetString(pAttributes, "sUserName", "");
        sUserPass = PropUtil.GetString(pAttributes, "sUserPass", "");
        sClientID = PropUtil.GetString(pAttributes, "sClientID", "");
        logger.config("Connecting to MQTT Broker with client ID" + sClientID);
        SubAssoc = new HashMap();

       // PropUtil.LoadFromFile(pPubAssociation, PropUtil.GetString(pAttributes, "pPubAssociation", ""));
       // PropUtil.LoadFromFile(pSubAssociation, PropUtil.GetString(pAttributes, "pSubAssociation", ""));
        logger.config("Loading subscribe associations");
        ArrayList<Association> SubAssocs = loadAssoc(PropUtil.GetString(pAttributes, "pSubAssociation", ""), false);
        for(Association a: SubAssocs) {
            SubAssoc.put(a.mqttTopic, a);
        }
        logger.config("Loading public associations");
        PubAssoc = loadAssoc(PropUtil.GetString(pAttributes, "pPubAssociation", ""), true);

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

    }
    
    ArrayList<Association> loadAssoc(String file, boolean isPublish) {
        if(file.endsWith("json")) {
            logger.config("MQTT Association config file «" + file + "» is a json");
            return loadAssocJson(file, isPublish);
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
                if(isPublish) {
                    assoc.internalName = e;
                    assoc.mqttTopic = sPubPrefix + (String) prop.getProperty(e, "");
                } else {
                    assoc.internalName = (String) prop.getProperty(e, "");
                    assoc.mqttTopic = sSubPrefix + e;
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
    
    @Override
    public void receiveEvent(String key, String value, Date date) {
        logger.finest("MQTT Receive Event and attempt publish");
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
                if(key.matches(reg)) {
                    mqttMessage = new MqttMessage(value.getBytes());
                    mqttMessage.setQos(iPubQos);

                    mqttClient.publish(sMQTTAttr, mqttMessage);
                    DateFormat df = DateFormat.getDateInstance();
                    
                    logger.finer("Event with date " + df.format(new Date()) + " matched topic " + sMQTTAttr + " with element " + key);
                }

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
    }
    
    ArrayList<Association> loadAssocJson(String file, boolean isPublish) {
        ArrayList<Association> list = new ArrayList();
        try {
            JsonReader jsr = Json.createReader(new FileInputStream(file));
            JsonObject jso = jsr.readObject();
            Iterator it = jso.entrySet().iterator();
            while(it.hasNext()) {
                try {
                    Map.Entry crt = (Map.Entry) it.next();
                    String name = (String) crt.getKey();
                    Association assoc = new Association();
                    JsonValue value = (JsonValue) crt.getValue();
                    if(value.getValueType() == JsonValue.ValueType.STRING) {
                        JsonString ss = (JsonString) value;
                        assoc.isClassic = true;
                        assoc.readJson = false;
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
                        assoc.readJson = ass.getBoolean("readJson", false);
                        assoc.internalName = ass.getString("internalName", name);
                        assoc.mqttTopic = ass.getString("mqttTopic");
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
    
    JsonObject makeObjects(String prefix, String[] things) {
        JsonObjectBuilder b = Json.createObjectBuilder();
        int last = 0;
        logger.finer("Making an object from prefix " + prefix + " from a string array of " + things.length + " objects");
        for(int i = 1; i <= things.length; i++) {
            if(i == things.length || !getNextSubObject(prefix.length(), things[last]).equals(getNextSubObject(prefix.length(), things[i]))) {
                String next = getNextSubObject(prefix.length(), things[last]);
//                System.out.println(next);
                if(next.charAt(next.length() - 1) == '/') {
//                    System.out.println("!");
                    b.add(next.substring(0, next.length() - 1), makeObjects(prefix + next, Arrays.copyOfRange(things, last, i)));
                } else {
                    logger.finer("Found simple value: " + things[last]);
                    b.add(next, pDataSet.getProperty(things[last], ""));
                }
                last = i;
            }
        }
        return b.build();
    }
    
    void decomposeObject(String prefix, JsonObject obj) {
        try {
            Iterator it = obj.entrySet().iterator();
            while(it.hasNext()) {
                try {                    
                    Map.Entry crt = (Map.Entry) it.next();
                    String name = (String) crt.getKey();
                    JsonValue value = (JsonValue) crt.getValue();
                    if(value.getValueType() == JsonValue.ValueType.OBJECT) {
                        JsonObject objj = (JsonObject) value;
                        decomposeObject(prefix + '/' + name, objj);
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
                        pDataSet.put(prefix + '/' + name, ss);
                        logger.finest("Found value for " + prefix + '/' + name + ": " + ss);
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
                                List<String> a = new ArrayList();
                                while(eKeys2.hasMoreElements()) {
                                    String crt = (String) eKeys2.nextElement();
                                    if(crt.matches(reg)) {
                                        a.add(crt);
                                        logger.finer("Matched topic " + sMQTTAttr + " with element " + crt);
                                    }
                                }
                                String[] list = new String[a.size()];
                                list = a.toArray(list);
                                Arrays.sort(list);
                                if(list.length == 0) {
                                    sValue = "";
                                } else if(list.length == 1) {
//                                    System.out.println("Only found one");
                                    sValue = (String) pDataSet.getProperty(list[0], "");
                                } else {
//                                    System.out.println("Found more than one");
                                    String pref = list[0];
                                    for(int i = 1; i < list.length; i++) {
                                        pref = greatestCommonPrefix(list[i], pref);
                                    }
                                    int i;
                                    for(i = pref.length() - 1; i > 0 && pref.charAt(i) != '/'; i--);
                                    pref = pref.substring(0, i + 1);
//                                    System.out.println(pref);
                                    JsonObject j = makeObjects(pref, list);
                                    sValue = j.toString();
                                }

                                mqttMessage = new MqttMessage(sValue.getBytes());
                                mqttMessage.setQos(iPubQos);

                                mqttClient.publish(sMQTTAttr, mqttMessage);
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
                                        pDataSet.put(sIntPrefix + req[i], ms[i]);
                                        logger.finest("Set " + sIntPrefix + req[i] + " to " + ms[i]);
                                    } else {
                                        logger.finest("Set " + sIntPrefix + sSAA[0] + "/" + req[i] + " to " + ms[i]);
                                        pDataSet.put(sIntPrefix + sSAA[0] + "/" + req[i], ms[i]);
                                    }
                                } else {
                                    if(sSAA[0].equals("")) {
                                        logger.finest("Did not get a value for json array at index " + i + "(" + sIntPrefix + req[i] + ")");
                                        pDataSet.put(sIntPrefix + req[i], "");
                                    } else {
                                        logger.finest("Did not get a value for json array at index " + i + "(" + sIntPrefix + sSAA[0] + "/" + req[i] + ")");
                                        pDataSet.put(sIntPrefix + sSAA[0] + "/" + req[i], "");
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
                    } else if(assoc.readJson) {
                        JsonReader jsonReader = Json.createReader(new StringReader(msg.toString()));
                        JsonObject object = jsonReader.readObject();
                        jsonReader.close();
                        logger.fine("Found json object");
                        decomposeObject(sIntPrefix + sSubAssocAttr, object);
                    } else {
                        logger.finer("Got value" + msg.toString() + " for " + sSubAssocAttr + " as topic " + topic);
                        pDataSet.put(sIntPrefix + sSubAssocAttr, msg.toString());
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
