package cc.fss.redrivegps;

import android.location.Location;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * redrive-android
 * <p>
 * Created by Sławomir Bienia on 30/04/15.
 * Copyright © 2015 FSS Sp. z o.o. All rights reserved.
 */
public class RDConverter {
    private static final String TAG = RDConverter.class.getSimpleName();

    private static final int PDB_SERIAL_V2_MSG_HEADER_SIZE = 1 + 2;
    private static final int PDB_SERIAL_V2_MSG_CRC_SIZE = 1;

    public static final int PACKET_FIELD_SatellitesNo = 0x0001,
            PACKET_FIELD_Time = 0x0002,
            PACKET_FIELD_LatLong = 0x0004,
            PACKET_FIELD_Speed = 0x0008,
            PACKET_FIELD_Heading = 0x0010,
            PACKET_FIELD_HDOP = 0x0020,
            PACKET_FIELD_Altitude = 0x0040,
            PACKET_FIELD_BatteryStatus = 0x0080;

    public static final int PDB_STATUS_NO_ERR = 0;
    public static final int PDB_STATUS_INVALID_LENGTH = -1000;
    public static final int PDB_STATUS_INVALID_CRC = -1001;
    public static final int PDB_STATUS_INVALID_HEADER = -1002;

    public RDConverter() {
    }

    private short PDBExpectedBytesLengthByFlags(short flags) {
        short length = 0;
        short mask = 0x0001;
        short flag;

        while (mask != 0) {

            flag = (short) (mask & flags);

            if (((PACKET_FIELD_SatellitesNo | PACKET_FIELD_BatteryStatus) & flag) > 0) {
                length += 1;
            } else if (((PACKET_FIELD_Time | PACKET_FIELD_Altitude) & flag) > 0) {
                length += 4;
            } else if (((PACKET_FIELD_LatLong) & flag) > 0) {
                length += 8;
            } else if (((PACKET_FIELD_Speed | PACKET_FIELD_Heading | PACKET_FIELD_HDOP) & flag) > 0) {
                length += 2;
            }

            mask = (short) (mask << 1);
        }

        return length;
    }


    public int decodeData(byte[] data, RDData RDData) throws ArrayIndexOutOfBoundsException {
        if (data.length < (PDB_SERIAL_V2_MSG_HEADER_SIZE + PDB_SERIAL_V2_MSG_CRC_SIZE)) {
            return PDB_STATUS_INVALID_LENGTH;
        }

        byte[] b_crc = Arrays.copyOfRange(data, data.length - PDB_SERIAL_V2_MSG_CRC_SIZE, data.length);
        int iCrc = b_crc[0];

        ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOfRange(data, 0, data.length - PDB_SERIAL_V2_MSG_CRC_SIZE));

        if (iCrc != Crc8(buffer.array(), data.length - PDB_SERIAL_V2_MSG_CRC_SIZE)) {
            Log.v(TAG, "decodeData() - Invalid CRC l:" + data.length + " - " + Arrays.toString(data) + " , crc:" + iCrc + "calcCRC:" + Crc8(buffer.array(), data.length - PDB_SERIAL_V2_MSG_CRC_SIZE));
            return PDB_STATUS_INVALID_CRC;
        }

        RDData.Header header = RDData.header;
        RDData.Fields fields = RDData.fields;

        int loc = 0;

        int bHeader;
        byte[] b_header = Arrays.copyOfRange(data, 0, 1);
        byte[] bytes2 = new byte[2];
        bytes2[0] = 0;
        bytes2[1] = b_header[0];
        bHeader = bytesToShort(bytes2);
        loc += 1;

        header.header = (char) bHeader;

        short uiFlags;

        byte[] b_flags = Arrays.copyOfRange(data, loc, loc + 2);
        uiFlags = bytesToShort(b_flags);
        loc += 2;

        header.flags = uiFlags;

        if (String.valueOf(header.header).substring(0, 1).equalsIgnoreCase("B")) {
            return PDB_STATUS_INVALID_HEADER;
        }

        if (PDBExpectedBytesLengthByFlags(header.flags) + PDB_SERIAL_V2_MSG_HEADER_SIZE + PDB_SERIAL_V2_MSG_CRC_SIZE != data.length) {
            return PDB_STATUS_INVALID_LENGTH;
        }


        if ((uiFlags & 0) > 0) {
            fields.szToken = Arrays.copyOfRange(data, loc, 6);
            loc += 6;
        }

        if ((uiFlags & PACKET_FIELD_SatellitesNo) > 0) {
            //Sat
            char bSat;
            byte[] b_sat = Arrays.copyOfRange(data, loc, loc + 1);
            loc += 1;

            fields.satellitesNo = (char) b_sat[0];
        }

        if ((uiFlags & PACKET_FIELD_Time) > 0) {
            //Time UTC
            long uiTime;
            byte[] b_time = Arrays.copyOfRange(data, loc, loc + 4);
            loc += 4;

            uiTime = bytesToIntUnsigned(b_time);
            fields.timeUTC = uiTime;
        }

        if ((uiFlags & PACKET_FIELD_LatLong) > 0) {
            //Lat
            int iLat;
            byte[] b_lat = Arrays.copyOfRange(data, loc, loc + 4);
            loc += 4;

            iLat = bytesToInt(b_lat);
            fields.latitude = iLat;

            //Lon
            int iLon;
            byte[] b_lon = Arrays.copyOfRange(data, loc, loc + 4);
            loc += 4;

            iLon = bytesToInt(b_lon);
            fields.longitude = iLon;
        }

        if ((uiFlags & PACKET_FIELD_Speed) > 0) {
            //Speed
            short sSpeed;
            byte[] b_speed = Arrays.copyOfRange(data, loc, loc + 2);
            loc += 2;

            sSpeed = bytesToShort(b_speed);
            fields.speed = sSpeed;
        }

        if ((uiFlags & PACKET_FIELD_Heading) > 0) {
            //Heading
            int sHeading;
            byte[] b_heading = Arrays.copyOfRange(data, loc, loc + 2);
            loc += 2;

            sHeading = bytesToShortUnsigned(b_heading);
            fields.heading = sHeading;
        }

        if ((uiFlags & PACKET_FIELD_HDOP) > 0) {
            //HDOP
            short sHDOP;
            byte[] b_HDOP = Arrays.copyOfRange(data, loc, loc + 2);
            loc += 2;

            sHDOP = bytesToShort(b_HDOP);
            fields.HDOP = sHDOP;
        }

        if ((uiFlags & PACKET_FIELD_Altitude) > 0) {
            //Height
            int iHeight;
            byte[] b_Height = Arrays.copyOfRange(data, loc, loc + 4);
            loc += 4;

            iHeight = bytesToInt(b_Height);
            fields.height = iHeight;
        }

        if ((uiFlags & PACKET_FIELD_BatteryStatus) > 0) {
            //DriftAngle
            char sBattery;
            byte[] b_arr = Arrays.copyOfRange(data, loc, loc + 1);

            sBattery = (char) b_arr[0];
            fields.batteryStatus = sBattery;
        }

        return PDB_STATUS_NO_ERR;
    }

    public static short bytesToShort(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.rewind();
        return buffer.getShort();
    }

    public static int bytesToShortUnsigned(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.rewind();
        short signed = buffer.getShort();

        return signed >= 0 ? signed : 2 * (int) Short.MAX_VALUE + 2 + signed;
    }

    public static int bytesToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.rewind();
        return buffer.getInt();
    }

    public static long bytesToIntUnsigned(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.rewind();
        int signed = buffer.getInt();

        return signed >= 0 ? signed : 2 * (long) Integer.MAX_VALUE + 2 + signed;
    }

    public boolean decodeData(RDData data, long midnightUTC, Location outputLocation) {
        boolean result = false;

        if (data != null && outputLocation != null) {
            int h, m, s, ms;

            RDData.Fields fields = data.fields;

            double t = (fields.timeUTC / 1000.0);
            h = (int) (t / 10000);
            t = t - h * 10000;
            m = (int) (t / 100);
            t = t - m * 100;
            s = (int) (t / 1);
            t = t - s;
            ms = (int) (t * 1000);

            double lat = fields.latitude / 100000.0 / 60.0;
            double lon = fields.longitude / 100000.0 / -60.0;

            outputLocation.setLatitude(lat);
            outputLocation.setLongitude(lon);
            outputLocation.setAltitude(fields.height / 100.0);
            outputLocation.setAccuracy(fields.satellitesNo == 0 || (((int) fields.satellitesNo) & 0x40) > 0 ? -1 : (float) (fields.HDOP / 100.0));
            outputLocation.setBearing((float) (fields.heading / 100.0));
            outputLocation.setSpeed((float) (fields.speed / (100.0 * 3.6)));
            outputLocation.setTime(midnightUTC + h * 3600000 + m * 60000 + s * 1000 + ms);

            result = true;
        }

        return result;
    }

    private byte Crc8(byte[] data, int length) {
        short Crc = 0;
        int i, j, c = 0;
        for (j = length; j > 0; j--, c++) {
            Crc ^= (data[c] << 8);

            for (i = 8; i > 0; i--) {
                if ((Crc & 0x8000) > 0)
                    Crc ^= (0x1070 << 3);//0x8380;  //polynomial of x^8 + x^2 + x^1 + 1 in most significant 9 bits
                Crc <<= 1;
            }
        }
        return (byte) (Crc >> 8);
    }
}
