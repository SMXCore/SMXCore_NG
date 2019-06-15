/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules.contrib;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import util.PropUtil;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Enumeration;
import modules.Module;


/**
 *
 * @author mihai
 */
public class S4G_LESSAg extends Module {

    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        PropUtil.LoadFromFile(pAssociation, PropUtil.GetString(pAttributes, "pAssociation", ""));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);

        //insert in database metadata related to the Modbus module 
        String sTSDate;
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        sTSDate = sdf.format(new Date());
        pDataSet.put("Module/S4G_LESSAg/"+sName+"/StartDateTime", sTSDate); // DateTime
        
        // initialize UniRCon varaibales
        E_bat_max = E_bat_nominal * E_bat_max_proc /  100;
        E_bat_min = E_bat_nominal * E_bat_min_proc /  100;
        Battery_efficiency = Battery_charging_efficiency * Battery_discharging_efficiency;
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

    // variables related to consumption
    double P_CONS; // Consumption power 
    double P_CONS_peak = 4000; // Peak consumption power 
    double P_Cons_SeTpoint = 2000; // Peak consumption which, if overpassed, it is used also the support of battery discharge
    
    // variables related to PV production
    double P_PV_meteo; // PV power based on enviromental condition (meteo)
    double P_PV_peak = -6000; // Peak PV power (Wp)
    double P_PV_curtail=0; // PV power to be curtailed
    double P_PV_after_curtail; // PV power after curtail = P_PV_meteo - P_PV_curtail
    
    double P_PCC;
    
    // Variables related to battery
    double P_BAT_real; // Active Power exchanged with the battery; this is equal with P_BAT_cd_setpoint unless there are constrainst related to real possible power
    double P_BAT_cd_setpoint=0; // Active power setpoint for exchange with the battery;  
    double P_BAT_max_inverters=1000; // W
    double E_bat_nominal=4; // Wh, energy stored in the battery, calculated by integrating P_BAT_real and considering also efficiency factors (Battery_charging_efficiency,Battery_discharging_efficiency)
    double E_bat_max=2; // Wh, maximum allowed energy in battery, may be lower than E_bat_max
    double E_bat_max_proc=100; // [%] of nominal energy
    double E_bat_min=200; // Wh
    double E_bat_min_proc=10; //  [%] of nominal energy
    double E_bat=0.4; // Wh, Initial value of the energy in the battery 
    double SoC = E_bat/ E_bat_max; // State of charge (battery)
    double Battery_charging_efficiency = 0.95;
    double Battery_discharging_efficiency = 0.97;
    double Battery_efficiency = 0.5;
    int battery_mode = 0; // 0= idle; 1 = charging; 2 = discharging
    // Separate energy measuremenst for charging and discharging, to be able to put in value the losses
    double Meter_E_BAT_real_charging=0; // Wh
    double Meter_E_BAT_real_discharging=0; // Wh
    
    public void Load_Cons_and_PV() {
                P_PV_meteo = Double.parseDouble((String) pDataSet.get(sPrefix + "SMX/Values/P_PV/value"));
                P_CONS = Double.parseDouble((String) pDataSet.get(sPrefix + "SMX/Values/P_CONS/value"));
                
                // simulating consumption
                if(counter<=50) P_CONS= P_CONS -40; else P_CONS= P_CONS + 50; 
                if(P_CONS<0) P_CONS=0;
                if(P_CONS>P_CONS_peak) P_CONS = P_CONS_peak;
                
                // simulating PV production
                if(counter<=70) P_PV_meteo = P_PV_meteo - 30; else P_PV_meteo = P_PV_meteo + 45; 
                if(P_PV_meteo<P_PV_peak) P_PV_meteo = P_PV_peak;
                if(P_PV_meteo>0) P_PV_meteo = 0;

                pDataSet.put(sPrefix+"SMX/Values/P_PV/value", Double2DecPointStr(P_PV_meteo,2));
                pDataSet.put(sPrefix+"SMX/Values/P_CONS/value", Double2DecPointStr(P_CONS,2));
                
    }
    public void Battery_command() {
        // Simulation of the battery behavior
        if(E_bat>=E_bat_max) if(P_BAT_cd_setpoint>0) P_BAT_real = 0;
        double E_plus_max; // Maximum energy quantity possible to be introduced in the battery in the next timey step, in charging mode
        double E_minus_max; // Maximum energy quantity possible to be extracted from the battery in the next timey step, in discharging mode
        
        if(P_BAT_cd_setpoint>=0) {
            // We check that the new setpoint, if positive (consumption/chraging mode), will not exceed the total energy of the battery
            if(P_BAT_cd_setpoint < P_BAT_real) E_plus_max = P_BAT_real * 1/3600; else E_plus_max = P_BAT_cd_setpoint * 1/3600;

            if(E_bat<E_bat_max-E_plus_max){
                P_BAT_real = P_BAT_cd_setpoint;
                battery_mode = 1; // charging mode
            } // simulare ER, partea de baterie
            else { 
                P_BAT_real = 0; P_BAT_cd_setpoint = 0; 
                battery_mode = 0; // idle mode
            }
            if(P_BAT_cd_setpoint==0) battery_mode = 0;
        }
        else {
            // P_BAT_cd_setpoint<0, meaning we have an order to discharge/produce energy from battery
            // We check that the new setpoint, if negative (produce/discharge mode), will not go below minimum energy accepted for the the battery
            if(P_BAT_cd_setpoint > P_BAT_real) E_minus_max = P_BAT_real * 1/3600; else E_minus_max = P_BAT_cd_setpoint * 1/3600;

            if(E_bat+E_minus_max>=E_bat_min){
                P_BAT_real = P_BAT_cd_setpoint; // simulare ER, partea de baterie
                battery_mode = 2; // discharging/production mode
            }
            else { 
                P_BAT_real = 0; P_BAT_cd_setpoint = 0; 
                battery_mode = 0; // idle mode
            }
        }
        
        if(P_BAT_real>=0) {
            if (P_BAT_real>P_BAT_max_inverters) P_BAT_real=P_BAT_max_inverters;
        } 
        else {
            if (P_BAT_real<-P_BAT_max_inverters) P_BAT_real=-P_BAT_max_inverters;
        }
        // Battery energy is obtained by integration, considering also effciency for chargind and discharging
        if(P_BAT_real>=0) E_bat = E_bat + P_BAT_real * 1/3600 * Battery_charging_efficiency;
        else E_bat = E_bat + P_BAT_real * 1/3600 * Battery_discharging_efficiency;
        
        SoC = E_bat/ E_bat_max;
        //P_PCC = - P_PV_meteo - P_BAT_real -P_CONS; // simulare retea inetrna prosumer
        
    }

    int counter = 0;
    public void Loop() {

        while (bStop == 0) {
            try { 
                if (lPeriod > 0) {
                    //lIniSysTimeMs = System.currentTimeMillis();
                    lDelay = lPeriod - (System.currentTimeMillis() % lPeriod);
                    Thread.sleep(lDelay);
                } else {
                    Thread.sleep(5000);
                }
                if (lMemSysTimeMs == 0) {
                    lMemSysTimeMs = System.currentTimeMillis();
                }

                lIniSysTimeMs = System.currentTimeMillis();
                ldt = lIniSysTimeMs - lMemSysTimeMs;
                lMemSysTimeMs = lIniSysTimeMs;

                // Here is running the main function
                LoopFunction();
                
                Load_Cons_and_PV();
                //P_CONS = Double.parseDouble((String) pDataSet.get(sPrefix + "SMX/Values/P_CONS/value"));
                P_PV_meteo = Double.parseDouble((String) pDataSet.get(sPrefix + "SMX/Values/P_PV/value"));
                P_CONS = Double.parseDouble((String) pDataSet.get(sPrefix + "SMX/Values/P_CONS/value"));

                P_PCC = Double.parseDouble((String) pDataSet.get(sPrefix + "SMX/Values/P_PCC/value"));
                P_BAT_real = Double.parseDouble((String) pDataSet.get(sPrefix + "SMX/Values/P_BAT/P_real"));
                P_PV_after_curtail = P_PV_meteo - P_PV_curtail;
                
                //P_CONS = -P_PCC - P_PV_meteo - P_BAT_real;
                P_PCC = P_CONS + P_PV_after_curtail + P_BAT_real;
                
                // If P_PCC<0 we solve the problem by changing the BAT setpoint
                if(P_PCC<0) {
                    // We are in injection (production) mode towards DSO, so we need to take AP measures
                    // we try as first measure to increase battery consumption
                    P_BAT_cd_setpoint=P_BAT_real-P_PCC;
                }
                else {
                    // We are in consumption mode from DSO point of view
                    if(P_PV_curtail==0) {
                        // we do not have previous curtailment, so we can eventually command the battery to charge (consume = positive Power)
                        if(P_PCC>P_Cons_SeTpoint) P_BAT_cd_setpoint = P_BAT_cd_setpoint + P_Cons_SeTpoint - P_PCC;
                        else P_BAT_cd_setpoint=0;//P_BAT_real;
                    }
                    else {
                        // we have previous curtail on PV, so we can reduce the curtailment with the value of P_PCC
                        if(P_PV_curtail<0) {
                            if(P_PV_curtail + P_PCC<=0)P_PV_curtail = P_PV_curtail + P_PCC;
                            else P_PV_curtail = 0;
                        }
                        
                    }
                }
                //
                System.out.println("#"+counter+" Before cd: P_CONS="+P_CONS+"\tP_PV_meteo="+P_PV_meteo+"\tP_PV_after_curtail="+P_PV_after_curtail+"\tP_PV_curtail="+P_PV_curtail+"\tP_PCC="+P_PCC
                        +"\tBatMode="+battery_mode+"\tP_BAT_real="+P_BAT_real
                        +"\tP_BAT_cd_setpoint="+P_BAT_cd_setpoint+"\tE_bat="+String.format ("%.3f", E_bat)+"\tSoC="+String.format ("%.4f", SoC)
                        +"\tE_bat_chrg="+String.format ("%.3f", Meter_E_BAT_real_charging)+"\tE_bat_disch="+String.format ("%.3f", Meter_E_BAT_real_discharging));
                // order the battrey to take the new setpoint; however, if battery full, new setpoint might be not considered
                Battery_command();
                // calculate again P_PCC with the P_BAT_real after battery constraints have been considerede in Battery_command
                P_PCC = P_CONS + P_PV_after_curtail + P_BAT_real;
                System.out.println("#"+counter+" Bf.PV crt: P_CONS="+P_CONS+"\tP_PV_meteo="+P_PV_meteo+"\tP_PV_after_curtail="+P_PV_after_curtail+"\tP_PV_curtail="+P_PV_curtail+"\tP_PCC="+P_PCC+"\tBatMode="+battery_mode+"\tP_BAT_real="+P_BAT_real+"\tP_BAT_cd_setpoint="+P_BAT_cd_setpoint+"\tE_bat="+String.format ("%.3f", E_bat)+"\tSoC="+String.format ("%.4f", SoC));
                // If battery could not solve the P_PCC problem, we need to curtail PV:
                if(P_PCC<0) P_PV_curtail = P_PV_curtail + P_PCC; //else P_PV_curtail = 0;
                P_PV_after_curtail = P_PV_meteo - P_PV_curtail;
                // we recalculate P_PCC
                P_PCC = P_CONS + P_PV_after_curtail + P_BAT_real;
                System.out.println("#"+counter+" *After cd: P_CONS="+P_CONS+"\tP_PV_meteo="+P_PV_meteo+"\tP_PV_after_curtail="+P_PV_after_curtail+"\tP_PV_curtail="+P_PV_curtail+"\tP_PCC="+P_PCC+"\tBatMode="+battery_mode+"\tP_BAT_real="+P_BAT_real+"\tP_BAT_cd_setpoint="+P_BAT_cd_setpoint+"\tE_bat="+String.format ("%.3f", E_bat)+"\tSoC="+String.format ("%.4f", SoC));
                System.out.println("-------------------------------------------------------------------------------------");
                
                counter++;
                pDataSet.put(sPrefix+"SMX/Values/P_PCC/value", Double2DecPointStr(P_PCC,2));
                pDataSet.put(sPrefix+"SMX/Values/P_BAT/P_real", Double2DecPointStr(P_BAT_real,2));
                pDataSet.put(sPrefix+"SMX/Values/P_BAT/P_setpoint", Double2DecPointStr(P_BAT_cd_setpoint,2));

                // Metering
                if (P_BAT_real>=0) Meter_E_BAT_real_charging = Meter_E_BAT_real_charging + P_BAT_real * 1 / 3600;
                else Meter_E_BAT_real_discharging = Meter_E_BAT_real_discharging - P_BAT_real * 1 / 3600;
                
            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }

    public void LoopFunction() {
        try {
            
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
    public long ldt = 0;
    public double ddt = 0.0;
    public long lDelay = 0;

    public static String Double2DecPointStr(double dVal, int iDecimals) {
        String sResult = " ";
        int iLen, iNoOfIntDigits;
        try {
            sResult = Double.toString(dVal);
            iLen = sResult.length();
            iNoOfIntDigits = sResult.indexOf(".");
            if (iLen - iNoOfIntDigits - 1 > iDecimals) {
                if (iDecimals < 1) {
                    sResult = sResult.substring(0, iNoOfIntDigits + iDecimals);
                } else {
                    sResult = sResult.substring(0, iNoOfIntDigits + iDecimals + 1);
                }
            }

        } catch (Exception e) {

        }
        return sResult;
    }
}
