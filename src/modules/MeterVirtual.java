/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package modules;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import util.PropUtil;

/**
 *
 * @author cristi
 */
public class MeterVirtual extends Module {

    @Override
    public void Initialize() {
        pDataSet = mmManager.getSharedData(PropUtil.GetString(pAttributes, "pDataSet", sName));
        sPrefix = PropUtil.GetString(pAttributes, "sPrefix", "");
        lPeriod = PropUtil.GetInt(pAttributes, "lPeriod", 1000);
        //ReadIniVals();
        //Calc();
        //WriteVals();
    }
    Properties pDataSet = null;
    String sPrefix = "";
    Thread tMeterCalc = null;

    @Override
    public void Start() {
        try {
            tMeterCalc = new Thread(new Runnable() {

                @Override
                public void run() {
                    Calculate();
                }
            });
            tMeterCalc.start();

        } catch (Exception e) {

        }
    }

    public void Calculate() {

        while (bStop == 0) {
            try {
                if (lPeriod > 0) {
                    //lIniSysTimeMs = System.currentTimeMillis();
                    lDelay = lPeriod - (System.currentTimeMillis() % lPeriod);
                    Thread.sleep(lDelay);
                } else {
                    Thread.sleep(10);
                }
                if (lMemSysTimeMs == 0) {
                    lMemSysTimeMs = System.currentTimeMillis();
                    ReadIniVals();
                    Calc();
                    WritePrimaryVals();
                    WriteVals();
                    continue;
                }

                lIniSysTimeMs = System.currentTimeMillis();
                ldt = lIniSysTimeMs - lMemSysTimeMs;
                lMemSysTimeMs = lIniSysTimeMs;
                ddt = (double) ldt / 1000.0 / 3600.0;

                if (Pause == 1) {
                    if (memPause == 0) {
                        memPause = Pause;
                        lMemSysTimeMs = 0;
                    }
                    continue;
                }
                memPause = Pause;

                if (!(RndPrc > 0)) {
                    ReadVals();
                }

                if (RndPrc > 0) {
                    RndCalc();
                    RndVarUpdate();
                }

                Calc();
                WriteVals();

            } catch (Exception e) {
                if (Debug == 1) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }

    private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss SSS");
    private final SimpleDateFormat sdfDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

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

    String sDate = "";
    double U1, U2, U3, I1, I2, I3, PF1, PF2, PF3, qPF1, qPF2, qPF3;
    double P1, P2, P3, P, Q1, Q2, Q3, Q, f, PF, Ap, Am, Rp, Rm, Rip, Rim, Rcp, Rcm;
    double RndPrc;

    public void Calc() {

        try {
            sDate = sdfDate.format(new Date());

            qPF1 = Math.sqrt(1 - PF1 * PF1);
            qPF2 = Math.sqrt(1 - PF2 * PF2);
            qPF3 = Math.sqrt(1 - PF3 * PF3);

            P1 = U1 * I1 * PF1 / 1000;
            P2 = U2 * I2 * PF2 / 1000;
            P3 = U3 * I3 * PF3 / 1000;

            Q1 = U1 * I1 * qPF1 / 1000;
            Q2 = U2 * I2 * qPF2 / 1000;
            Q3 = U3 * I3 * qPF3 / 1000;

            P = P1 + P2 + P3;
            Q = Q1 + Q2 + Q3;

            if (P1 > 0) {
                Ap += P1 * ddt;
            } else {
                Am += P1 * ddt;
            }
            if (P2 > 0) {
                Ap += P2 * ddt;
            } else {
                Am += P2 * ddt;
            }
            if (P3 > 0) {
                Ap += P3 * ddt;
            } else {
                Am += P3 * ddt;
            }

            Rm += Q * ddt;

        } catch (Exception e) {
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }

    public void RndCalc() {
        dRndAct = (Math.random() - 0.4999999) * RndPrc / 100;
        //System.out.println(Double.toString(dRndAct));
    }

    double dRndAct = 0.0;

    public double RndChange(double dval) {
        dval = dval - dval * dRndAct;
        return dval;
    }

    public void RndVarUpdate() {
        U1 = RndChange(U1);
        U2 = RndChange(U2);
        U3 = RndChange(U3);

        I1 = RndChange(I1);
        I2 = RndChange(I2);
        I3 = RndChange(I3);

        f = RndChange(f);

    }

    public void ReadVals() {

        try {

            U1 = PropUtil.GetDouble(pDataSet, sPrefix + "U1", 0.00);
            U2 = PropUtil.GetDouble(pDataSet, sPrefix + "U2", 0.00);
            U3 = PropUtil.GetDouble(pDataSet, sPrefix + "U3", 0.00);

            I1 = PropUtil.GetDouble(pDataSet, sPrefix + "I1", 0.00);
            I2 = PropUtil.GetDouble(pDataSet, sPrefix + "I2", 0.00);
            I3 = PropUtil.GetDouble(pDataSet, sPrefix + "I3", 0.00);

            PF1 = PropUtil.GetDouble(pDataSet, sPrefix + "PF1", 0.00);
            PF2 = PropUtil.GetDouble(pDataSet, sPrefix + "PF2", 0.00);
            PF3 = PropUtil.GetDouble(pDataSet, sPrefix + "PF3", 0.00);

            // RndPrc = PropUtil.GetDouble(pAttributes, "RndPrc", 1.00);
        } catch (Exception e) {

            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }

    public void ReadIniVals() {

        try {
            U1 = PropUtil.GetDouble(pAttributes, "U1", 0.00);
            U2 = PropUtil.GetDouble(pAttributes, "U2", 0.00);
            U3 = PropUtil.GetDouble(pAttributes, "U3", 0.00);

            I1 = PropUtil.GetDouble(pAttributes, "I1", 0.00);
            I2 = PropUtil.GetDouble(pAttributes, "I2", 0.00);
            I3 = PropUtil.GetDouble(pAttributes, "I3", 0.00);

            PF1 = PropUtil.GetDouble(pAttributes, "PF1", 0.00);
            PF2 = PropUtil.GetDouble(pAttributes, "PF2", 0.00);
            PF3 = PropUtil.GetDouble(pAttributes, "PF3", 0.00);

            RndPrc = PropUtil.GetDouble(pAttributes, "RndPrc", 0.1);

            DecU = PropUtil.GetInt(pAttributes, "DecU", 2);
            DecI = PropUtil.GetInt(pAttributes, "DecI", 2);
            DecPF = PropUtil.GetInt(pAttributes, "DecPF", 2);
            Decf = PropUtil.GetInt(pAttributes, "Decf", 2);
            DecPQ = PropUtil.GetInt(pAttributes, "DecPQ", 2);
            DecAR = PropUtil.GetInt(pAttributes, "DecAR", 2);
            PF = PropUtil.GetDouble(pAttributes, "PF", 0.00);

            f = PropUtil.GetDouble(pAttributes, "f", 0.00);

            Ap = PropUtil.GetDouble(pAttributes, "Ap", 0.00);
            Am = PropUtil.GetDouble(pAttributes, "Am", 0.00);
            Rp = PropUtil.GetDouble(pAttributes, "Rp", 0.00);
            Rm = PropUtil.GetDouble(pAttributes, "Rm", 0.00);

        } catch (Exception e) {
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }
    int DecU, DecI, DecPF, Decf, DecPQ, DecAR;

    public void WritePrimaryVals() {
        try {
            pDataSet.put(sPrefix + "U1", Double2DecPointStr(U1, DecU));
            pDataSet.put(sPrefix + "U2", Double2DecPointStr(U2, DecU));
            pDataSet.put(sPrefix + "U3", Double2DecPointStr(U3, DecU));

            pDataSet.put(sPrefix + "I1", Double2DecPointStr(I1, DecI));
            pDataSet.put(sPrefix + "I2", Double2DecPointStr(I2, DecI));
            pDataSet.put(sPrefix + "I3", Double2DecPointStr(I3, DecI));

            pDataSet.put(sPrefix + "PF1", Double2DecPointStr(PF1, DecPF));
            pDataSet.put(sPrefix + "PF2", Double2DecPointStr(PF2, DecPF));
            pDataSet.put(sPrefix + "PF3", Double2DecPointStr(PF3, DecPF));
            pDataSet.put(sPrefix + "PF", Double2DecPointStr(PF, DecPF));

            pDataSet.put(sPrefix + "f", Double2DecPointStr(f, DecPF));

            // Timestamp
            pDataSet.put(sPrefix + "U1-ts", sDate);
            pDataSet.put(sPrefix + "U2-ts", sDate);
            pDataSet.put(sPrefix + "U3-ts", sDate);

            pDataSet.put(sPrefix + "I1-ts", sDate);
            pDataSet.put(sPrefix + "I2-ts", sDate);
            pDataSet.put(sPrefix + "I3-ts", sDate);

            pDataSet.put(sPrefix + "PF1-ts", sDate);
            pDataSet.put(sPrefix + "PF2-ts", sDate);
            pDataSet.put(sPrefix + "PF3-ts", sDate);
            pDataSet.put(sPrefix + "PF-ts", sDate);

            pDataSet.put(sPrefix + "f-ts", sDate);
        } catch (Exception e) {
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }

    public void WriteVals() {

        try {
            pDataSet.put(sPrefix + "Date", sDate);

            pDataSet.put(sPrefix + "Date-ts", sDate);

            if (RndPrc > 0) {
                WritePrimaryVals();

            }
            pDataSet.put(sPrefix + "P", Double2DecPointStr(P, DecPQ));
            pDataSet.put(sPrefix + "P1", Double2DecPointStr(P1, DecPQ));
            pDataSet.put(sPrefix + "P2", Double2DecPointStr(P2, DecPQ));
            pDataSet.put(sPrefix + "P3", Double2DecPointStr(P3, DecPQ));

            pDataSet.put(sPrefix + "Q", Double2DecPointStr(Q, DecPQ));
            pDataSet.put(sPrefix + "Q1", Double2DecPointStr(Q1, DecPQ));
            pDataSet.put(sPrefix + "Q2", Double2DecPointStr(Q2, DecPQ));
            pDataSet.put(sPrefix + "Q3", Double2DecPointStr(Q3, DecPQ));

            pDataSet.put(sPrefix + "Ap", Double2DecPointStr(Ap, DecAR));
            pDataSet.put(sPrefix + "Am", Double2DecPointStr(Am, DecAR));
            pDataSet.put(sPrefix + "Rp", Double2DecPointStr(Rp, DecAR));
            pDataSet.put(sPrefix + "Rm", Double2DecPointStr(Rm, DecAR));

            // Timestamp
            pDataSet.put(sPrefix + "P-ts", sDate);
            pDataSet.put(sPrefix + "P1-ts", sDate);
            pDataSet.put(sPrefix + "P2-ts", sDate);
            pDataSet.put(sPrefix + "P3-ts", sDate);

            pDataSet.put(sPrefix + "Q-ts", sDate);
            pDataSet.put(sPrefix + "Q1-ts", sDate);
            pDataSet.put(sPrefix + "Q2-ts", sDate);
            pDataSet.put(sPrefix + "Q3-ts", sDate);

            pDataSet.put(sPrefix + "Ap-ts", sDate);
            pDataSet.put(sPrefix + "Am-ts", sDate);
            pDataSet.put(sPrefix + "Rp-ts", sDate);
            pDataSet.put(sPrefix + "Rm-ts", sDate);

        } catch (Exception e) {
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }

    public String Double2DecPointStr(double dVal, int iDecimals) {
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
            if (Debug == 1) {
                System.out.println(e.getMessage());
            }
        }
        return sResult;
    }

}
