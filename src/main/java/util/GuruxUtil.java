/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.enums.DataType;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.enums.Unit;
import gurux.dlms.manufacturersettings.GXDLMSAttribute;
import gurux.dlms.manufacturersettings.GXDLMSAttributeSettings;
import gurux.dlms.objects.GXDLMSCaptureObject;
import gurux.dlms.objects.GXDLMSClock;
import gurux.dlms.objects.GXDLMSData;
import gurux.dlms.objects.GXDLMSExtendedRegister;
import gurux.dlms.objects.GXDLMSObject;
import gurux.dlms.objects.GXDLMSObjectCollection;
import gurux.dlms.objects.GXDLMSProfileGeneric;
import gurux.dlms.objects.GXDLMSRegister;
import gurux.dlms.objects.enums.SortMethod;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/**
 * Load COSEM objects from the file.
 *
 * @param path File path.
 * @return Collection of serialized COSEM objects.
 * @throws XMLStreamException
 * @throws IOException
 */
public class GuruxUtil {

    public static GXDLMSObjectCollection GXDLMSObjectLoadWithVals(final String path)
            throws XMLStreamException, IOException {
        GXDLMSObjectCollection objects = new GXDLMSObjectCollection();
        FileInputStream tmp = null;
        Object o = null;
        boolean bo = false;
        byte by = 0;
        int i = 0;
        double d = 0.0;
        long l = 0;

        try {
            tmp = new FileInputStream(path);
            XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            XMLStreamReader xmlStreamReader
                    = inputFactory.createXMLStreamReader(tmp);
            GXDLMSObject obj = null;
            String target;
            ObjectType type;
            String data = null;
            while (xmlStreamReader.hasNext()) {
                try {
                    int event = xmlStreamReader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        data = null;
                        target = xmlStreamReader.getLocalName();
                        if ("object".compareToIgnoreCase(target) == 0) {
                            type = ObjectType
                                    .valueOf(xmlStreamReader.getAttributeValue(0));
                            obj = GXDLMSClient.createObject(type);
                            objects.add(obj);
                        }
                    } else if (event == XMLStreamConstants.CHARACTERS) {
                        data = xmlStreamReader.getText();
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        target = xmlStreamReader.getLocalName();
                        if ("SN".compareToIgnoreCase(target) == 0) {
                            obj.setShortName(Integer.parseInt(data));
                        } else if ("LN".compareToIgnoreCase(target) == 0) {
                            obj.setLogicalName(data);
                        } else if ("Description".compareToIgnoreCase(target) == 0) {
                            obj.setDescription(data);
                        } else if ("DataType".compareToIgnoreCase(target) == 0) {
                            obj.setDataType(2, DataType.valueOf(data));
                        } else if ("scalar".compareToIgnoreCase(target) == 0) {
                            if (obj instanceof GXDLMSRegister) {
                                ((GXDLMSRegister) obj).setScaler(Double.parseDouble(data));
                            } else if (obj instanceof GXDLMSExtendedRegister) {
                                ((GXDLMSExtendedRegister) obj).setScaler(Double.parseDouble(data));
                            }
                        } else if ("unit".compareToIgnoreCase(target) == 0) {
                            if (obj instanceof GXDLMSRegister) {
                                ((GXDLMSRegister) obj).setUnit(Unit.forValue(Integer.parseInt(data)));
                            } else if (obj instanceof GXDLMSExtendedRegister) {
                                ((GXDLMSExtendedRegister) obj).setUnit(Unit.forValue(Integer.parseInt(data)));
                            }
                        } else if ("value_class".compareToIgnoreCase(target) == 0) {

                            if (data.endsWith("Integer")) {
                                o = i;
                            } else if (data.endsWith("Boolean")) {
                                o = bo;
                            } else if (data.endsWith("Byte")) {
                                o = by;
                            } else if (data.endsWith("Double")) {
                                o = d;
                            } else if (data.endsWith("Long")) {
                                o = l;
                            } else {
                                o = Class.forName(data).newInstance();
                            }
                            if (obj instanceof GXDLMSData) {
                                ((GXDLMSData) obj).setValue(o);
                            } else if (obj instanceof GXDLMSRegister) {
                                ((GXDLMSRegister) obj).setValue(o);
                            } else if (obj instanceof GXDLMSExtendedRegister) {
                                ((GXDLMSExtendedRegister) obj).setValue(o);
                            } else if (obj instanceof GXDLMSClock) {
                                ((GXDLMSClock) obj).setTime((GXDateTime) o);
                            }
                        } else if ("value".compareToIgnoreCase(target) == 0) {
                            GXDLMSObjectSetValue(obj, data);
                        } else if ("deviation".compareToIgnoreCase(target) == 0) {
                            ((GXDLMSClock) obj).setDeviation(Integer.parseInt(data));
                        } else if ("PGCapturePeriod".compareToIgnoreCase(target) == 0) {
                            ((GXDLMSProfileGeneric) obj).setCapturePeriod(Integer.parseInt(data));
                        } else if ("PGProfileEntries".compareToIgnoreCase(target) == 0) {
                            ((GXDLMSProfileGeneric) obj).setProfileEntries(Integer.parseInt(data));
                        } else if ("PGColumns".compareToIgnoreCase(target) == 0) {
                            String[] sObjs = data.split("\t");

                            for (String sObj : sObjs) {
                                ((GXDLMSProfileGeneric) obj).addCaptureObject(objects.findByLN(ObjectType.ALL, sObj), 2, 0);
                            }

                            ((GXDLMSProfileGeneric) obj).setSortMethod(SortMethod.FIFO);
                            ((GXDLMSProfileGeneric) obj).setSortObject(objects.findByLN(ObjectType.ALL, sObjs[0]));
                        }
                    }
                } catch (Exception exc) {
                    System.out.println(exc.getMessage());
                }
            }
        } finally {
            if (tmp != null) {
                tmp.close();
            }
        }
        return objects;
    }

    public static void GXDLMSObjectSaveWithVals(final GXDLMSObjectCollection objects, final String path)
            throws IOException, XMLStreamException {
        PrintWriter pw = null;
        GXDLMSRegister gxRegister;
        GXDLMSData gxData;
        GXDLMSClock gxClock;
        GXDLMSProfileGeneric gxPG;

        try {
            // GXDLMSAttribute gxAttribute;
            pw = new PrintWriter(path, "utf-8");
            String newline = System.getProperty("line.separator");
            XMLOutputFactory output = XMLOutputFactory.newInstance();
            XMLStreamWriter writer = output.createXMLStreamWriter(pw);
            writer.writeStartDocument("utf-8", "1.0");
            writer.setPrefix("gurux", "http://www.gurux.org");
            writer.setDefaultNamespace("http://www.gurux.org");
            writer.writeCharacters(newline);
            writer.writeStartElement("objects");
            writer.writeCharacters(newline);
            for (GXDLMSObject it : objects) {
                writer.writeCharacters(newline);
                writer.writeStartElement("object");
                writer.writeAttribute("Type",
                        String.valueOf(it.getObjectType()));
                writer.writeCharacters(newline);
                // Add SN
                if (it.getShortName() != 0) {
                    writer.writeStartElement("SN");
                    writer.writeCharacters(String.valueOf(it.getShortName()));
                    writer.writeEndElement();
                    writer.writeCharacters(newline);
                }
                // Add LN
                writer.writeStartElement("LN");
                writer.writeCharacters(it.getLogicalName());
                writer.writeEndElement();
                writer.writeCharacters(newline);
                // Add description if given.
                if (it.getDescription() != null
                        && !it.getDescription().isEmpty()) {
                    writer.writeStartElement("Description");
                    writer.writeCharacters(it.getDescription());
                    writer.writeEndElement();
                    writer.writeCharacters(newline);
                }

                writer.writeStartElement("DataType");
                writer.writeCharacters(it.getDataType(2).name());
                writer.writeEndElement();
                writer.writeCharacters(newline);

                /*      writer.writeStartElement("UIDataType");
                 writer.writeCharacters(it.getDataType(2).name());
                 writer.writeEndElement();
                 writer.writeCharacters(newline);
                 */
                if (it instanceof GXDLMSData) {
                    try {
                        gxData = (GXDLMSData) it;

                        writer.writeStartElement("value_class");
                        writer.writeCharacters(gxData.getValue().getClass().getName());
                        writer.writeEndElement();
                        writer.writeCharacters(newline);

                        writer.writeStartElement("value");
                        writer.writeCharacters(gxData.getValue().toString());
                        writer.writeEndElement();
                        writer.writeCharacters(newline);

                    } catch (Exception ex) {

                    }
                }

                if (it instanceof GXDLMSRegister) {
                    try {
                        gxRegister = (GXDLMSRegister) it;

                        writer.writeStartElement("scalar");
                        writer.writeCharacters(Double.toString(gxRegister.getScaler()));
                        writer.writeEndElement();
                        writer.writeCharacters(newline);

                        writer.writeStartElement("unit");
                        writer.writeCharacters(Integer.toString(gxRegister.getUnit().getValue()));
                        writer.writeEndElement();
                        writer.writeCharacters(newline);

                        writer.writeStartElement("value_class");
                        writer.writeCharacters(gxRegister.getValue().getClass().getName());
                        writer.writeEndElement();
                        writer.writeCharacters(newline);

                        writer.writeStartElement("value");
                        writer.writeCharacters(gxRegister.getValue().toString());
                        writer.writeEndElement();
                        writer.writeCharacters(newline);

                    } catch (Exception ex) {

                    }
                }
                if (it instanceof GXDLMSClock) {
                    try {
                        gxClock = (GXDLMSClock) it;
                        writer.writeStartElement("value_class");
                        writer.writeCharacters(gxClock.getTime().getClass().getName());
                        writer.writeEndElement();
                        writer.writeCharacters(newline);

                        writer.writeStartElement("value");
                        writer.writeCharacters(sdfDate.format(gxClock.getTime().getValue().toString()));
                        writer.writeEndElement();
                        writer.writeCharacters(newline);

                        writer.writeStartElement("deviation");
                        writer.writeCharacters(Integer.toString(gxClock.getDeviation()));
                        writer.writeEndElement();
                        writer.writeCharacters(newline);

                    } catch (Exception ex) {

                    }
                }

                if (it instanceof GXDLMSProfileGeneric) {
                    try {
                        gxPG = (GXDLMSProfileGeneric) it;

                        writer.writeStartElement("PGCapturePeriod");
                        writer.writeCharacters(Integer.toString(gxPG.getCapturePeriod()));
                        writer.writeEndElement();
                        writer.writeCharacters(newline);

                        writer.writeStartElement("PGProfileEntries");
                        writer.writeCharacters(Integer.toString(gxPG.getProfileEntries()));
                        writer.writeEndElement();
                        writer.writeCharacters(newline);

                        writer.writeStartElement("PGColumns");
                        writer.writeCharacters(getProfileGenericColumnsString(it));
                        writer.writeEndElement();
                        writer.writeCharacters(newline);
                    } catch (Exception ex) {

                    }
                }

                /*          
                 // Add attributes values
                 writer.writeStartElement("attributes");
                 for (GXDLMSAttributeSettings gxAttribute : it.getAttributes()) {
                    
                 writer.writeStartElement(Integer.toString(gxAttribute.getIndex()));
                 writer.writeCharacters(gxAttribute.getType().name());
                 writer.writeEndElement();
                    
                 }

                 // Close attributes.
                 writer.writeEndElement();
                 writer.writeCharacters(newline);
                 */
                // Close object.
                writer.writeEndElement();
                writer.writeCharacters(newline);
            }
            // Close objects
            writer.writeEndElement();
            writer.writeEndDocument();
            pw.flush();
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
    }

    public static String getProfileGenericColumnsString(final GXDLMSObject it) {
        StringBuilder sb = new StringBuilder();

        try {
            GXDLMSProfileGeneric pg = (GXDLMSProfileGeneric) it;
            boolean first = true;

            for (Map.Entry<GXDLMSObject, GXDLMSCaptureObject> col : pg
                    .getCaptureObjects()) {
                if (!first) {
                    sb.append("\t");
                } else {
                    first = false;
                }

                sb.append(col.getKey().getLogicalName());

            }
        } catch (Exception ex) {

        }
        return sb.toString();
    }

    public static SimpleDateFormat sdfDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

    public static void GXDLMSObjectSetValue(GXDLMSObject it, String sValue) {
        try {
            Object oCrtValue = it.getValues()[1];
            Object oNewValue = null;
            GXDLMSData gxData;
            double dScaler = 1.0;

            if (it instanceof GXDLMSRegister) {
                dScaler = ((GXDLMSRegister) it).getScaler();
            } else if (it instanceof GXDLMSExtendedRegister) {
                dScaler = ((GXDLMSExtendedRegister) it).getScaler();
            }

            if (oCrtValue instanceof String) {
                oNewValue = sValue;
            } else if (oCrtValue instanceof Boolean) {
                oNewValue = Boolean.valueOf(sValue);
            } else if (oCrtValue instanceof Byte) {
                oNewValue = Byte.valueOf(sValue);
            } else if (oCrtValue instanceof Integer) {
                oNewValue = Integer.valueOf(sValue)/dScaler;
            } else if (oCrtValue instanceof Long) {
                oNewValue = Long.valueOf(sValue)/dScaler;
            } else if (oCrtValue instanceof Double) {
                oNewValue = Double.valueOf(sValue)/dScaler;
            } else if (oCrtValue instanceof GXDateTime) {
                oNewValue = new GXDateTime(sdfDate.parse(sValue));
            }
            if (oNewValue == null) {
                return;
            }

            if (it instanceof GXDLMSData) {
                ((GXDLMSData) it).setValue(oNewValue);
            } else if (it instanceof GXDLMSRegister) {
                ((GXDLMSRegister) it).setValue(oNewValue);
            } else if (it instanceof GXDLMSExtendedRegister) {
                ((GXDLMSExtendedRegister) it).setValue(oNewValue);
            } else if (it instanceof GXDLMSClock) {
                ((GXDLMSClock) it).setTime((GXDateTime) oNewValue);
            }

        } catch (Exception ex) {
        }
    }
}
