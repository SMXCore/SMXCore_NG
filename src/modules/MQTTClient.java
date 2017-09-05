/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import util.PropUtil;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.Json;

/**
 *
 * @author cristi, vlad
 */
public class MQTTClient extends Module {

    @Override
    public void Initialize() {
        sBroker = PropUtil.GetString(pAttributes, "sBroker", "tcp://localhost:18159");
        sUserName = PropUtil.GetString(pAttributes, "sUserName", "");
        sUserPass = PropUtil.GetString(pAttributes, "sUserPass", "");
        sClientID = PropUtil.GetString(pAttributes, "sClientID", "");

        PropUtil.LoadFromFile(pPubAssociation, PropUtil.GetString(pAttributes, "pPubAssociation", ""));
        PropUtil.LoadFromFile(pSubAssociation, PropUtil.GetString(pAttributes, "pSubAssociation", ""));

        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));

        sPubPrefix = PropUtil.GetString(pAttributes, "sPubPrefix", "SMX/");
        sSubPrefix = PropUtil.GetString(pAttributes, "sSubPrefix", "SMX/");
        sIntPrefix = PropUtil.GetString(pAttributes, "sIntPrefix", "SMX/");

        sSubTopics = PropUtil.GetString(pAttributes, "sSubTopics", "");
        ssSubTopics = sSubTopics.split(",");

        iPubQos = PropUtil.GetInt(pAttributes, "iReadQos", 1);
        iSubQos = PropUtil.GetInt(pAttributes, "iSubQos", 1);

        lPeriod = PropUtil.GetLong(pAttributes, "lPeriod", 5000);

    }

    String sSubTopics = "";
    String[] ssSubTopics = null;

    Properties pDataSet = null;

    String sPubPrefix = "SMX/";
    String sSubPrefix = "SMX/";
    String sIntPrefix = "SMX/";

    Properties pPubAssociation = new Properties();
    Properties pSubAssociation = new Properties();

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
        for(int i = 1; i <= things.length; i++) {
            if(i == things.length || !getNextSubObject(prefix.length(), things[last]).equals(getNextSubObject(prefix.length(), things[i]))) {
                String next = getNextSubObject(prefix.length(), things[last]);
                System.out.println(next);
                if(next.charAt(next.length() - 1) == '/') {
                    System.out.println("!");
                    b.add(next.substring(0, next.length() - 1), makeObjects(prefix + next, Arrays.copyOfRange(things, last, i)));
                } else {
                    b.add(next, pDataSet.getProperty(things[last], ""));
                }
                last = i;
            }
        }
        return b.build();
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
                        MQTTDisconnect();
                        pPubAssociation = new Properties();
                        pSubAssociation = new Properties();
                        Initialize();
                    }
                    
                    if (iConnected == 0) {
                        MQTTConnect();
                    }
                    if (iConnected == 0) {
                        continue;
                    }
                    eKeys = pPubAssociation.keys();
                    while (eKeys.hasMoreElements()) {
                        try {
                           // if (iConnected == 0) {
                           //     break;
                           // }
                            sInternalAttr = (String) eKeys.nextElement();
                            sMQTTAttr = sPubPrefix + (String) pPubAssociation.getProperty(sInternalAttr, "");
                            if (sMQTTAttr.length() > sPubPrefix.length()) {
                                String reg = "^" + sIntPrefix + sInternalAttr;
                                reg = reg.replaceAll("/", "\\/");
                                Enumeration eKeys2 = pDataSet.keys();
                                List<String> a = new ArrayList();
                                while(eKeys2.hasMoreElements()) {
                                    String crt = (String) eKeys2.nextElement();
                                    if(crt.matches(reg)) {
                                        a.add(crt);
                                    }
                                }
                                String[] list = (String[]) a.toArray();
                                Arrays.sort(list);
                                if(list.length == 0) {
                                    sValue = "";
                                } else if(list.length == 1) {
                                    sValue = (String) pDataSet.getProperty(list[0], "");
                                } else {
                                    String pref = list[0];
                                    for(int i = 1; i < list.length; i++) {
                                        pref = greatestCommonPrefix(list[1], pref);
                                    }
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
                            if (Debug == 1) {
                                System.out.println(ex.getMessage());
                            }
                        }
                    }

                } catch (Exception e) {
                    if (Debug == 1) {
                        System.out.println(e.getMessage());
                    }
                }

            }
        } catch (Exception e) {

        }
    }
    public int Pause = 0;
    public int memPause = 0;
    public int bStop = 0;

    public int Debug = 0;

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

                    if (sSubPrefix.length() > 0) {
                        if (topic.startsWith(sSubPrefix)) {
                            topic = topic.substring(sSubPrefix.length());
                        } else {
                            return;
                        }
                    }
                    sSubAssocAttr = pSubAssociation.getProperty(topic).trim();
                    if (sSubAssocAttr == null) {
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
                            }
                            for(int i = 0; i < req.length; i++) {
                                if(i < ms.length) {
                                    if(req[i].startsWith("\"") && ms[i].startsWith("\"")) {
                                        ms[i] = ms[i].substring(1, ms[i].length() - 1);
                                    }
                                    if(req[i].startsWith("\"")) {
                                        req[i] = req[i].substring(1, req[i].length() - 1);
                                    }
                                    if(sSAA[0].equals("")) {
                                        pDataSet.put(req[i], ms[i]);
                                    } else {
                                        pDataSet.put(sSAA[0] + "/" + req[i], ms[i]);
                                    }
                                } else {
                                    if(sSAA[0].equals("")) {
                                        pDataSet.put(req[i], ms[i]);
                                    } else {
                                        pDataSet.put(sSAA[0] + "/" + req[i], "");
                                    }
                                }
                            }
                        } else {
                            if(sSAA[0].equals("")) {
                                pDataSet.put(req[0] + "/" + "ERR", msg.toString());
                            } else {
                                pDataSet.put(sSAA[0], msg.toString());
                            }
                        }
                    } else {
                        pDataSet.put(sSubAssocAttr, msg.toString());
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
            eKeys = pSubAssociation.keys();
            while (eKeys.hasMoreElements()) {
                sTopic = (String) eKeys.nextElement();
                mqttClient.subscribe(sSubPrefix + sTopic, iSubQos);
                //System.out.println(sKey + " : " + sValue);
            }

            iConnected = 1;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void MQTTDisconnect() {
        try {
            iConnected = 0;
            mqttClient.disconnect();
            mqttClient.close();
            mqttClient = null;
        } catch (Exception e) {
            
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
