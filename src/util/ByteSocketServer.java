/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

/**
 *
 * @author cristi
 */
public class ByteSocketServer {

    Properties pAttributes = new Properties();

    public void setAttributes(Properties pSetAttributes) {
        pAttributes = pSetAttributes;
    }

    Thread tSocketServe = null;

    public void Start() {
        try {
            tSocketServe = new Thread(new Runnable() {
                @Override
                public void run() {
                    SocketServe();
                }
            });
            tSocketServe.start();

        } catch (Exception e) {
        }
    }

    public void Initialize() {
        iSSBPort = PropUtil.GetInt(pAttributes, "iSSBPort", 0);
        iSSBCheckPeriod = PropUtil.GetInt(pAttributes, "iSSBCheckPeriod", 10);
        iSSBReadTimeout = PropUtil.GetInt(pAttributes, "iSSBReadTimeout", 2000);
    }

    public ServerSocket ss = null;
    int iSSBPort = 0;
    Socket s = null;
    InputStream is = null;
    OutputStream os = null;

    byte[] bbRec = new byte[1024];
    byte[] bRec = new byte[1024];
    byte[] bSend = new byte[1024];

    int iRead;
    String sRead;
    int iWrite;
    int iSSBReadTimeout = 2000;
    int iSSBCheckPeriod = 10;
    int iConnTimeOut = 2000;
    int iReqPause = 100;
    int iDebug = 0;

    int bStop = 0;

    private void SocketServe() {
        while (bStop == 0) {

            try {
                Thread.sleep(1);
                Thread.sleep(iSSBCheckPeriod);

                // if ((ss == null) || (!ss.isBound()) || (ss.isClosed()) || //|| (!sComm.isConnected()) || (sComm.isInputShutdown()) || (sComm.isOutputShutdown())
                //         (s == null) || (s.isInputShutdown()) || (s.isOutputShutdown()) || (s.isClosed())) {//|| (iSuccesivErr > iMaxSuccErr)
                //     ReOpen();
                // }
                iRead = is.read(bbRec);
                if (iRead < 0) {
                    ReOpen();
                }
                if (iRead > 0) {

                    sRead = ByteArr2String(bbRec, 0, iRead);
                }

            } catch (Exception e) {
                ReOpen();
            }
        }
    }

    private String ByteArr2String(byte[] ba, int iStart, int iLen) {
        String sRet = "";

        try {

            sRet = ConvertUtil.ByteStr2HexStr(ba, iStart, iLen);

            System.out.println(sRet);

        } catch (Exception e) {
        }

        return sRet;
    }

    String sRet;

    public String ReadLastMessage() {
        sRet = sRead;
        sRead = "";
        return sRet;
    }

    public void SocketWrite(String sWrite) {
        try {
            os.write(ConvertUtil.HexStr2ByteStr(sWrite));
        } catch (Exception e) {
            ReOpen();
        }
    }

    public void CloseServer() {
        try {
            if (ss != null) {
                ss.close();
                ss = null;
            }
        } catch (Exception e) {
        }
        ss = null;
    }

    public void ClosePort() {
        try {
            if (s != null) {
                is.close();
                os.close();
                s.close();
            }
        } catch (Exception e) {
        }
        is = null;
        os = null;
        s = null;

    }

    public void ReOpen() {
        try {
            ClosePort();

            if (ss == null) {
                try {
                    ss = new ServerSocket(iSSBPort);
                    ss.setReuseAddress(true);
                    ss.setSoTimeout(iConnTimeOut);

                } catch (Exception e) {
                }
            }

            s = ss.accept();
            s.setSoTimeout(iSSBReadTimeout);
            is = s.getInputStream();
            os = s.getOutputStream();

        } catch (Exception e) {

            try {
                CloseServer();
                Thread.sleep(10);
            } catch (Exception ex) {
            }

            if (iDebug == 1) {
                System.out.println(e.getMessage());
            }
        }
    }

}
