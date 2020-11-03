/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

/**
 *
 * @author cristi
 */
public class FileUtil {

    public static void SaveToFile(String sFilePath, String sContent) {
        BufferedWriter output = null;
        try {
            output = new BufferedWriter(new FileWriter(sFilePath, true));
            output.write(sContent);
            output.flush();
            output.close();

        } catch (Exception e) {
        }
    }

    public static String LoadFromFile(String sFilePath) {
        StringBuffer sbResult = new StringBuffer();
        BufferedReader brFile = null;
        String sLineSep = System.getProperty("line.separator");
        String sLine;
        try {
            brFile = new BufferedReader(new FileReader(sFilePath));

            while ((sLine = brFile.readLine()) != null) {
                sbResult.append(sLine);
                sbResult.append(sLineSep);
            }
            brFile.close();

        } catch (Exception e) {
        }
        return sbResult.toString();
    }
}
