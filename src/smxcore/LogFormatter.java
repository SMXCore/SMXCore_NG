/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package smxcore;

/**
 *
 * @author vlad
 */

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {
    // this method is called for every log record
    public String format(LogRecord rec) {
        StringBuffer buf = new StringBuffer(1000);
        buf.append(calcDate(rec.getMillis()));
        buf.append("\t");
        buf.append(rec.getLevel());
        buf.append("\t");
        buf.append(rec.getLoggerName());
        buf.append("\t");
        
        buf.append(formatMessage(rec));
        buf.append("\r\n");

        return buf.toString();
    }

    private String calcDate(long millisecs) {
        SimpleDateFormat date_format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss:uu:SSS");
        Date resultdate = new Date(millisecs);
        return date_format.format(resultdate);
    }

    // this method is called just after the handler using this
    // formatter is created
    public String getHead(Handler h) {
        return "Date\tLevel\tSource\tMessage";
      }

    // this method is called just after the handler using this
    // formatter is closed
    public String getTail(Handler h) {
        return "";
    }
}