package cc.fss.redrivegps;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * redrive-android
 * <p>
 * Created by Sławomir Bienia on 19/08/16.
 * Copyright © 2016 FSS Sp. z o.o. All rights reserved.
 */

public class RDCommands {

    //RD Status
    public static final int RDS_STATUS_NO_ERR = 0;
    public static final int RDS_STATUS_INVALID_LENGTH = -1000;
    public static final int RDS_STATUS_INVALID_CRC = -1001;
    public static final int RDS_STATUS_INVALID_HEADER = -1002;

    //RD Commands

    public static final int RDS_CMS_RESET = 0x01;

    public static final int RDS_CMS_GET_DEVICE_INFO = 0x02;
    public static final int RDS_CMS_GET_BT_DEVICE_INFO = 0x03;

    public static final int RDS_CMS_SET_DEVICE_NAME = 0x10;
    public static final int RDS_CMS_SET_GPS_RATE = 0x20;
    public static final int RDS_CMS_SET_TIMEZONE = 0x30;

    public static final int RDS_CMD_FW_UPDATE_MODE = 0x40;

    public static final int RDS_CMD_LOGGING_START = 0x80;
    public static final int RDS_CMD_LOGGING_STOP = 0x81;

    //GPS RATE

    public static final int RDS_PARAM_GPS_RATE_1HZ = 0x01;
    public static final int RDS_PARAM_GPS_RATE_5HZ = 0x05;
    public static final int RDS_PARAM_GPS_RATE_10HZ = 0x0A;
    public static final int RDS_PARAM_GPS_RATE_20HZ = 0x14;

    private static byte Crc8(byte[] data, int length) {
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

    public static RDCommandRequest requestForSetGPSRate(int gpsRate) {
        RDCommandRequest commandRequest = new RDCommandRequest();

        commandRequest.data = new byte[2];
        commandRequest.data[0] = RDS_CMS_SET_GPS_RATE;
        commandRequest.data[1] = (byte) gpsRate;
        commandRequest.size = 2;

        serializeRequest(commandRequest);

        return commandRequest;
    }

    public static RDCommandRequest requestForSetTimezone(int offsetFromGMT) {
        RDCommandRequest commandRequest = new RDCommandRequest();

        commandRequest.data = new byte[2];
        commandRequest.data[0] = RDS_CMS_SET_GPS_RATE;
        commandRequest.data[1] = (byte) offsetFromGMT;
        commandRequest.size = 2;

        serializeRequest(commandRequest);

        return commandRequest;
    }

    public static RDCommandRequest requestForDeviceInfo() {
        RDCommandRequest commandRequest = new RDCommandRequest();

        commandRequest.data = new byte[1];
        commandRequest.data[0] = RDS_CMS_GET_DEVICE_INFO;
        commandRequest.size = 1;

        serializeRequest(commandRequest);

        return commandRequest;
    }

    public static RDCommandRequest request(byte cmd) {
        RDCommandRequest commandRequest = new RDCommandRequest();

        commandRequest.data = new byte[1];
        commandRequest.data[0] = cmd;
        commandRequest.size = 1;

        serializeRequest(commandRequest);

        return commandRequest;
    }

    public static RDCommandRequest requestForInitFirmwareUpdate(long firmwareSize) {
        RDCommandRequest commandRequest = new RDCommandRequest();

        commandRequest.data = new byte[5];
        commandRequest.data[0] = RDS_CMD_FW_UPDATE_MODE;
        commandRequest.data[1] = (byte) (firmwareSize);
        commandRequest.data[2] = (byte) (firmwareSize >>> 8);
        commandRequest.data[3] = (byte) (firmwareSize >>> 16);
        commandRequest.data[4] = (byte) (firmwareSize >>> 24);
        commandRequest.size = 5;

        serializeRequest(commandRequest);

        return commandRequest;
    }

    //copy data and add crc8
    private static void serializeRequest(RDCommandRequest commandRequest) {
        if (commandRequest != null) {
            byte[] data = commandRequest.data;

            byte[] serializedData = new byte[data.length + 1];

            System.arraycopy(data, 0, serializedData, 0, data.length);

            serializedData[data.length] = Crc8(data, data.length);

            commandRequest.data = serializedData;
            commandRequest.size++;
        }
    }

    public static int deserializeResponse(byte[] data, short length, RDCommandResponse response) {
        if (length > 20 || length < 2) {
            response.status = RDS_STATUS_INVALID_LENGTH;
        } else {
            byte readCrc8 = data[length - 1];
            byte computedCrc8 = Crc8(Arrays.copyOfRange(data, 0, length - 1), length - 1);

            if (readCrc8 == computedCrc8) {
                int dataLength = length - 1 - 1 - 1;

                response.cmd = data[0];
                response.code = data[1];
                response.data = Arrays.copyOfRange(data, 2, 2 + dataLength);

                response.dataLength = dataLength;
                response.status = RDS_STATUS_NO_ERR;
            } else {
                response.status = RDS_STATUS_INVALID_CRC;
            }
        }

        return response.status;
    }

    public static int deviceInfoFromCmdResponse(RDCommandResponse response, RDDeviceInfo deviceInfo) {
        int status = -1;

        if (response.data.length >= 8) {
            deviceInfo.gpsRate = response.data[0];
            deviceInfo.sdCardFlags = response.data[1];
            deviceInfo.timezone = bytesToChar(new byte[]{response.data[2], response.data[3]});
            deviceInfo.build = bytesToChar(new byte[]{response.data[4], response.data[5]});
            deviceInfo.minor = response.data[6];
            deviceInfo.major = response.data[7];

            if (response.data.length >= 8 + 6) {
                deviceInfo.btMac = Arrays.copyOfRange(response.data, 8, 8 + 6);
            }

            status = 0;
        }

        return status;
    }

    public static char bytesToChar(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.rewind();
        return buffer.getChar();
    }

    public static class RDCommandRequest {
        public byte size;
        public byte[] data;
        public int status;
    }

    public static class RDCommandResponse {
        public byte cmd;
        public byte code;
        public int dataLength;
        public byte[] data;
        public int status;
    }

    public static class RDDeviceInfo {
        public byte gpsRate;
        public byte sdCardFlags;
        public char timezone;

        public char build;
        public byte minor;
        public byte major;

        public byte[] btMac;
    }
}
