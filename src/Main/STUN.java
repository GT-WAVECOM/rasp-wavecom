package Main;

import Utils.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

public class STUN implements Runnable {
    private static final String TAG = "STUN";
    private int listeningPort;
    private boolean[] stunPoolStatus;
    private int stunPoolIndex;
    private String lightSailPublicIP;
    private Map<String, PoolInformation> poolQueue;

    public STUN(int port, boolean[] stunPoolStatus,
                Map<String, PoolInformation> poolQueue,
                String lightSailPublicIP, int index) {
        listeningPort = port;
        this.stunPoolStatus = stunPoolStatus;
        this.lightSailPublicIP = lightSailPublicIP;
        stunPoolIndex = index;
        this.poolQueue = poolQueue;
    }

    public static byte[] addr2bytes(IPEndPoint endPoint) {
        ArrayList<Byte> bytes = new ArrayList<>();
        // serialize ip address
        for (byte b : endPoint.getIpAddress().getAddress()) {
            bytes.add(b);
        }
        // serialize port & nat type
        ByteBuffer byteBuffer = ByteBuffer.allocate(2);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        short port = (short) endPoint.getPort();
        byteBuffer.putShort(port);

        for (byte b : byteBuffer.array()) {
            bytes.add(b);
        }

        byte[] res = new byte[bytes.size()];
        for (int i = 0; i < res.length; i++) {
            res[i] = bytes.get(i);
        }

        return res;
    }

    @Override
    public void run() {
        try {
            // binding the stun to given port
            DebugMessage.log(TAG, "STUN Socket is trying to bind to port: " + listeningPort);
            DatagramSocket communicationPort = new DatagramSocket(listeningPort);
            communicationPort.setReuseAddress(true);
            stunPoolStatus[stunPoolIndex] = true;

            boolean[] isSendingHeartBeat = new boolean[] {true};
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        InetAddress lightSailAddress = InetAddress.getByName(lightSailPublicIP);
                        byte[] heartbeat = "rasp 1".getBytes();
                        DatagramPacket heartbeatPacket;
                        while (true) {
                            if (isSendingHeartBeat[0]) {
                                DebugMessage.log(TAG, "Sending heartbeat...");
                                heartbeatPacket = new DatagramPacket(heartbeat, heartbeat.length, lightSailAddress, 7000);
                                communicationPort.send(heartbeatPacket);
                            }
                            Thread.sleep(3000);
                        }
                    } catch (Exception e) {
                        DebugMessage.log(TAG, "Heartbeat thread error");
                    }
                }
            }).start();

            //start listening to the port
            byte[] buffer = new byte[128];
//            while (true) {
//                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
//                communicationPort.receive(packet);
//                String msg = new String(packet.getData(), 0, packet.getLength());
//                IPEndPoint incomingAddress = new IPEndPoint(packet.getAddress(), packet.getPort());
//
//                if (msg.startsWith("del")) { // voice call cancel request
//                    DebugMessage.log(TAG, "Communication cancel requested");
//                    String pool = msg.substring(4);
//                    poolQueue.remove(pool);
//                    byte[] response = "cancel!!".getBytes();
//                    DatagramPacket resp = new DatagramPacket(response, response.length, incomingAddress.getIpAddress(), incomingAddress.getPort());
//                    communicationPort.send(resp);
//                    DebugMessage.log(TAG, "cancel request before connecting");
//                } else {
//                    DebugMessage.log(TAG, String.format("Connection from %s:%d", incomingAddress.getIpAddress().toString(), incomingAddress.getPort()));
//
//                    String[] data = msg.trim().split(" ", 0);
//                    if (data.length != 2) {
//                        DebugMessage.log(TAG, "Malformed request, discarded...");
//                        continue;
//                    }
//
//                    String pool = data[0];
//                    DeviceType deviceType = DeviceType.getDeviceType(data[1]);
//                    DebugMessage.log(TAG, String.format("Stun server %d received request from %s for pool \"%s\"", this.stunPoolIndex, data[1], pool));
//
//                    byte[] resp;
//                    if (!poolQueue.containsKey(pool)) {
//
//                        if (deviceType == DeviceType.MobilePhone) {
//                            resp = "offline!".getBytes();
//                            DatagramPacket respPacket = new DatagramPacket(resp, resp.length, incomingAddress.getIpAddress(), incomingAddress.getPort());
//                            communicationPort.send(respPacket);
//                            continue;
//                        }
//
//                        PoolInformation info = new PoolInformation(incomingAddress, deviceType);
//                        poolQueue.put(pool, info);
//
//                        resp = new byte[] {'1'};
//                        DatagramPacket respPacket = new DatagramPacket(resp, resp.length, incomingAddress.getIpAddress(), incomingAddress.getPort());
//                        communicationPort.send(respPacket);
//                    } else {
//                        PoolInformation peerInfo = poolQueue.get(pool);
//                        // if the request exits, waiting for phone to join
//                        if (deviceType == DeviceType.ESP32 && !peerInfo.isCallGoingOn()) {
//                            PoolInformation info = new PoolInformation(incomingAddress, deviceType);
//                            poolQueue.put(pool, info);
//
//                            resp = new byte[] {'1'};
//                            DatagramPacket respPacket = new DatagramPacket(resp, resp.length, incomingAddress.getIpAddress(), incomingAddress.getPort());
//                            communicationPort.send(respPacket);
//                            continue;
//                        }
//
//                        if (!peerInfo.isCallGoingOn()) {
//                            // initiate the turn socket and share with device and phone
//                            Random rand = new Random();
//                            DatagramSocket turnSocket = null;
//                            boolean isTurnPortValid = false;
//                            int turnPort = listeningPort + 1;
//
//                            while (!isTurnPortValid) {
//                                try {
//                                    turnPort = listeningPort + rand.nextInt(1000) + 1;
//                                    DebugMessage.log(TAG, "Turn server is trying to bind to port: " + turnPort);
//                                    turnSocket = new DatagramSocket(turnPort);
//                                    isTurnPortValid = true;
//                                    turnSocket.setReuseAddress(true);
//                                } catch (SocketException e) {
//                                    DebugMessage.log(TAG, "Turn port " + turnPort + " is not available, retrying...");
//                                }
//                            }
//
//                            DebugMessage.log(TAG, "Turn server is listening on port: " + turnSocket.getLocalPort());
//
//                            InetAddress turnAddress = InetAddress.getByName(lightSailPublicIP);
//                            byte[] turnBuf = addr2bytes(new IPEndPoint(turnAddress, turnPort));
//
//                            DatagramPacket peerPacket = new DatagramPacket(turnBuf, turnBuf.length,
//                                    incomingAddress.getIpAddress(), incomingAddress.getPort());
//
//                            DatagramPacket currPacket = new DatagramPacket(turnBuf, turnBuf.length,
//                                    peerInfo.getEndPoint().getIpAddress(), peerInfo.getEndPoint().getPort());
//
//                            communicationPort.send(peerPacket);
//                            communicationPort.send(currPacket);
//
//                            // initiate the turn thread
//                            DebugMessage.log(TAG, "Hurray! symmetric chat link established.");
//                            DebugMessage.log(TAG, "======== transfer to turn server =======\n");
//
//                            TURN turn = new TURN(turnSocket, new PoolInformation(incomingAddress, deviceType), new PoolInformation(peerInfo.getEndPoint(), peerInfo.getDeviceType()), pool, poolQueue);
//                            Thread turnThread = new Thread(turn);
//                            turnThread.setDaemon(true);
//                            turnThread.start();
//
//                            peerInfo.setEndPoint(new IPEndPoint(turnAddress, turnPort));
//                            peerInfo.setCallGoingOn(true);
//
//                        } else { // voice communication has been initiated, handle the device retry request
//                            DebugMessage.log(TAG, "Retry request for the TURN server from " + data[1]);
//                            PoolInformation currInfo = poolQueue.get(pool);
//                            byte[] turnBuf = addr2bytes(currInfo.getEndPoint());
//                            DatagramPacket currPacket = new DatagramPacket(turnBuf, turnBuf.length,
//                                    incomingAddress.getIpAddress(), incomingAddress.getPort());
//                            communicationPort.send(currPacket);
//                            DebugMessage.log(TAG, "resend: " + currInfo.getEndPoint());
//                        }
//                    }
//                }
//            }
        } catch (SocketException e) {
            System.out.println("Binding to port " + listeningPort + " fail");
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Other error in Stun");
            e.printStackTrace();
        } finally {
            stunPoolStatus[stunPoolIndex] = false;
        }
    }
}
