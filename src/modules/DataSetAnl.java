/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collector;
import java.util.stream.Stream;
import util.PropUtil;

/**
 *
 * @author vlad
 */

public class DataSetAnl extends Module {
    Properties pDataSet = null; // The data set of the real time database
    public long lPeriod = 60;
    Thread tLoop = null; // The thread that we are running on
    PrintWriter file;
    String inputFile = "commands.txt";
    String lastDate = new String();
    List<String> commandList = new ArrayList<String>();

    Date refreshFile() {
        DateFormat df = new SimpleDateFormat("yyyyMMdd");
        Date date = new Date();
        if(!df.format(date).equals(lastDate)) {
            try {
                DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd.");
                FileWriter fw = new FileWriter(/*"/home/pi/" + */dateFormat.format(date) + "DataSetAnl.txt", true);
                BufferedWriter bw = new BufferedWriter(fw);
                file = new PrintWriter(bw);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return date;
    }
    
    void printToFile(String s) {
        DateFormat df = new SimpleDateFormat("yyyy/MM/dd\tHH:mm:ss:SSS\t");
        Date date = refreshFile();
        file.println("------------------------------------------------------");
        file.println(df.format(date) + s);
        file.flush();
    }
    
    void readCommandFile(String filename) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filename), Charset.defaultCharset());
            commandList.addAll(lines);
            Files.delete(Paths.get(filename));
        } catch(Exception e) {
        }
    }
    
    void processCommand(String line) {
        try {
            String[] args = line.split(" ");
            if(args[0].equals("listAll")) {
                printToFile("DataSet1: ");
                list(pDataSet, file);
                file.flush();
            } else if(args[0].equals("reload")) {
//                System.out.println("Reloading " + args[1]);
                Module m = (Module) mmManager.getModuleDebug(args[1]);
                if(args.length > 2) {
//                    System.out.println("From " + args[2]);
                    PropUtil.LoadFromFile(m.pAttributes, args[2]);
//                    System.out.println("Success ");
                } else {
//                    System.out.println("From " + m.sAttributesFile);
                    PropUtil.LoadFromFile(m.pAttributes,  m.sAttributesFile);
//                    System.out.println("Success ");
                }
                printToFile("Reload success (no exception thrown)");
                m.bReinitialize = true;
            }
        } catch(Exception e) {
            e.printStackTrace(file);
        }
    }
    
    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData("DataSet1");
        refreshFile();
    }
    
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
    public void Loop() {
        while (true) {
            try {
                refreshFile();
                readCommandFile(inputFile);
                for(Iterator<String> i = commandList.iterator(); i.hasNext();) {
                    processCommand(i.next());
                }
                commandList = new ArrayList<String>();
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        }
    }
    public void list(Properties prop, PrintWriter out) {
        for (Enumeration<?> e = prop.propertyNames() ; e.hasMoreElements() ;) {
            String key = (String) e.nextElement();
            String val = prop.getProperty(key);
            if (val.length() > 255) {
                val = val.substring(0, 252) + "...";
            }
            out.println(key + "=" + val);
        }
    }
}
