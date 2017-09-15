package cc.fss.redrivegps;

import org.altbeacon.beacon.Beacon;

import java.util.Collection;

/**
 * redrive-android
 * <p>
 * Created by Sławomir Bienia on 04/05/15.
 * Copyright © 2015 FSS Sp. z o.o. All rights reserved.
 */
public interface RDConnectorDiagnosticDelegate extends RDConnectorDelegate {
    void connectorDidSendBytes(RDConnector connector, long countOfBytes, float sendingProgress);

    void connectorDidCompleteSending(RDConnector connector);

    void connectorDidUpdateDiscoveredBeacons(RDConnector connector, Collection<Beacon> beacons);
}
