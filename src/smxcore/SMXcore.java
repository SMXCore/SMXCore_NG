/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package smxcore;

import java.io.File;
import java.util.Properties;
import modules.ModulesManager;
import util.FileUtil;
import util.PropUtil;

/**
 *
 * @author cristi
 */
public class SMXcore {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        try {
            String sModulesFile = "Modules.txt";
            if (args.length > 0) {
                sModulesFile = args[0];
            }
            ModulesManager mmManager = new ModulesManager();

            Properties pAttributes = new Properties();
            PropUtil.LoadFromFile(pAttributes, sModulesFile);
            mmManager.setAttributes(pAttributes);
            mmManager.LoadModules();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
