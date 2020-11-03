/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoClient;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;

import java.util.logging.Logger;
import java.util.logging.Level;

import com.mongodb.Block;
import com.mongodb.client.FindIterable;

import static com.mongodb.client.model.Filters.*;

import java.util.Enumeration;
import java.util.Properties;

import util.PropUtil;

/**
 *
 * @author cristi
 */
public class MongoDBClient extends Module {

    @Override
    public void Initialize() {
        
        sURI = PropUtil.GetString(pAttributes, "sURI", "mongodb://localhost");
        sDatabase = PropUtil.GetString(pAttributes, "sDatabase", "SMX");
        sCollection = PropUtil.GetString(pAttributes, "sCollection", "LD01");

        sFind = PropUtil.GetString(pAttributes, "sFind", "/");
        sRepl = PropUtil.GetString(pAttributes, "sRepl", ".");
        sIDName = PropUtil.GetString(pAttributes, "sIDName", "_id");

        PropUtil.LoadFromFile(pWriteAssociation, PropUtil.GetString(pAttributes, "pWriteAssociation", ""));
        PropUtil.LoadFromFile(pReadAssociation, PropUtil.GetString(pAttributes, "pReadAssociation", ""));

        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));

        sWritePrefix = PropUtil.GetString(pAttributes, "sWritePrefix", "SMX/");
        sReadPrefix = PropUtil.GetString(pAttributes, "sReadPrefix", "SMX/");
        sIntPrefix = PropUtil.GetString(pAttributes, "sIntPrefix", "SMX/");

        lPeriod = PropUtil.GetLong(pAttributes, "lPeriod", 5000);

        Logger mongoLogger = Logger.getLogger( "org.mongodb.driver" );
        mongoLogger.setLevel(Level.SEVERE); 
        
    }
    
    Properties pDataSet = null;    
    String sWritePrefix = "SMX/";
    String sReadPrefix = "SMX/";
    String sIntPrefix = "SMX/";

    Properties pWriteAssociation = new Properties();
    Properties pReadAssociation = new Properties();

    String sURI = "mongodb://localhost";
    String sDatabase = "SIRIUSsgs";
    String sCollection = "rest";

    MongoClient mongoClient = null;
    MongoDatabase database = null;
    MongoCollection<Document> collection = null;

    UpdateOptions uoUpsert = new UpdateOptions();
    String sKey = "1";
    String sRepl = "-";
    String sFind = ".";
    String sIDName = "_id";

    public long lPeriod = 0;
    public long lIniSysTimeMs = 0;
    public long lDelay = 0;
    public long lMemSysTimeMs = 0;

    public int Pause = 0;
    public int memPause = 0;
    public int bStop = 0;

    public int Debug = 0;

    public void ReadWriteLoop() {
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
                        ConnOpen();
                    }

                    SendVals();

                    ReadVals();

                } catch (Exception e) {
                    if (Debug == 1) {
                        System.out.println(e.getMessage());
                    }
                }

            }
        } catch (Exception e) {
            
        }
    }
    int isv = 0;

    List<Document> documentList = new ArrayList<Document>();

    String[] ssID = null;
    //String[] ssIDAttr=null;
    String[] ssIDAttr = null;

    FindIterable<Document> iterable;

    String sValue;
    Enumeration eKeys;
    String sInternalAttr;
    String sMongoDBAttr;

    private void ReadVals() {
        try {

            eKeys = pReadAssociation.keys();
            while (eKeys.hasMoreElements()) {
                try {
                    if (iConnected == 0) {
                        break;
                    }

                    sMongoDBAttr = (String) eKeys.nextElement();
                    sIntDataPath = sIntPrefix + pReadAssociation.getProperty(sMongoDBAttr, "");

                    sMongoDBAttr = sReadPrefix + sMongoDBAttr;
                    ssID = sMongoDBAttr.split(sFind);
                    sDataID = ssID[ssID.length - 2];
                    sDataAttr = ssID[ssID.length - 1];
                    sDataPath = sMongoDBAttr.replaceAll(sFind, sRepl);
                    iterable = collection.find(eq(sIDName, sDataID));
                    iterable.forEach(new Block<Document>() {
                        @Override
                        public void apply(final Document document) {

                            pDataSet.put(sIntDataPath, (String) document.get(sDataAttr));

                            // System.out.println(document);
                        }
                    });
                } catch (Exception ex) {

                    if (Debug == 1) {
                        System.out.println(ex.getMessage());
                    }
                }
            }

        } catch (Exception e) {

        }
    }
    String sDataPath = "";
    String sDataID = "";
    String sDataAttr = "";
    String sIntDataPath = "";

    private void SendVals() {
        try {
            eKeys = pWriteAssociation.keys();
            while (eKeys.hasMoreElements()) {
                try {
                    if (iConnected == 0) {
                        break;
                    }
                    sInternalAttr = (String) eKeys.nextElement();
                    sMongoDBAttr = sWritePrefix + (String) pWriteAssociation.getProperty(sInternalAttr, "");
                    if (sMongoDBAttr.length() > sWritePrefix.length()) {
                        sValue = (String) pDataSet.getProperty(sIntPrefix + sInternalAttr, "");
                        ssID = sMongoDBAttr.split(sFind);
                        sDataID = ssID[ssID.length - 2];
                        sDataAttr = ssID[ssID.length - 1];
                        sDataPath = sMongoDBAttr.replaceAll(sFind, sRepl);

                        collection.updateOne(new Document(sIDName, sDataID),
                                new Document("$set", new Document(sDataAttr, sValue)), uoUpsert);

                    }
                } catch (Exception ex) {

                    if (Debug == 1) {
                        System.out.println(ex.getMessage());
                    }
                }
            }

        } catch (Exception e) {

        }
    }

    private void ConnOpen() {
        try {

            mongoClient = new MongoClient(new MongoClientURI(sURI));
            database = mongoClient.getDatabase(sDatabase);
            collection = database.getCollection(sCollection);
            uoUpsert.upsert(true);

            iConnected = 1;
        } catch (Exception e) {
            iConnected = 0;

        }
    }

    int iConnected = 0;

    private void ConnClose() {
        try {
            iConnected = 0;
            mongoClient.close();
            mongoClient = null;

        } catch (Exception e) {

        }
    }

    Thread tReadWriteLoop = null;

    @Override
    public void Start() {
        try {
            tReadWriteLoop = new Thread(new Runnable() {

                @Override
                public void run() {
                    ReadWriteLoop();
                }
            });
            tReadWriteLoop.start();

        } catch (Exception e) {

        }
    }

}
