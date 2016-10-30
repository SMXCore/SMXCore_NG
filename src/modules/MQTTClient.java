/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.util.Enumeration;
import java.util.Properties;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import util.PropUtil;

/**
 *
 * @author cristi
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

    public void MQTTPublishLoop() {
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
                                sValue = (String) pDataSet.getProperty(sIntPrefix + sInternalAttr, "");

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
                    sSubAssocAttr = pSubAssociation.getProperty(topic);
                    if (sSubAssocAttr == null) {
                        return;
                    }
                    pDataSet.put(sSubAssocAttr, msg.toString());

                    //System.out.println("Recived:" + sTopic);
                    //System.out.println("Recived:" + new String(msg.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken arg0) {
                    //System.out.println("Delivary complete");
                    // Params.put("sLastSentMsgDate", sdfLogDate.format(new Date()));
                }

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
