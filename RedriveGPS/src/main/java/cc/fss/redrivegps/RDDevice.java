package cc.fss.redrivegps;

/**
 * redrive-android
 * <p>
 * Created by Sławomir Bienia on 02/09/16.
 * Copyright © 2016 FSS Sp. z o.o. All rights reserved.
 */

public class RDDevice {

    private String name;

    private int batteryLevel;
    private int batteryState;

    private int gpsRate;
    private int timezone;
    private boolean scCardInserted;
    private boolean sdCardRecording;

    private String firmwareVersionString;
    private String macAddress;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public int getBatteryState() {
        return batteryState;
    }

    public void setBatteryState(int batteryState) {
        this.batteryState = batteryState;
    }

    public int getGpsRate() {
        return gpsRate;
    }

    public void setGpsRate(int gpsRate) {
        this.gpsRate = gpsRate;
    }

    public int getTimezone() {
        return timezone;
    }

    public void setTimezone(int timezone) {
        this.timezone = timezone;
    }

    public boolean isScCardInserted() {
        return scCardInserted;
    }

    public void setScCardInserted(boolean scCardInserted) {
        this.scCardInserted = scCardInserted;
    }

    public boolean isSdCardRecording() {
        return sdCardRecording;
    }

    public void setSdCardRecording(boolean sdCardRecording) {
        this.sdCardRecording = sdCardRecording;
    }

    public String getFirmwareVersionString() {
        return firmwareVersionString;
    }

    public void setFirmwareVersionString(String firmwareVersionString) {
        this.firmwareVersionString = firmwareVersionString;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    @Override
    public String toString() {
        return "RDDevice{" +
                "name='" + name + '\'' +
                ", batteryLevel=" + batteryLevel +
                ", batteryState=" + batteryState +
                ", gpsRate=" + gpsRate +
                ", timezone=" + timezone +
                ", scCardInserted=" + scCardInserted +
                ", sdCardRecording=" + sdCardRecording +
                ", firmwareVersionString='" + firmwareVersionString + '\'' +
                ", macAddress='" + macAddress + '\'' +
                '}';
    }
}
