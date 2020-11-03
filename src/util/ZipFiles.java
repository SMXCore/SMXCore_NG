/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;
import java.nio.file.Path;

import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 *
 * @author cristi
 */
public class ZipFiles extends Thread {

    private static ZipFiles instance = null;

    protected ZipFiles() {
        // Exists only to defeat instantiation.
    }

    public static ZipFiles getInstance() {
        if (instance == null) {
            instance = new ZipFiles();
            instance.start();
        }
        return instance;
    }

    public Vector<String> FileList = new Vector();
    String sSep = "\n";

    public void Compress(String sFile, String sZipFile) {
        Compress(sFile, sZipFile, "none");
    }

    public void Compress(String sFile, String sZipFile, String sCmd) {
        FileList.add(sFile + sSep + sZipFile + sSep + sCmd);
    }

    boolean bRun = true;
    int iPause = 1000;
    String[] sFiles = null;
    Path path;
    //File f = null;

    @Override
    public void run() {
        while (bRun) {
            try {
                Thread.sleep(iPause);
                if (!FileList.isEmpty()) {
                    sFiles = FileList.get(0).split(sSep);
                    FileList.remove(0);
                    zipFile(sFiles[0], sFiles[1], sFiles[2]);
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

    }

    public static void zipFile(String finputFile, String zipFilePath, String sCmd) {
        try {

            File inputFile = new File(finputFile);
            if (!inputFile.canRead()) {
                return;
            }

            // Wrap a FileOutputStream around a ZipOutputStream
            // to store the zip stream to a file. Note that this is
            // not absolutely necessary
            FileOutputStream fileOutputStream = new FileOutputStream(zipFilePath);
            ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);

            // a ZipEntry represents a file entry in the zip archive
            // We name the ZipEntry after the original file's name
            ZipEntry zipEntry = new ZipEntry(inputFile.getName());
            zipOutputStream.putNextEntry(zipEntry);

            FileInputStream fileInputStream = new FileInputStream(inputFile);
            //InputStream is = Files.newInputStream(inputFile.toPath(), StandardOpenOption.READ);

            byte[] buf = new byte[8192];
            int bytesRead;

            // Read the input file by chucks of 1024 bytes
            // and write the read bytes to the zip stream
            while ((bytesRead = fileInputStream.read(buf)) > 0) {
                zipOutputStream.write(buf, 0, bytesRead);
            }

            // close ZipEntry to store the stream to the file
            zipOutputStream.closeEntry();

            fileInputStream.close();
            //is.close();

            zipOutputStream.close();
            fileOutputStream.close();

            if ("move".equals(sCmd)) {
                if (inputFile.delete()) {
                    //   System.out.println(" file :" + inputFile.getCanonicalPath() + " deleted :");
                } else {
                    //   System.out.println(" file :" + inputFile.getCanonicalPath() + " not deleted :");
                }

            }

            // System.out.println("Regular file :" + inputFile.getCanonicalPath() + " is zipped to archive :" + zipFilePath);
        } catch (IOException e) {
            // e.printStackTrace();
        }

    }

    public static String GetZipFileContent(String zipFilePath) {
        String sResult = "";

        try {
            FileInputStream fileInputStream = new FileInputStream(zipFilePath);
            ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);
            ZipEntry zipEntry;// = zipInputStream.getNextEntry();
            byte[] buf = new byte[8192];
            int iCount = 0;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                while ((iCount = zipInputStream.read(buf, 0, 8192))
                        != -1) {
                    sResult += new String(buf, 0, iCount);
                }
            }
            zipInputStream.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        return sResult;
    }

}
