package cc.fss.redrivegps;

/**
 * redrive-android
 * <p>
 * Created by Sławomir Bienia on 30/04/15.
 * Copyright © 2015 FSS Sp. z o.o. All rights reserved.
 */
public class RDData {

    public Header header;
    public Fields fields;

    public RDData() {
        header = new Header();
        fields = new Fields();
    }

    public class Header {
        public char header;
        public short flags;
    }

    public class Fields {
        public byte szToken[];
        public char satellitesNo;
        public long timeUTC;
        public int latitude;
        public int longitude;
        public short speed;
        public int heading;
        public short HDOP;
        public int height;
        public short verticalSpeed;
        public short ay;
        public short ax;
        public short yaw;
        public short driftAngle;
        public char batteryStatus;
    }
}
