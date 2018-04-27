/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gurux;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import gurux.common.GXCommon;
import gurux.common.IGXMedia;
import gurux.common.ReceiveParameters;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDLMSConverter;
import gurux.dlms.GXDLMSException;
import gurux.dlms.GXDateTime;
import gurux.dlms.GXReplyData;
import gurux.dlms.GXSimpleEntry;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.DataType;
import gurux.dlms.enums.ErrorCode;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.enums.RequestTypes;
import gurux.dlms.manufacturersettings.GXManufacturer;
import gurux.dlms.manufacturersettings.GXObisCode;
import gurux.dlms.manufacturersettings.GXServerAddress;
import gurux.dlms.manufacturersettings.HDLCAddressType;
import gurux.dlms.objects.GXDLMSCaptureObject;
import gurux.dlms.objects.GXDLMSDemandRegister;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSObjectCollection;
import gurux.dlms.objects.GXDLMSProfileGeneric;
import gurux.dlms.objects.GXDLMSRegister;
import gurux.dlms.objects.IGXDLMSBase;
import gurux.dlms.objects.enums.SortMethod;
import gurux.io.BaudRate;
import gurux.io.Parity;
import gurux.io.StopBits;
import gurux.net.GXNet;
import gurux.serial.GXSerial;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Hashtable;

/**
 *
 * @author w7user
 */
public class GXCommunicate {

    IGXMedia Media;
    public boolean Trace = false;
    long ConnectionStartTime;
    GXManufacturer manufacturer;
    public GXDLMSClient dlms;
    boolean iec;
    java.nio.ByteBuffer replyBuff;
    int WaitTime = 10000;

    public static void trace(PrintWriter logFile, String text) {
        logFile.write(text);
        System.out.print(text);
    }

    public static void traceLn(PrintWriter logFile, String text) {
        logFile.write(text + "\r\n");
        System.out.print(text + "\r\n");
    }

    public GXCommunicate(int waitTime, gurux.dlms.GXDLMSClient dlms,
            GXManufacturer manufacturer, boolean iec, Authentication auth,
            String pw, IGXMedia media) throws Exception {
        Files.deleteIfExists(Paths.get("trace.txt"));
        Media = media;
        WaitTime = waitTime;
        this.dlms = dlms;
        this.manufacturer = manufacturer;
        this.iec = iec;
        boolean useIec47
                = manufacturer.getUseIEC47() && media instanceof gurux.net.GXNet;
        dlms.setUseLogicalNameReferencing(
                manufacturer.getUseLogicalNameReferencing());
        int value = manufacturer.getAuthentication(auth).getClientAddress();
        dlms.setClientAddress(value);
        GXServerAddress serv = manufacturer.getServer(HDLCAddressType.DEFAULT);
        if (useIec47) {
            dlms.setInterfaceType(InterfaceType.WRAPPER);
        } else {
            dlms.setInterfaceType(InterfaceType.HDLC);
            value = GXDLMSClient.getServerAddress(serv.getLogicalAddress(),
                    serv.getPhysicalAddress());
        }
        dlms.setServerAddress(value);
        dlms.setAuthentication(auth);
        dlms.setPassword(pw.getBytes("ASCII"));
        System.out.println("Authentication: " + auth);
        System.out.println("ClientAddress: 0x"
                + Integer.toHexString(dlms.getClientAddress()));
        System.out.println("ServerAddress: 0x"
                + Integer.toHexString(dlms.getServerAddress()));
        if (dlms.getInterfaceType() == InterfaceType.WRAPPER) {
            replyBuff = java.nio.ByteBuffer.allocate(8 + 1024);
        } else {
            replyBuff = java.nio.ByteBuffer.allocate(100);
        }
    }

    public void close() throws Exception {
        if (Media != null) {
            System.out.println("DisconnectRequest");
            GXReplyData reply = new GXReplyData();
            readDLMSPacket(dlms.disconnectRequest(), reply);
            Media.close();
        }
    }

    public String now() {
        return new SimpleDateFormat("HH:mm:ss.SSS")
                .format(java.util.Calendar.getInstance().getTime());
    }

    public void writeTrace(String line) {
        if (Trace) {
            System.out.println(line);
        }
        PrintWriter logFile = null;
        try {
            logFile = new PrintWriter(
                    new BufferedWriter(new FileWriter("trace.txt", true)));
            logFile.println(line);
        } catch (IOException ex) {
            throw new RuntimeException(ex.getMessage());
        } finally {
            if (logFile != null) {
                logFile.close();
            }
        }
    }

    public void readDLMSPacket(byte[][] data) throws Exception {
        GXReplyData reply = new GXReplyData();
        for (byte[] it : data) {
            reply.clear();
            readDLMSPacket(it, reply);
        }
    }

    /**
     * Read DLMS Data from the device. If access is denied return null.
     */
    public void readDLMSPacket(byte[] data, GXReplyData reply)
            throws Exception {
        if (data == null || data.length == 0) {
            return;
        }
        reply.setError((short) 0);
        Object eop = (byte) 0x7E;
        // In network connection terminator is not used.
        if (dlms.getInterfaceType() == InterfaceType.WRAPPER
                && Media instanceof GXNet) {
            eop = null;
        }
        Integer pos = 0;
        boolean succeeded = false;
        ReceiveParameters<byte[]> p
                = new ReceiveParameters<byte[]>(byte[].class);
        p.setEop(eop);
        p.setCount(5);
        p.setWaitTime(WaitTime);
        synchronized (Media.getSynchronous()) {
            while (!succeeded) {
                writeTrace("DLMS_S01a<- " + now() + "\t" + GXCommon.bytesToHex(data));
                Media.send(data, null);
                if (p.getEop() == null) {
                    p.setCount(1);
                }
                succeeded = Media.receive(p);
                if (!succeeded) {
                    // Try to read again...
                    if (pos++ == 3) {
                        throw new RuntimeException(
                                "Failed to receive reply from the device in given time.");
                    }
                    System.out.println("Data send failed. Try to resend "
                            + pos.toString() + "/3");
                }
            }
            // Loop until whole DLMS packet is received.
            try {
                while (!dlms.getData(p.getReply(), reply)) {
                    if (p.getEop() == null) {
                        p.setCount(1);
                    }
                    if (!Media.receive(p)) {
                        // If echo.
                        if (reply.isEcho()) {
                            Media.send(data, null);
                        }
                        // Try to read again...
                        if (++pos == 3) {
                            throw new Exception(
                                    "Failed to receive reply from the device in given time.");
                        }
                        System.out.println("Data send failed. Try to resend "
                                + pos.toString() + "/3");
                    }
                }
            } catch (Exception e) {
                writeTrace("DLMS_R01-> " + now() + "\t"
                        + GXCommon.bytesToHex(p.getReply()));
                throw e;
            }
        }
        String a1 = GXCommon.bytesToHex(p.getReply());
        writeTrace("DLMS_R02-> " + now() + "\t" + GXCommon.bytesToHex(p.getReply()));
        //writeTrace("DLMS_R02text-> " + now() + "\t" + a1);
        if (reply.getError() != 0) {
            if (reply.getError() == ErrorCode.REJECTED.getValue()) {
                Thread.sleep(1000);
                readDLMSPacket(data, reply);
            } else {
                writeTrace("DLMS_R02_exception-> " + now() + "\t" + GXCommon.bytesToHex(p.getReply()));
                throw new GXDLMSException(reply.getError());
            }
        }
    }

    public void readDataBlock(byte[][] data, GXReplyData reply) throws Exception {
        for (byte[] it : data) {
            reply.clear();
            readDataBlock(it, reply);
        }
    }

    /**
     * Reads next data block.
     *
     * @param data
     * @return
     * @throws Exception
     */
    public void readDataBlock(byte[] data, GXReplyData reply) throws Exception {
        RequestTypes rt;
        if (data.length != 0) {
            readDLMSPacket(data, reply);
            while (reply.isMoreData()) {
                rt = reply.getMoreData();
                data = dlms.receiverReady(rt);
                readDLMSPacket(data, reply);
            }
        }
    }

    /**
     * Initializes connection.
     *
     * @param port
     * @throws InterruptedException
     * @throws Exception
     */
    public void initializeConnection() throws Exception, InterruptedException {
        Media.open();
        if (Media instanceof GXSerial) {
            GXSerial serial = (GXSerial) Media;
            serial.setDtrEnable(true);
            serial.setRtsEnable(true);
            if (iec) {
                ReceiveParameters<byte[]> p
                        = new ReceiveParameters<byte[]>(byte[].class);
                p.setAllData(false);
                p.setEop((byte) '\n');
                p.setWaitTime(WaitTime);
                String data;
                String replyStr;
                synchronized (Media.getSynchronous()) {
                    data = "/?!\r\n";
                    writeTrace("DLMS_S02<- " + now() + "\t"
                            + GXCommon.bytesToHex(data.getBytes("ASCII")));
                    Media.send(data, null);
                    if (!Media.receive(p)) {
                        throw new Exception("Invalid meter type.");
                    }
                    writeTrace("DLMS_R03->" + now() + "\t"
                            + GXCommon.bytesToHex(p.getReply()));
                    // If echo is used.
                    replyStr = new String(p.getReply());
                    if (data.equals(replyStr)) {
                        p.setReply(null);
                        if (!Media.receive(p)) {
                            throw new Exception("Invalid meter type.");
                        }
                        writeTrace("DLMS_R04-> " + now() + "\t"
                                + GXCommon.bytesToHex(p.getReply()));
                        replyStr = new String(p.getReply());
                    }
                }
                if (replyStr.length() == 0 || replyStr.charAt(0) != '/') {
                    throw new Exception("Invalid responce.");
                }
                String manufactureID = replyStr.substring(1, 4);
                if (manufacturer.getIdentification()
                        .compareToIgnoreCase(manufactureID) != 0) {
                    throw new Exception("Manufacturer "
                            + manufacturer.getIdentification()
                            + " expected but " + manufactureID + " found.");
                }
                int bitrate = 0;
                char baudrate = replyStr.charAt(4);
                switch (baudrate) {
                    case '0':
                        bitrate = 300;
                        break;
                    case '1':
                        bitrate = 600;
                        break;
                    case '2':
                        bitrate = 1200;
                        break;
                    case '3':
                        bitrate = 2400;
                        break;
                    case '4':
                        bitrate = 4800;
                        break;
                    case '5':
                        bitrate = 9600;
                        break;
                    case '6':
                        bitrate = 19200;
                        break;
                    default:
                        throw new Exception("Unknown baud rate.");
                }
                System.out.println("Bitrate is : " + bitrate);
                // Send ACK
                // Send Protocol control character
                byte controlCharacter = (byte) '2';// "2" HDLC protocol
                // procedure (Mode E)
                // Send Baudrate character
                // Mode control character
                byte ModeControlCharacter = (byte) '2';// "2" //(HDLC protocol
                // procedure) (Binary
                // mode)
                // Set mode E.
                byte[] tmp = new byte[]{0x06, controlCharacter,
                    (byte) baudrate, ModeControlCharacter, 13, 10};
                p.setReply(null);
                synchronized (Media.getSynchronous()) {
                    Media.send(tmp, null);
                    writeTrace("DLMS_S01b<- " + now() + "\t" + GXCommon.bytesToHex(tmp));
                    p.setWaitTime(100);
                    if (Media.receive(p)) {
                        writeTrace("DLMS_R05-> " + now() + "\t"
                                + GXCommon.bytesToHex(p.getReply()));
                    }
                    Media.close();
                    // This sleep make sure that all meters can be read.
                    Thread.sleep(400);
                    serial.setDataBits(8);
                    serial.setParity(Parity.NONE);
                    serial.setStopBits(StopBits.ONE);
                    serial.setBaudRate(BaudRate.forValue(bitrate));
                    Media.open();
                    serial.setDtrEnable(true);
                    serial.setRtsEnable(true);
                    Thread.sleep(400);
                }
            }
        }
        ConnectionStartTime
                = java.util.Calendar.getInstance().getTimeInMillis();
        GXReplyData reply = new GXReplyData();
        byte[] data = dlms.snrmRequest();
        if (data.length != 0) {
            readDLMSPacket(data, reply);
            // Has server accepted client.
            dlms.parseUAResponse(reply.getData());

            // Allocate buffer to same size as transmit buffer of the meter.
            // Size of replyBuff is payload and frame (Bop, EOP, crc).
            int size = (int) ((((Number) dlms.getLimits().getMaxInfoTX())
                    .intValue() & 0xFFFFFFFFL) + 40);
            replyBuff = java.nio.ByteBuffer.allocate(size);
        }
        reply.clear();
        // Generate AARQ request.
        // Split requests to multiple packets if needed.
        // If password is used all data might not fit to one packet.
        for (byte[] it : dlms.aarqRequest()) {
            readDLMSPacket(it, reply);
        }
        // Parse reply.
        dlms.parseAareResponse(reply.getData());
        reply.clear();

        // Get challenge Is HLS authentication is used.
        if (dlms.getIsAuthenticationRequired()) {
            for (byte[] it : dlms.getApplicationAssociationRequest()) {
                readDLMSPacket(it, reply);
            }
            dlms.parseApplicationAssociationResponse(reply.getData());
        }
    }

    /**
     * Reads Clock attributes
     *
     */
    public Object readClockAttr(GXDLMSObject item)
            throws Exception {
        int attributeIndex = 1;

        for (attributeIndex = 1; attributeIndex < 10; attributeIndex++) {
            try {
                byte[] data = dlms.read(item.getName(), item.getObjectType(),
                        attributeIndex)[0];
                GXReplyData reply = new GXReplyData();
                readDataBlock(data, reply);
                // Update data type on read.
                if (item.getDataType(attributeIndex) == DataType.NONE) {
                    item.setDataType(attributeIndex, reply.getValueType());
                }
                dlms.updateValue(item, attributeIndex, reply.getValue());
            } catch (Exception ex) {

            }
        }

        return item;
    }

    /**
     * Reads selected DLMS object with selected attribute index.
     *
     * @param item
     * @param attributeIndex
     * @return
     * @throws Exception
     */
    public Object readObject(GXDLMSObject item, int attributeIndex)
            throws Exception {
        byte[] data = dlms.read(item.getName(), item.getObjectType(),
                attributeIndex)[0];
        GXReplyData reply = new GXReplyData();

        readDataBlock(data, reply);
        // Update data type on read.
        if (item.getDataType(attributeIndex) == DataType.NONE) {
            item.setDataType(attributeIndex, reply.getValueType());
        }
        return dlms.updateValue(item, attributeIndex, reply.getValue());
    }

    public String readDate(GXDLMSObject item, int attributeIndex)
            throws Exception {
        Object o = readObject(item, attributeIndex);
        GXDateTime gxDate;
        Date dtDate;
        long lOffset = (!(this.manufacturer.getIdentification().contains("LGZ1") || this.manufacturer.getIdentification().contains("ETR"))) ? 3600 * 1000 : 0;
        if (o instanceof GXDateTime) {
            gxDate = (GXDateTime) o;
            dtDate = new Date(gxDate.getValue().getTime() - lOffset);
            gxDate.setValue(dtDate);
            return df1.format(dtDate).toString();
        }
        return "";
    }
    /*
     * /// Read list of attributes.
     */

    public void readList(List<Entry<GXDLMSObject, Integer>> list)
            throws Exception {
        GXByteBuffer bb = new GXByteBuffer();
        byte[][] data = dlms.readList(list);
        GXReplyData reply = new GXReplyData();
        for (byte[] it : data) {
            reply.clear();
            readDataBlock(it, reply);
            if (reply.isComplete()) {
                bb.set(reply.getData());
            }
        }
        dlms.updateValues(list, bb);
    }

    /**
     * Writes value to DLMS object with selected attribute index.
     *
     * @param item
     * @param attributeIndex
     * @throws Exception
     */
    public void writeObject(GXDLMSObject item, int attributeIndex)
            throws Exception {
        byte[][] data = dlms.write(item, attributeIndex);
        readDLMSPacket(data);
    }

    /*
     * Returns columns of profile Generic.
     */
    public List<Entry<GXDLMSObject, GXDLMSCaptureObject>>
            GetColumns(GXDLMSProfileGeneric pg) throws Exception {
        Object entries = readObject(pg, 7);
        GXObisCode code = manufacturer.getObisCodes()
                .findByLN(pg.getObjectType(), pg.getLogicalName(), null);
        if (code != null) {
            System.out.println("Reading Profile Generic: " + pg.getLogicalName()
                    + " " + code.getDescription() + " entries:"
                    + entries.toString());
        } else {
            System.out.println("Reading Profile Generic: " + pg.getLogicalName()
                    + " entries:" + entries.toString());
        }
        GXReplyData reply = new GXReplyData();
        byte[] data = dlms.read(pg.getName(), pg.getObjectType(), 3)[0];
        readDataBlock(data, reply);
        dlms.updateValue((GXDLMSObject) pg, 3, reply.getValue());
        return pg.getCaptureObjects();
    }

    /**
     * Read Profile Generic's data by entry start and count.
     *
     * @param pg
     * @param index
     * @param count
     * @return
     * @throws Exception
     */
    public Object[] readRowsByEntry(GXDLMSProfileGeneric pg, int index,
            int count) throws Exception {
        byte[][] data = dlms.readRowsByEntry(pg, index, count);
        GXReplyData reply = new GXReplyData();
        readDataBlock(data, reply);
        return (Object[]) dlms.updateValue(pg, 2, reply.getValue());
    }

    /**
     * Read Profile Generic's data by range (start and end time).
     *
     * @param pg
     * @param sortedItem
     * @param start
     * @param end
     * @return
     * @throws Exception
     */
    public Object[] readRowsByRange(final GXDLMSProfileGeneric pg,
            final Date start, final Date end) throws Exception {
        GXReplyData reply = new GXReplyData();
        byte[][] data = dlms.readRowsByRange(pg, start, end);
        readDataBlock(data, reply);
        return (Object[]) dlms.updateValue(pg, 2, reply.getValue());
    }

    /*
     * Read Scalers and units from the register objects.
     */
    public void readRegisters(final GXDLMSObjectCollection objects,
            final PrintWriter logFile) throws Exception {
        GXDLMSObjectCollection objs = objects.getObjects(new ObjectType[]{
            ObjectType.REGISTER});//, ObjectType.CLOCK

        int i = 0;
        for (i = 10; i < objs.size(); i++) {
            objs.remove(i);
        }

        try {
            List<Entry<GXDLMSObject, Integer>> list
                    = new ArrayList<Entry<GXDLMSObject, Integer>>();
            i = 0;
            for (GXDLMSObject it : objs) {
                if (i++ > 10) {
                    break;
                }
                if (it instanceof GXDLMSRegister) {
                    list.add(new GXSimpleEntry<GXDLMSObject, Integer>(it, 2));
                } else if (it instanceof GXDLMSDemandRegister) {
                    list.add(new GXSimpleEntry<GXDLMSObject, Integer>(it, 2));
                } else {
                    list.add(new GXSimpleEntry<GXDLMSObject, Integer>(it, 2));
                }
            }
            readList(list);
        } catch (Exception ex) {

            objs.clear();
            if (objs.isEmpty()) {
                throw new Exception("readRegisters-error");
            }
            for (GXDLMSObject it : objs) {
                try {
                    if (it instanceof GXDLMSRegister) {
                        readObject(it, 3);
                    } else if (it instanceof GXDLMSDemandRegister) {
                        readObject(it, 4);
                    }
                } catch (Exception e) {
                    traceLn(logFile,
                            "Err! Failed1 to read scaler and unit value: "
                            + e.getMessage());
                    // Continue reading.
                }
            }
        }
    }

    /*
     * Read Scalers and units from the register objects.
     */
    public void readScalerAndUnits(final GXDLMSObjectCollection objects,
            final PrintWriter logFile, String s1) {
        GXDLMSObjectCollection objs = objects.getObjects(new ObjectType[]{
            ObjectType.REGISTER, ObjectType.DEMAND_REGISTER,
            ObjectType.EXTENDED_REGISTER});

        try {
            List<Entry<GXDLMSObject, Integer>> list
                    = new ArrayList<Entry<GXDLMSObject, Integer>>();
            for (GXDLMSObject it : objs) {
                if (it instanceof GXDLMSRegister) {
                    list.add(new GXSimpleEntry<GXDLMSObject, Integer>(it, 3));
                }
                if (it instanceof GXDLMSDemandRegister) {
                    list.add(new GXSimpleEntry<GXDLMSObject, Integer>(it, 4));
                }
            }
            readList(list);
        } catch (Exception ex) {
            int obj1=0;
            for (GXDLMSObject it : objs) {
                obj1++;
                try {
                    if (it instanceof GXDLMSRegister) {
                        readObject(it, 3);
                    } else if (it instanceof GXDLMSDemandRegister) {
                        readObject(it, 4);
                    }
                } catch (Exception e) {
                    traceLn(logFile,
                            "Err! Failed2 to read scaler and unit value."+s1+"["+Integer.toString(obj1)+"]: "
                            + e.getMessage());
                    // Continue reading.
                }
            }
        }
    }

    public String getProfileGenericColumnsString(final GXDLMSObject it) {
        StringBuilder sb = new StringBuilder();

        try {
            GXDLMSProfileGeneric pg = (GXDLMSProfileGeneric) it;
            boolean first = true;

            for (Entry<GXDLMSObject, GXDLMSCaptureObject> col : pg
                    .getCaptureObjects()) {
                if (!first) {
                    sb.append("\t");
                } else {
                    first = false;
                }
                if (col.getKey().getObjectType() == ObjectType.CLOCK) {
                    //readObject(col.getKey(),2);
                }
                sb.append(col.getKey().getName());
                //sb.append(" ");
                //  String desc = col.getKey().getDescription();
                //  if (desc != null) {
                //      sb.append(desc);
                //  }

            }
        } catch (Exception ex) {

        }
        return sb.toString();
    }

    /*
     * Read profile generic columns from the meter.
     */
    public void readProfileGenericColumns(final GXDLMSObjectCollection objects,
            final PrintWriter logFile) {
        readProfileGenericColumns(objects, logFile);
    }

    /*
     * Read profile generic columns from the meter.
     */
    public String readProfileGenericColumnsString(final GXDLMSObjectCollection objects,
            final PrintWriter logFile) {
        GXDLMSObjectCollection profileGenerics
                = objects.getObjects(ObjectType.PROFILE_GENERIC);
        StringBuilder sb = new StringBuilder();
        for (GXDLMSObject it : profileGenerics) {
            traceLn(logFile, "Profile Generic " + it.getName() + " Columns:");
            GXDLMSProfileGeneric pg = (GXDLMSProfileGeneric) it;
            // Read columns.
            try {
                readObject(pg, 3);
                boolean first = true;

                for (Entry<GXDLMSObject, GXDLMSCaptureObject> col : pg
                        .getCaptureObjects()) {
                    if (!first) {
                        sb.append("\t");
                    } else {
                        first = false;
                    }
                    if (col.getKey().getObjectType() == ObjectType.CLOCK) {
                        //readObject(col.getKey(),2);
                    }
                    sb.append(col.getKey().getName());
                    // sb.append(" ");
                    // String desc = col.getKey().getDescription();
                    // if (desc != null) {
                    //     sb.append(desc);
                    // }

                }
                traceLn(logFile, sb.toString());
            } catch (Exception ex) {
                traceLn(logFile,
                        "Err! Failed to read columns:" + ex.getMessage());
                // Continue reading.
            }

        }
        return sb.toString();
    }

    /**
     * Read all data from the meter except profile generic (Historical) data.
     */
    public void readValues(final GXDLMSObjectCollection objects,
            final PrintWriter logFile) {
        for (GXDLMSObject it : objects) {
            if (!(it instanceof IGXDLMSBase)) {
                // If interface is not implemented.
                System.out.println(
                        "Unknown Interface: " + it.getObjectType().toString());
                continue;
            }

           // if (it instanceof GXDLMSProfileGeneric) {
            // Profile generic are read later
            // because it might take so long time
            // and this is only a example.
            //     continue;
            //}
            traceLn(logFile,
                    "-------- Reading " + it.getClass().getSimpleName() + " "
                    + it.getName().toString() + " "
                    + it.getDescription());
            for (int pos : ((IGXDLMSBase) it).getAttributeIndexToRead()) {
                try {
                    if (it instanceof GXDLMSProfileGeneric) {
                        if (pos == 2) {
                            continue;
                        }
                    }
                    Object val = readObject(it, pos);
                    if (val instanceof byte[]) {
                        val = GXCommon.bytesToHex((byte[]) val);
                    } else if (val instanceof Double) {
                        NumberFormat formatter
                                = NumberFormat.getNumberInstance();
                        val = formatter.format(val);
                    } else if (val != null && val.getClass().isArray()) {
                        String str = "";
                        for (int pos2 = 0; pos2 != Array
                                .getLength(val); ++pos2) {
                            if (!str.equals("")) {
                                str += ", ";
                            }
                            Object tmp = Array.get(val, pos2);
                            if (tmp instanceof byte[]) {
                                str += GXCommon.bytesToHex((byte[]) tmp);
                            } else {
                                str += String.valueOf(tmp);
                            }
                        }
                        val = str;
                    }
                    traceLn(logFile,
                            "Index: " + pos + " Value: " + String.valueOf(val));
                } catch (Exception ex) {
                    traceLn(logFile,
                            "Error! Index: " + pos + " " + ex.getMessage());
                    // Continue reading.
                }
            }
        }
    }

    public void readValues2(final GXDLMSObjectCollection objects,
            final PrintWriter logFile) {
        for (GXDLMSObject it : objects) {
            if (!(it instanceof IGXDLMSBase)) {
                // If interface is not implemented.
                System.out.println(
                        "Unknown Interface: " + it.getObjectType().toString());
                continue;
            }

            if (it instanceof GXDLMSProfileGeneric) {
                // Profile generic are read later
                // because it might take so long time
                // and this is only a example.
                continue;
            }
            traceLn(logFile,
                    "-------- Reading " + it.getClass().getSimpleName() + " "
                    + it.getName().toString() + " "
                    + it.getDescription());
            int pos = 2;
            //for (int pos : ((IGXDLMSBase) it).getAttributeIndexToRead()) {
            try {
                Object val = readObject(it, pos);
                if (val instanceof byte[]) {
                    val = GXCommon.bytesToHex((byte[]) val);
                } else if (val instanceof Double) {
                    NumberFormat formatter
                            = NumberFormat.getNumberInstance();
                    val = formatter.format(val);
                } else if (val != null && val.getClass().isArray()) {
                    String str = "";
                    for (int pos2 = 0; pos2 != Array
                            .getLength(val); ++pos2) {
                        if (!str.equals("")) {
                            str += ", ";
                        }
                        Object tmp = Array.get(val, pos2);
                        if (tmp instanceof byte[]) {
                            str += GXCommon.bytesToHex((byte[]) tmp);
                        } else {
                            str += String.valueOf(tmp);
                        }
                    }
                    val = str;
                }
                traceLn(logFile,
                        "Index: " + pos + " Value: " + String.valueOf(val));
            } catch (Exception ex) {
                traceLn(logFile,
                        "Error! Index: " + pos + " " + ex.getMessage());
                // Continue reading.
            }
            //}
        }
    }

    /**
     * Read profile generic (Historical) data attributes.
     */
    public void readProfileGenericsAttr(final GXDLMSObjectCollection objects,
            final PrintWriter logFile) throws Exception {
        GXDLMSObjectCollection profileGenerics
                = objects.getObjects(ObjectType.PROFILE_GENERIC);
        for (GXDLMSObject it : profileGenerics) {
            traceLn(logFile,
                    "-------- Reading " + it.getClass().getSimpleName() + " "
                    + it.getName().toString() + " "
                    + it.getDescription());
            long capture_period = ((Number) readObject(it, 4)).longValue();
            readObject(it, 5);
            readObject(it, 6);
            long entriesInUse = ((Number) readObject(it, 7)).longValue();
            long entries = ((Number) readObject(it, 8)).longValue();
            traceLn(logFile, "Capture period: " + String.valueOf(capture_period)
                    + "  Entries: " + String.valueOf(entriesInUse) + "/"
                    + String.valueOf(entries));
        }
    }

    /**
     * Read profile generic (Historical) data.
     */
    public void readProfileGenericsFirst5Rows(final GXDLMSObjectCollection objects,
            final PrintWriter logFile) throws Exception {
        Object[] cells;
        GXDLMSObjectCollection profileGenerics
                = objects.getObjects(ObjectType.PROFILE_GENERIC);
        for (GXDLMSObject it : profileGenerics) {
            GXDLMSProfileGeneric pg = (GXDLMSProfileGeneric) it;
            // Read first 3 item.
            try {
                cells = readRowsByEntry(pg, 1, 5);
                for (Object rows : cells) {
                    for (Object cell : (Object[]) rows) {
                        if (cell instanceof byte[]) {
                            trace(logFile,
                                    GXCommon.bytesToHex((byte[]) cell) + "\t");
                        } else {
                            trace(logFile, cell + "\t");
                        }
                    }
                    traceLn(logFile, "");
                }
            } catch (Exception ex) {
                traceLn(logFile,
                        "Error! Failed to read first row: " + ex.getMessage());
                // Continue reading if device returns access denied error.
            }
        }
    }

    /**
     * Read profile generic (Historical) data.
     */
    public void readProfileGenericsLast5Rows(final GXDLMSObjectCollection objects,
            final PrintWriter logFile) throws Exception {
        Object[] cells;
        GXDLMSObjectCollection profileGenerics
                = objects.getObjects(ObjectType.PROFILE_GENERIC);
        for (GXDLMSObject it : profileGenerics) {
            GXDLMSProfileGeneric pg = (GXDLMSProfileGeneric) it;
            // Read first 3 item.
            try {
                if (pg.getSortMethod() == SortMethod.FIFO) {
                    readObject(it, 7);
                    cells = readRowsByEntry(pg, pg.getEntriesInUse() - 5, pg.getEntriesInUse());
                } else {
                    cells = readRowsByEntry(pg, 1, 5);
                }

                for (Object rows : cells) {
                    for (Object cell : (Object[]) rows) {
                        if (cell instanceof byte[]) {
                            trace(logFile,
                                    GXCommon.bytesToHex((byte[]) cell) + "\t");
                        } else {
                            trace(logFile, cell + "\t");
                        }
                    }
                    traceLn(logFile, "");
                }
            } catch (Exception ex) {
                traceLn(logFile,
                        "Error! Failed to read first row: " + ex.getMessage());
                // Continue reading if device returns access denied error.
            }
        }
    }

    /**
     * Read profile generic (Historical) data.
     */
    public void readProfileGenericsLastNRows(final GXDLMSObject it, final int iNoOfRows,
            final PrintWriter logFile) throws Exception {
        Object[] cells;

        GXDLMSProfileGeneric pg = (GXDLMSProfileGeneric) it;
        // Read first N item.
        try {
            if (pg.getSortMethod() == SortMethod.FIFO) {
                readObject(it, 7);
                cells = readRowsByEntry(pg, pg.getEntriesInUse() - iNoOfRows + 1, pg.getEntriesInUse());
            } else {
                cells = readRowsByEntry(pg, 1, iNoOfRows);
            }
            traceLn(logFile, it.getLogicalName());
            for (Object rows : cells) {
                for (Object cell : (Object[]) rows) {
                    if (cell instanceof byte[]) {
                        trace(logFile,
                                GXCommon.bytesToHex((byte[]) cell) + "\t");
                    } else {
                        trace(logFile, cell + "\t");
                    }
                }
                traceLn(logFile, "");
            }
        } catch (Exception ex) {
            traceLn(logFile,
                    "Error! Failed to read first row: " + ex.getMessage());
            // Continue reading if device returns access denied error.
        }

    }

    DateFormat df1 = new SimpleDateFormat("MM/dd/YYYY HH:mm:ss");

    public Object[] readRowsByEntryPrint(GXDLMSProfileGeneric pg, int index,
            int count, final PrintWriter logFile, int iPrint) throws Exception {
        Object[] oRet = readRowsByEntry(pg, index, count);
        /*  for (Object rows : oRet) {
         for (Object cell : (Object[]) rows) {
         if (cell instanceof byte[]) {
         trace(logFile,
         GXCommon.bytesToHex((byte[]) cell) + "\t");
         } else {
         if (cell instanceof GXDateTime) {
         trace(logFile, df1.format(((GXDateTime) cell).getValue()).toString() + "\t");
         } else {
         trace(logFile, cell + "\t");
         }
         }
         } */
        if (iPrint == 1) {
            traceLn(logFile, ProfileToString(oRet));
        }
        //}
        return oRet;

    }

    public String ProfileToString(Object[] oProfileRows) {
        String sRet = "";
        Date dtTmp;
        long lOffset = (!this.manufacturer.getIdentification().contains("LGZ1")) ? 3600 * 1000 : 0;
        try {
            for (Object rows : oProfileRows) {
                for (Object cell : (Object[]) rows) {
                    if (cell instanceof byte[]) {
                        sRet += GXCommon.bytesToHex((byte[]) cell) + "\t";
                    } else {
                        if (cell instanceof GXDateTime) {
                            dtTmp = ((GXDateTime) cell).getValue();
                            dtTmp = new Date(dtTmp.getTime() - lOffset);
                            sRet += df1.format(dtTmp).toString() + "\t";
                        } else {
                            sRet += cell + "\t";
                        }
                    }
                }
            }
        } catch (Exception ex) {

        }
        return sRet;

    }

    /**
     * Read profile generic (Historical) data.
     */
    public void updateProfileGenericsDetails(Hashtable htProfileDetail, final GXDLMSObject it, int iNoOfRows,
            final PrintWriter logFile) throws Exception {
        Object[] cells;
        Object[] cellsFrom;
        Object[] cellsTo;
        Object[] cellsTemp;

        int iTotalEntries = 0;
        int iCrtEntries = 0;
        int iFrom = 0;
        int iTo = 0;
        int iCrt = 0;
        int iTemp = 0;

        Date lDateFrom;
        Date lDateTo;
        Date lDateCrt;
        Date lDateTemp;

        int iAscending = 0;

        GXDLMSProfileGeneric pg = (GXDLMSProfileGeneric) it;
        // Read first N item.
        try {
            readObject(it, 7);
            readObject(it, 8);
            iCrtEntries = pg.getEntriesInUse();
            iTotalEntries = pg.getProfileEntries();

            htProfileDetail.put("iTotalEntries", iTotalEntries);

            if (pg.getSortMethod() == SortMethod.LIFO) {
                iCrtEntries = 1;
                cells = readRowsByEntryPrint(pg, iCrtEntries, iCrtEntries, logFile, 0);

                lDateTemp = GetProfileDate(cells);
                htProfileDetail.put("iCrtEntries", iCrtEntries);
                htProfileDetail.put("lLastDate", new Date(lDateTemp.getTime() - 1000));
                htProfileDetail.put("sLastRow", ProfileToString(cells));
                return;
            }

            if (iCrtEntries < iTotalEntries) {
                iNoOfRows = iNoOfRows / 2;
                cells = readRowsByEntryPrint(pg, pg.getEntriesInUse() - iNoOfRows, pg.getEntriesInUse() - iNoOfRows, logFile, 0);

                lDateTemp = GetProfileDate(cells);
                htProfileDetail.put("iCrtEntries", iCrtEntries - iNoOfRows);
                htProfileDetail.put("lLastDate", lDateTemp);
                htProfileDetail.put("sLastRow", ProfileToString(cells));

            } else {

                cellsFrom = readRowsByEntryPrint(pg, 0, 1, logFile, 0);
                lDateFrom = GetProfileDate(cellsFrom);

                /*    cellsTemp = readRowsByEntryPrint(pg, iTotalEntries / 3, iTotalEntries / 3, logFile,0);
                 lDateTemp = GetProfileDate(cellsTemp);

                 cellsTo = readRowsByEntryPrint(pg, iTotalEntries * 2 / 3, iTotalEntries * 2 / 3, logFile,0);
                 lDateTo = GetProfileDate(cellsTo);

                 if ((lDateTemp.after(lDateFrom) && lDateTo.after(lDateTemp) && lDateFrom.before(lDateTo))
                 || (lDateTemp.after(lDateFrom) && lDateTo.before(lDateTemp) && lDateFrom.after(lDateTo))
                 || (lDateTemp.before(lDateFrom) && lDateTo.after(lDateTemp) && lDateFrom.after(lDateTo))) {
                 iAscending = 1;
                 } else {
                 iAscending = 0;
                 }
                 htProfileDetail.put("iAscending", iAscending);
                 System.out.println("iAscending =" + iAscending);
                 */
                iAscending = 1;
                cellsTo = readRowsByEntryPrint(pg, iTotalEntries, iTotalEntries, logFile, 0);
                lDateTo = GetProfileDate(cellsTo);

                iFrom = 0;
                iTo = iTotalEntries;
                while (iTo - iFrom >= iNoOfRows) {
                    iCrt = (iTo - iFrom) / 2 + iFrom;
                    if (iCrt == iFrom) {
                        break;
                    }
                    cells = readRowsByEntryPrint(pg, iCrt, iCrt, logFile, 0);
                    lDateCrt = GetProfileDate(cells);
                    if (iAscending == 1) {
                        if (lDateCrt.after(lDateTo)) {
                            lDateFrom = lDateCrt;
                            iFrom = iCrt;
                        } else {
                            lDateTo = lDateCrt;
                            iTo = iCrt;
                        }
                    } else {
                        if (lDateCrt.before(lDateTo)) {
                            lDateFrom = lDateCrt;
                            iFrom = iCrt;
                        } else {
                            lDateTo = lDateCrt;
                            iTo = iCrt;
                        }
                    }

                }

                cells = readRowsByEntryPrint(pg, iFrom, iFrom + iNoOfRows, logFile, 0);
                lDateTemp = GetProfileDate(cells);
                htProfileDetail.put("iCrtEntries", iFrom);
                htProfileDetail.put("lLastDate", lDateTemp);
                htProfileDetail.put("sLastRow", ProfileToString(cells));
            }

        } catch (Exception ex) {
            traceLn(logFile,
                    "Error! Failed to read first row: " + ex.getMessage());
            // Continue reading if device returns access denied error.
        }

    }

    /**
     * Read profile generic (Historical) data.
     */
    public void readProfileGenericsCurrentRows(Hashtable htProfileDetail, final GXDLMSObject it, final int iNoOfRows,
            final PrintWriter logFile) throws Exception {
        Object[] cells;
        int iTotalEntries = 0;
        int iCrtEntries = 0;
        Date lDateCrt;
        Date lLastDate;
        long lOffset = (!this.manufacturer.getIdentification().contains("LGZ1")) ? 3600 * 1000 : 0;

        GXDLMSProfileGeneric pg = (GXDLMSProfileGeneric) it;
        try {
            iTotalEntries = (int) htProfileDetail.get("iTotalEntries");
            iCrtEntries = (int) htProfileDetail.get("iCrtEntries");
            if (pg.getSortMethod() == SortMethod.LIFO) {
                iCrtEntries = 1;
            } else if (++iCrtEntries > iTotalEntries) {
                iCrtEntries = 1;
            }
            cells = readRowsByEntryPrint(pg, iCrtEntries, iCrtEntries, logFile, 0);
            lDateCrt = GetProfileDate(cells);
            lLastDate = (Date) htProfileDetail.get("lLastDate");
            // Compensate for summer time or time sync
            lLastDate = new Date(lLastDate.getTime() - 24 * lOffset);
            if (lDateCrt.after(lLastDate)) {
                htProfileDetail.put("iCrtEntries", iCrtEntries);
                htProfileDetail.put("lLastDate", lDateCrt);
                htProfileDetail.put("sLastRow", ProfileToString(cells));
            }

        } catch (Exception ex) {

        }
    }

    /**
     * Read profile generic (Historical) data.
     */
    public void readProfileGenericsCurrentNRows(final GXDLMSObject it, final int iNoOfRows,
            final PrintWriter logFile) throws Exception {
        Object[] cells;
        Object[] cellsFrom;
        Object[] cellsTo;
        Object[] cellsTemp;

        int iTotalEntries = 0;
        int iCrtEntries = 0;
        int iFrom = 0;
        int iTo = 0;
        int iCrt = 0;
        int iTemp = 0;

        Date lDateFrom;
        Date lDateTo;
        Date lDateCrt;
        Date lDateTemp;

        int iAscending = 0;

        GXDLMSProfileGeneric pg = (GXDLMSProfileGeneric) it;
        // Read first N item.
        try {
            readObject(it, 7);
            readObject(it, 8);
            iCrtEntries = pg.getEntriesInUse();
            iTotalEntries = pg.getProfileEntries();

            if (iCrtEntries < iTotalEntries) {
                cells = readRowsByEntryPrint(pg, pg.getEntriesInUse() - iNoOfRows + 1, pg.getEntriesInUse(), logFile, 0);
            } else {

                for (int iR = 8; iR < 12; iR++) {
                    cellsTo = readRowsByEntryPrint(pg, iR, iR + 2, logFile, 0);
                    //lDateTo = GetProfileDate(cellsTo);
                }

                cellsFrom = readRowsByEntryPrint(pg, 0, 1, logFile, 0);
                lDateFrom = GetProfileDate(cellsFrom);

                cellsTemp = readRowsByEntryPrint(pg, iTotalEntries / 3, iTotalEntries / 3, logFile, 0);
                lDateTemp = GetProfileDate(cellsTemp);

                cellsTo = readRowsByEntryPrint(pg, iTotalEntries * 2 / 3, iTotalEntries * 2 / 3, logFile, 0);
                lDateTo = GetProfileDate(cellsTo);

                if ((lDateTemp.after(lDateFrom) && lDateTo.after(lDateTemp) && lDateFrom.before(lDateTo))
                        || (lDateTemp.after(lDateFrom) && lDateTo.before(lDateTemp) && lDateFrom.after(lDateTo))
                        || (lDateTemp.before(lDateFrom) && lDateTo.after(lDateTemp) && lDateFrom.after(lDateTo))) {
                    iAscending = 1;
                } else {
                    iAscending = 0;
                }
                System.out.println("iAscending =" + iAscending);

                cellsTo = readRowsByEntryPrint(pg, iTotalEntries, iTotalEntries, logFile, 0);
                lDateTo = GetProfileDate(cellsTo);

                iFrom = 0;
                iTo = iTotalEntries;
                while (iTo - iFrom >= iNoOfRows) {
                    iCrt = (iTo - iFrom) / 2 + iFrom;
                    if (iCrt == iFrom) {
                        break;
                    }
                    cells = readRowsByEntryPrint(pg, iCrt, iCrt, logFile, 0);
                    lDateCrt = GetProfileDate(cells);
                    if (iAscending == 1) {
                        if (lDateCrt.after(lDateTo)) {
                            lDateFrom = lDateCrt;
                            iFrom = iCrt;
                        } else {
                            lDateTo = lDateCrt;
                            iTo = iCrt;
                        }
                    } else {
                        if (lDateCrt.before(lDateTo)) {
                            lDateFrom = lDateCrt;
                            iFrom = iCrt;
                        } else {
                            lDateTo = lDateCrt;
                            iTo = iCrt;
                        }
                    }

                }
                cells = readRowsByEntryPrint(pg, iFrom, iFrom + iNoOfRows, logFile, 0);
            }

        } catch (Exception ex) {
            traceLn(logFile,
                    "Error! Failed to read first row: " + ex.getMessage());
            // Continue reading if device returns access denied error.
        }

    }

    private Date GetProfileDate(Object[] cells) {
        Date lRet = new Date(0);
        Object[] row;
        GXDateTime gxdt;
        long lOffset = (!this.manufacturer.getIdentification().contains("LGZ")) ? 3600 * 1000 : 0;

        try {
            row = (Object[]) cells[0];
            gxdt = (GXDateTime) row[0];
            lRet = new Date(gxdt.getValue().getTime() - lOffset);
            // lRet=gxdt.toMeterTime();
            //lRet = new Date(row[0].toString());

        } catch (Exception ex) {

        }

        return lRet;
    }

    /**
     * Read profile generic (Historical) data.
     */
    public void readProfileGenerics(final GXDLMSObjectCollection objects,
            final PrintWriter logFile) throws Exception {
        Object[] cells;
        GXDLMSObjectCollection profileGenerics
                = objects.getObjects(ObjectType.PROFILE_GENERIC);
        for (GXDLMSObject it : profileGenerics) {
            traceLn(logFile,
                    "-------- Reading " + it.getClass().getSimpleName() + " "
                    + it.getName().toString() + " "
                    + it.getDescription());

            long entriesInUse = ((Number) readObject(it, 7)).longValue();
            long entries = ((Number) readObject(it, 8)).longValue();
            traceLn(logFile, "Entries: " + String.valueOf(entriesInUse) + "/"
                    + String.valueOf(entries));
            GXDLMSProfileGeneric pg = (GXDLMSProfileGeneric) it;
            // If there are no columns.
            if (entriesInUse == 0 || pg.getCaptureObjects().size() == 0) {
                continue;
            }
            ///////////////////////////////////////////////////////////////////
            // Read first item.
            try {
                cells = readRowsByEntry(pg, 1, 1);
                for (Object rows : cells) {
                    for (Object cell : (Object[]) rows) {
                        if (cell instanceof byte[]) {
                            trace(logFile,
                                    GXCommon.bytesToHex((byte[]) cell) + "\t");
                        } else {
                            trace(logFile, cell + "\t");
                        }
                    }
                    traceLn(logFile, "");
                }
            } catch (Exception ex) {
                traceLn(logFile,
                        "Error! Failed to read first row: " + ex.getMessage());
                // Continue reading if device returns access denied error.
            }
            ///////////////////////////////////////////////////////////////////
            // Read last day.
            try {
                java.util.Calendar start = java.util.Calendar
                        .getInstance(java.util.TimeZone.getTimeZone("UTC"));
                start.set(java.util.Calendar.HOUR_OF_DAY, 0); // set hour to
                // midnight
                start.set(java.util.Calendar.MINUTE, 0); // set minute in
                // hour
                start.set(java.util.Calendar.SECOND, 0); // set second in
                // minute
                start.set(java.util.Calendar.MILLISECOND, 0);
                start.add(java.util.Calendar.DATE, -1);

                java.util.Calendar end = java.util.Calendar.getInstance();
                end.set(java.util.Calendar.MINUTE, 0); // set minute in hour
                end.set(java.util.Calendar.SECOND, 0); // set second in
                // minute
                end.set(java.util.Calendar.MILLISECOND, 0);
                cells = readRowsByRange((GXDLMSProfileGeneric) it,
                        start.getTime(), end.getTime());
                for (Object rows : cells) {
                    for (Object cell : (Object[]) rows) {
                        if (cell instanceof byte[]) {
                            System.out.print(
                                    GXCommon.bytesToHex((byte[]) cell) + "\t");
                        } else {
                            trace(logFile, cell + "\t");
                        }
                    }
                    traceLn(logFile, "");
                }
            } catch (Exception ex) {
                traceLn(logFile,
                        "Error! Failed to read last day: " + ex.getMessage());
                // Continue reading if device returns access denied error.
            }
        }
    }

    /*
     * Read all objects from the meter. This is only example. Usually there is
     * no need to read all data from the meter.
     */
    public void readAllObjects(PrintWriter logFile) throws Exception {
        System.out.println("Reading association view");
        GXReplyData reply = new GXReplyData();
        // Get Association view from the meter.
        // readDataBlock(dlms.getObjectsRequest(), reply);
        // GXDLMSObjectCollection objects
        //         = dlms.parseObjects(reply.getData(), true);

        //Save objects
        //objects.save("Objects1.xml");
        //GXDLMSObjectCollection objs = objects.getObjects(new ObjectType[]{
        //    ObjectType.REGISTER});
        //objs.save("Objects2.xml");
        // Get description of the objects.
        //GXDLMSConverter converter = new GXDLMSConverter();
        //converter.updateOBISCodeInformation(objects);
        //Load objects
        GXDLMSObjectCollection objects = new GXDLMSObjectCollection();
        objects = objects.load("Gurux-data/Objects1.xml");
        //readRegisters(objects, logFile);
        //objects.save("Objects21.xml");
        // Read Scalers and units from the register objects.
        readScalerAndUnits(objects, logFile, "All");
        // Read Profile Generic columns.
        //readProfileGenericColumns(objects, logFile);
        // Read all attributes from all objects.
        String s = "";
        for (int i = 0; i < 10; i++) {
            readRegisters(objects, logFile);
            //readValues(objects, logFile);
            System.out.println(ObjectsValuesToString(objects));
            s += df.format(new Date()) + " - " + objects.toString() + System.lineSeparator();
        }
        System.out.println(s);
        //objects.save("Objects22.xml");
        // Read historical data.
        //readProfileGenerics(objects, logFile);
        System.out.println("ok");
    }

    DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static String ObjectsValuesToString(GXDLMSObjectCollection objects) {
        StringBuilder sb = new StringBuilder();
        int iNoA = 0;
        int i;
        sb.append('[');
        for (GXDLMSObject it : objects) {
            if (sb.length() != 1) {
                sb.append(", ");
            }
            // GXDLMSAttributeSettings gas=it.getAttributes().find(1);
            iNoA = it.getValues().length;
            for (i = 0; i < iNoA; i++) {
                try {
                    sb.append(it.getValues()[i].toString() + "~");
                } catch (Exception e) {
                    //System.out.println(e.getMessage());
                }
            }
        }
        sb.append(']');
        return sb.toString();
    }

    public void readAndPrintAllObjects(PrintWriter logFile) throws Exception {
        System.out.println("Reading association view");
        GXReplyData reply = new GXReplyData();
        // Get Association view from the meter.
        readDataBlock(dlms.getObjectsRequest(), reply);
        if (reply.getData().size() == 0) {
            reply.getData().size(reply.getData().getData().length);
        }
        GXDLMSObjectCollection objects
                = dlms.parseObjects(reply.getData(), true);

        //Save objects
        //objects.save("Objects1.xml");
        //GXDLMSObjectCollection objs = objects.getObjects(new ObjectType[]{
        //    ObjectType.REGISTER});
        //objs.save("Objects2.xml");
        // Get description of the objects.
        GXDLMSConverter converter = new GXDLMSConverter();
        converter.updateOBISCodeInformation(objects);
        readValues(objects, logFile);

        String s = ObjectsValuesAndTypeToString(objects);
        System.out.println(s);
        logFile.print(s);
        logFile.flush();

        util.GuruxUtil.GXDLMSObjectSaveWithVals(objects, "ObjAsRead.txt");

    }

    public static String ObjectsValuesAndTypeToString(GXDLMSObjectCollection objects) {
        StringBuilder sb = new StringBuilder();
        int iNoA = 0;
        int i;
        sb.append('[');
        for (GXDLMSObject it : objects) {
            if (sb.length() != 1) {
                sb.append(", ");
            }
            // GXDLMSAttributeSettings gas=it.getAttributes().find(1);
            iNoA = it.getValues().length;
            for (i = 0; i < iNoA; i++) {
                try {
                    sb.append(it.getValues()[i].toString() + "~");
                    sb.append(it.getValues()[i].getClass().getCanonicalName() + "\r\n");
                } catch (Exception e) {
                    //System.out.println(e.getMessage());
                }
            }
        }
        sb.append("]\r\n");
        return sb.toString();
    }
}
