/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import util.ConvertUtil;
import util.PropUtil;

/**
 *
 * @author PC16_EB105
 */
public class GenericSerialCom extends Module {
    
    private class BufferizedRecord {
        String source, timestamp, tsvec;
        int maxBufSize, tsperiod;

        public BufferizedRecord(String source, String timestamp, String tsvec, int maxBufSize, int tsperiod) {
            this.source = source;
            this.timestamp = timestamp;
            this.tsvec = tsvec;
            this.maxBufSize = maxBufSize;
            this.tsperiod = tsperiod;
        }
        
    }
    
    private String sIP = "localhost";
    private int iPort = 502;
    private String separators, startChars, stopChars, ignoreList;
    public Socket s = null;
    public OutputStream outToServer = null;
    public InputStream inFromServer = null;
    private ArrayList<BufferizedRecord> bufrecords;
    
    private String mask = "";
    
    String configMessage = "";
    
    private boolean sendDateTime; // de fapt ar trebui un string parametrizabil
    BufferedWriter bw;
    
    @Override
    public void Initialize() {
        //pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        //PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
        //sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        //lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);
        
        JsonObject jso = read_jso_from_file(sAttributesFile);
        sPrefix = jso.getString("prefix");
        sIP = jso.getString("sIP", "localhost");
        iPort = jso.getInt("iPort", 502);
        pDataSet = mmManager.getSharedData(jso.getString("dataSet", ""));
        pDataSet.put("/SMX/GSC_Alive", "True");
        separators = jso.getString("separators", ";");
        startChars = jso.getString("startChars", "@");
        mask = jso.getString("mask", "");
        stopChars = jso.getString("stopChars", "\n\r");
        configMessage = jso.getString("configMessage", "");
        ignoreList = jso.getString("ignoreList", "[^a-zA-Z0-9=\\-\\\\.,]");
        sendDateTime = jso.getBoolean("sendDateTime", false);
        lPeriod = jso.getInt("lPeriod", 1000);
        replace_list = new ArrayList();
        accept_list = new ArrayList();
        bufrecords = new ArrayList();
        try {
            //bw = new BufferedWriter(new FileWriter("GSCDebug", true));
        } catch(Exception e) {
            
        }
        s = null;
        try {
            JsonArray rl = jso.getJsonArray("replace_list");
            for(int i = 0; i < rl.size(); i++) {
                JsonArray descriptor = rl.getJsonArray(i);
                ReplaceDescriptor rd = new ReplaceDescriptor();
                String pat = descriptor.getString(0);
                rd.pattern = Pattern.compile(pat);
                rd.replacement = descriptor.getString(1);
                replace_list.add(rd);
            }
        } catch(Exception e) {
            
        }
        try {
            JsonArray rl = jso.getJsonArray("accept_list");
            for(int i = 0; i < rl.size(); i++) {
                String acc = rl.getString(i);
                accept_list.add(acc);
            }
        } catch(Exception e) {
            
        }
        try {
            JsonArray rl = jso.getJsonArray("bufferizedRecords");
            for(int i = 0; i < rl.size(); i++) {
                JsonObject obj = rl.getJsonObject(i);
                BufferizedRecord br = new BufferizedRecord(obj.getString("source"), obj.getString("timestamp"), obj.getString("tsvec", ""), obj.getInt("maxBufSize", 1000), obj.getInt("tsperiod", 0));
                bufrecords.add(br);
            }
        } catch(Exception e) {
            
        }
        buffer = new String();
        message = new String();
        
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm:ss")
            .appendPattern(":0SSSSSS")
            // create formatter
            .toFormatter();
        nodateformatter = formatter;

        iReadTimeOut = jso.getInt("iReadTimeOut", 2000);
        iConnTimeOut = jso.getInt("iConnTimeOut", 3000);
    }

    public int iConnTimeOut = 3000;
    public int iReadTimeOut = 2000;
    public int iReqPause = 100;
    public int iMaxSuccErr = 10;
   
    public void ReOpen() {
        try {
            try {
                if (s != null) {
                    s.close();
                }
            } catch (Exception e) {
            }

            s = new Socket();
            s.setReuseAddress(true);
            s.connect(new InetSocketAddress(sIP, iPort), iConnTimeOut);
            s.setSoTimeout(iReadTimeOut);
            outToServer = s.getOutputStream();
            inFromServer = s.getInputStream();
            DateTimeFormatter propFormat = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral(" ")
                .appendPattern("HH:mm:ss").toFormatter();
            if(sendDateTime) {
                String datetime = "@dt=" + LocalDateTime.now().format(propFormat) + "\r\n";
                byte[] cmd_byte = datetime.getBytes();
                outToServer.write(cmd_byte);
            
                bw.write(String.format("%05d", msgnr) + " ");
                bw.write(LocalDateTime.now().format(nodateformatter));
                bw.write(" >> ");
                bw.write(datetime.replaceAll("[\\n\\r]", "") + "\n");
                bw.flush();
            }
            if(!configMessage.equals("")) {
                byte[] cmd_byte = configMessage.getBytes();
                outToServer.write(cmd_byte);
            
                bw.write(String.format("%05d", msgnr) + " ");
                bw.write(LocalDateTime.now().format(nodateformatter));
                bw.write(" >> ");
                bw.write(configMessage.replaceAll("[\\n\\r]", "") + "\n");
                bw.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("GenSerCom="+e.getMessage());
        }
    }
    
    byte[] bRec = new byte[4000];
    String buffer;
    String message;
    
    public void Read() {
        //System.out.println("Reading " + System.currentTimeMillis());
        
        try {
            if ((s == null) || (!s.isConnected()) || (s.isInputShutdown()) || (s.isOutputShutdown())) {
                ReOpen();
            }
            String sent;
            if(mask.equals("")) {
                byte[] nothing = new byte[1];
                sent = ".";
                nothing[0] = '.';
                outToServer.write(nothing);
            } else {
                String cmd = "@g=" + mask + "\r\n";
                sent = cmd;
                byte[] cmd_byte = cmd.getBytes();
                outToServer.write(cmd_byte);
            }
            
            bw.write(String.format("%05d", msgnr) + " ");
            bw.write(LocalDateTime.now().format(nodateformatter));
            bw.write(" >> ");
            bw.write(sent.replaceAll("[\\n\\r]", "") + "\n");
            bw.flush();
                //System.out.println("Afterwrite " + System.currentTimeMillis());
            int iRec = inFromServer.read(bRec);
                //System.out.println("Afterread " + System.currentTimeMillis());
            String read = new String(bRec, "UTF-8");
            read = read.substring(0, iRec); // ca sa nu avem 4000 caractere
            //System.out.println("Hexread >" + read);
            //read = ConvertUtil.HexStr2Str(read);
            //System.out.println("Read >" + read);
            buffer = buffer.concat(read);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to read");
            s = null;
        }
    }
    
    Properties pAssociation = new Properties();

    Properties pDataSet = null; // The data set of the real time database
    String sPrefix = ""; // The module's prefix, prefixed to the name of values in the data set
    Thread tLoop = null; // The thread that we are running on

    @Override
    public void Start() {
        try {
            tLoop = new Thread(new Runnable() {

                @Override
                public void run() {
                    Loop();
                    // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }
            });
            tLoop.start();

        } catch (Exception e) {
            // Log(Name + "-Open-" + e.getMessage() + "-" + e.getCause());
        }
    }
    int msgnr = 0;
    
    void nextMessage() {
        int i;
        if(message.length() != 0) return;
        for(i = 0; i < buffer.length(); i++) {
            if(startChars.contains("" + buffer.charAt(i))) {
                break;
            }
        }
        if(i == buffer.length()) {
            buffer = "";
            return;
        }
        int start = i + 1;
        for(i++; i < buffer.length(); i++) {
            if(stopChars.contains("" + buffer.charAt(i))) {
                break;
            }
        }
        if(i == buffer.length()) {
            return;
        }
        int stop = i;
        message = buffer.substring(start, stop);
        if(stop != 0) {
            try {
                bw.write(String.format("%05d", msgnr) + " ");
                bw.write(LocalTime.now().format(nodateformatter));
                bw.write(" << ");
                bw.write(buffer.substring(0, stop + 1).replaceAll("[\\n\\r]", "") + "\n");
                bw.flush();
                msgnr++;
            } catch(Exception e) {
                
            }
        }
        buffer = buffer.substring(stop + 1);
    }
    
    String nextFrame() {
        if(message.length() == 0) return "";
        int i;
        for(i = 0; i < message.length(); i++) {
            if(separators.contains("" + message.charAt(i))) {
                break;
            }
        }
        if(i == message.length()) {
            message = "";
            return "";
        }
        String msg = message.substring(0, i).replaceAll(ignoreList, "");
        message = message.substring(i + 1);
        return msg;
    }
   
    /*int nextFrame() {
        for(int i = 0; i < buffer.length(); i++) {
            if(buffer.charAt(i) == '\n') return i;
        }
        return 0;
    }*/
    class ReplaceDescriptor {
        Pattern pattern;
        String replacement;
    }
    ArrayList<ReplaceDescriptor> replace_list;
    ArrayList<String> accept_list;
    
    void ProcessFrame(String frame) {
        int i;
        for(i = 0; i < frame.length(); i++) {
            if(frame.charAt(i) == '=') break;
        }
        if(i == frame.length()) return;
        String name = frame.substring(0, i);
        String value = frame.substring(i + 1);
        if(accept_list.size() > 0) {
            boolean accepted = false;
            for(String reg : accept_list) {
                Pattern p = Pattern.compile(reg);
                Matcher m = p.matcher(name);
                if(m.find()) {
                    accepted = true;
                }
            }
            if(!accepted) return;
        }
        //...
        //redenumire
        //adaugam prefix
        String internal_name = sPrefix + '/' + name;
        for(i = 0; i < replace_list.size(); i++) {
            Matcher match = replace_list.get(i).pattern.matcher(internal_name);
            internal_name = match.replaceAll(replace_list.get(i).replacement);
        }
        //punem in baza de date
        pDataSet.put(internal_name, value);
        //System.out.println("<GSC> Put in dataset " + internal_name + "= " + value);
    }
    
    LocalTime lastlt;
    String sCrtFileName = "";
    String sLastFileName = "";
    Date dCrtDate = null;
    Date dLastDate = null;
    
    DateTimeFormatter nodateformatter;
    
    public void Loop() {

        while (bStop == 0) {
            try {
                if (lPeriod > 0) {
                    //lIniSysTimeMs = System.currentTimeMillis();
                    lDelay = lPeriod - (System.currentTimeMillis() % lPeriod);
                    Thread.sleep(lDelay);
                    //System.out.println("Slept " + lDelay + " out of " + lPeriod + " (" + (System.currentTimeMillis() % lPeriod) + ")");
                } else {
                    Thread.sleep(1000);
                }
                if (lMemSysTimeMs == 0) {
                    lMemSysTimeMs = System.currentTimeMillis();
                }

                lIniSysTimeMs = System.currentTimeMillis();
                ldt = lIniSysTimeMs - lMemSysTimeMs;
                lMemSysTimeMs = lIniSysTimeMs;
                
                     
                dCrtDate = new Date();
                SimpleDateFormat sdf = new SimpleDateFormat("YYYYMMdd");

                if (dLastDate != null) {
                    if (dLastDate.getDay() != dCrtDate.getDay()) {
                        // if (dLastDate.getMinutes() != dCrtDate.getMinutes()) {
                        try {
                            bw.close();
                        } catch (Exception ex) {
                        }
                        bw = null;

                    }
                }
                dLastDate = dCrtDate;

                if (bw == null) {
                    sCrtFileName = "GSCDebug" + "-" + sdf.format(dCrtDate) + ".txt";
                    bw = new BufferedWriter(new FileWriter(sCrtFileName, true));
                }
                
                
                Read();
                String frame;
                nextMessage();
                do {
                    frame = nextFrame();
                    if(frame.length() > 0) {
                        ProcessFrame(frame);
                    }
                } while(frame.length() > 0);
                DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .appendPattern("HH:mm:ss")
                    .appendPattern(":0SSSSSS")
                    // create formatter
                    .toFormatter();
                nodateformatter = formatter;
                DateTimeFormatter propFormat = new DateTimeFormatterBuilder()
                    .append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral(" ")
                    .appendPattern("HH:mm:ss")
                    .appendPattern(":SSSSSS")
                    // create formatter
                    .toFormatter();
                DateTimeFormatter propFormat2 = new DateTimeFormatterBuilder()
                    .appendPattern("HH:mm:ss")
                    .appendPattern(":SSSSSS")
                    // create formatter
                    .toFormatter();
                for (BufferizedRecord br : bufrecords) {
                    try {
                        String sbuf = pDataSet.getProperty(br.source, "");
                        if(sbuf.equals("")) {
                            continue;
                        }
                        int wrsize = 0;
                        JsonReader jsonReader = Json.createReader(new StringReader(sbuf));
                        JsonArray array = jsonReader.readArray();
                        
                        JsonArray tsv = null;
                        if(!br.tsvec.equals("")) {
                            String tsbuf = pDataSet.getProperty(br.tsvec, "");
                            if(!tsbuf.equals("")) {
                                jsonReader = Json.createReader(new StringReader(tsbuf));
                                tsv = jsonReader.readArray();
                            }
                        }
                        
                        String sbufint = pDataSet.getProperty("/INTERNALBUF" + br.source, "[]");
                        //ArrayList<Record> rcrds = new ArrayList();
                        JsonArrayBuilder jsb = Json.createArrayBuilder();
                        try {
                            JsonReader jsrd2 = Json.createReader(new StringReader(sbufint));
                            JsonArray bufarr = jsrd2.readArray();
                            for(int i = 0; i < bufarr.size(); i++) {
                                JsonArray rd = bufarr.getJsonArray(i);
                                jsb.add(rd);
                                /*Record crd = new Record();
                                crd.value = rd.getJsonNumber(0).doubleValue();
                                crd.timestamp = rd.getString(1);
                                crd.ts_SMX = rd.getString(2);*/
                            }
                            wrsize += bufarr.size();
                        } catch(Exception e) {
                        }

                        String timestamp = pDataSet.getProperty(br.timestamp, "");
                        LocalTime time;
                        try {
                            time = LocalTime.parse(timestamp, formatter);
                        } catch(Exception e) {
                            try {
                                int secadd = timestamp.charAt(9) - '0';
                                String tscorrected = timestamp.substring(0, 9).concat("0").concat(timestamp.substring(10));
                                time = LocalTime.parse(tscorrected, formatter);
                                time = time.plusSeconds(secadd);
                            } catch(Exception e2) {
                                time = lastlt.plusNanos(lPeriod * 1000000);
                            }
                        }
                        lastlt = time;
                        LocalDateTime ts_smxdt = LocalDateTime.now();
                        String ts_smx = ts_smxdt.format(propFormat);
                        //System.out.println("Pre " + time.format(formatter));
                        /*long micros = time.getNano();
                        time = time.minusNanos(micros);
                        time = time.plusNanos(micros * 1000);*/
                        //time = time.minusNanos(br.tsperiod * 1000000);
                        //System.out.println(time.format(formatter));
                        int secrem = 0;
                        long lastusec;
                        try {
                            lastusec = tsv.getInt(tsv.size() - 1);
                        } catch(Exception e) {
                            lastusec = 0;
                        }
                        JsonArrayBuilder jsbtemp = Json.createArrayBuilder();
                        for(int i = array.size() - 1; i >= 0; i--) {
                            //Record crd = new Record();
                            JsonArrayBuilder arrc = Json.createArrayBuilder();
                            //crd.value = 
                            arrc.add(array.getJsonNumber(i).doubleValue());
                            LocalTime arranged;
                            if(tsv == null) {
                                arranged = time.minusNanos((long) ((array.size()) - i) * br.tsperiod * 1000000l);
                            } else {
                                if(lastusec < tsv.getInt(i)) {
                                    secrem++;
                                }
                                arranged = time.minusNanos(time.getNano()).plusNanos(tsv.getInt(i) * 1000).minusSeconds(secrem);
                                lastusec = tsv.getInt(i);
                            }
                            //crd.timestamp = 
                            arrc.add(arranged.format(propFormat2));
                            //crd.ts_SMX = 
                            arrc.add(ts_smx);
                            jsbtemp.add(arrc.build());
                        }
                        int lenrev = array.size();
                        JsonArray jsbt = jsbtemp.build();
                        while(lenrev != 0) {
                            jsb.add(jsbt.getJsonArray(lenrev - 1));
                            lenrev--;
                        }
                        wrsize += array.size();
                        while(wrsize > br.maxBufSize) {
                            jsb.remove(0);
                            wrsize--;
                        }
                        String jsonString;
                        try(Writer writer = new StringWriter()) {
                            Json.createWriter(writer).write(jsb.build());
                            jsonString = writer.toString();
                        }
                        pDataSet.put("/INTERNALBUF" + br.source, jsonString);
                        
                    } catch (Exception e) {
                        //if (Debug == 1) {
                            System.out.println(e.getMessage() + "\n" + pDataSet.getProperty(br.source, "") + "\n" + pDataSet.getProperty(br.tsvec, ""));
                            e.printStackTrace();
                        //}
                    }
                }

            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage() + "\n" + e.getStackTrace().toString());
                }
            }

        }
    }



    public int Pause = 0;
    public int memPause = 0;
    public int bStop = 0;

    public int Debug = 0;

    public long lPeriod = 0;
    public long lIniSysTimeMs = 0;
    public long lMemSysTimeMs = 0;
    public long ldt = 0;
    public double ddt = 0.0;
    public long lDelay = 0;


    
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
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            String sStackTrace = sw.toString();
            logger.warning(ex.getMessage() + "\n Stacktrace: " + sStackTrace);
        }
        return jso;
    }
}
