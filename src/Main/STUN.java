package Main;

import Utils.*;

import javax.crypto.spec.DESedeKeySpec;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

public class STUN implements Runnable {
    private static final String TAG = "STUN";
    private int listeningPort;
    private int sourcePort;
    private boolean[] stunPoolStatus;
    private int stunPoolIndex;
    private String lightSailPublicIP;
    private InetAddress heartBeatAddress;
    private int heartBeatPort;
    private byte[] heartbeat;
    private boolean isPhoneOnline;
    private IPEndPoint phoneAdd;
    private Map<String, IPEndPoint> sourcePool;

    public STUN(int port, boolean[] stunPoolStatus,
                Map<String, IPEndPoint> sourcePool,
                String lightSailPublicIP, int index) {
        listeningPort = port;
        sourcePort = 6666;
        this.stunPoolStatus = stunPoolStatus;
        this.lightSailPublicIP = lightSailPublicIP;
        stunPoolIndex = index;
        this.sourcePool = sourcePool;
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

    private IPEndPoint byte2addr(byte[] data) throws UnknownHostException
    {
        int offset = 0;
        //Address
        byte[] ip = new byte[4];
        ip[0] = data[offset++];
        ip[1] = data[offset++];
        ip[2] = data[offset++];
        ip[3] = data[offset++];

        //Port
        int firstDigit = byte2int(data[offset++]);
        int secondDigit = byte2int(data[offset++]);
        int port = (firstDigit | secondDigit << 8);

        return new IPEndPoint(InetAddress.getByAddress(ip), port);
    }

    private int byte2int(byte input) {
        int res = 0;
        for (int i = 0; i < 8; i++) {
            int mask = 1 << i;
            res |= input & mask;
        }
        return res;
    }

    @Override
    public void run() {
        try {
            // binding the stun to given port
            DebugMessage.log(TAG, "STUN Socket is trying to bind to port: " + listeningPort);
            // communication port is used for communication with light sail server
            DatagramSocket communicationPort = new DatagramSocket(listeningPort);
            communicationPort.setReuseAddress(true);
            stunPoolStatus[stunPoolIndex] = true;

            // heart beat thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        heartBeatAddress = InetAddress.getByName(lightSailPublicIP);
                        heartBeatPort = 7000;
                        heartbeat = "rasp 1".getBytes();
                        DatagramPacket heartbeatPacket;
                        while (true) {
                            DebugMessage.log(TAG, "Sending heartbeat to: " + heartBeatAddress.toString() + heartBeatPort);
                            heartbeatPacket = new DatagramPacket(heartbeat, heartbeat.length, heartBeatAddress, heartBeatPort);
                            communicationPort.send(heartbeatPacket);
                            Thread.sleep(3000);
                        }
                    } catch (Exception e) {
                        DebugMessage.log(TAG, "Heartbeat thread error");
                    }
                }
            }).start();

            // thread listening to devices (source pool)
            DebugMessage.log(TAG, "Source Socket is trying to bind to port: " + sourcePort);
            // communication port is used for communication with light sail server
            DatagramSocket sourceSocket = new DatagramSocket(sourcePort);
            sourceSocket.setReuseAddress(true);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    DatagramPacket[] sourcePackets = new DatagramPacket[100];
                    byte[][] sourceBuffer = new byte[100][516];
                    for (int i = 0; i < 100; i++) {
                        sourcePackets[i] = new DatagramPacket(sourceBuffer[i], sourceBuffer[i].length);
                    }

                    int sourceBufferIndex = 0;
                    IPEndPoint incomingAddr;
                    DatagramPacket sourceSoundPacket;
                    try {
                        while (true) {
                            sourceSocket.receive(sourcePackets[sourceBufferIndex]);
                            String mac = new String(sourceBuffer[sourceBufferIndex], 0, 4);

                            incomingAddr = sourcePool.get(mac);
                            // update the source pool
                            if ( incomingAddr == null || !incomingAddr.getIpAddress().equals(sourcePackets[sourceBufferIndex].getAddress()) || incomingAddr.getPort() != sourcePackets[sourceBufferIndex].getPort()) {
                                incomingAddr = new IPEndPoint(sourcePackets[sourceBufferIndex].getAddress(), sourcePackets[sourceBufferIndex].getPort());
                                sourcePool.put(mac, incomingAddr);
                            }

                            if (isPhoneOnline) {
                                // forward the sound packet to device
                                DebugMessage.log(TAG, sourcePackets[sourceBufferIndex].getLength() + "");
                                sourceSoundPacket = new DatagramPacket(sourceBuffer[sourceBufferIndex], 4, sourceBuffer[sourceBufferIndex].length - 4, phoneAdd.getIpAddress(), phoneAdd.getPort());
                                communicationPort.send(sourceSoundPacket);
                            }

                            sourceBufferIndex = (sourceBufferIndex + 1) % 100;
                        }
                    } catch (IOException e) {
                        DebugMessage.log(TAG, "source socket error");
                        e.printStackTrace();
                    }
                }
            }).start();

            //start listening to the port
            DatagramPacket[] packets = new DatagramPacket[100];
            byte[][] buffer = new byte[100][512];
            for (int i = 0; i < 100; i++) {
                packets[i] = new DatagramPacket(buffer[i], buffer[i].length);
            }

            int bufferIndex = 0;
            String mac;
            IPEndPoint testTarget;
            byte[] mac_bytes;
            byte[] toSend = new byte[516];
            DatagramPacket toSendPacket = new DatagramPacket(toSend, toSend.length, null, 0);

            while (true) {
                communicationPort.receive(packets[bufferIndex]);
                if (packets[bufferIndex].getLength() == 6) {
                    phoneAdd = byte2addr(buffer[bufferIndex]);
                    DebugMessage.log(TAG, phoneAdd.toString());
                    heartBeatAddress = phoneAdd.getIpAddress();
                    heartBeatPort = phoneAdd.getPort();
                    heartbeat = "1".getBytes();
                    isPhoneOnline = true;
                } else if (packets[bufferIndex].getLength() == 1) { // either response from stun (1 -> phone offline) or heartbeat (2) from phone
                    String msg = new String(buffer[bufferIndex], 0, packets[bufferIndex].getLength());
                } else if (packets[bufferIndex].getLength() == 8) { // LC STOP
                    String msg = new String(buffer[bufferIndex], 0, packets[bufferIndex].getLength());
                    DebugMessage.log(TAG, msg);
                    heartBeatAddress = InetAddress.getByName(lightSailPublicIP);
                    heartBeatPort = 7000;
                    heartbeat = "rasp 1".getBytes();
                    isPhoneOnline = false;
                } else if (packets[bufferIndex].getLength() == 512) { // sound packets
                    if (!sourcePool.isEmpty()) {
                        // forward the sound to device
                        mac = (String) sourcePool.keySet().toArray()[0];
                        testTarget = sourcePool.get(mac);
                        mac_bytes = mac.getBytes();
                        int i = 0;
                        for (; i < 4; i++) {
                            toSend[i] = mac_bytes[i];
                        }
                        for (; i < 516; i++) {
                            toSend[i] = buffer[bufferIndex][i - 4];
                        }

                        toSendPacket.setAddress(testTarget.getIpAddress());
                        toSendPacket.setPort(testTarget.getPort());
                        sourceSocket.send(toSendPacket);
                    }
                    // if source pool is empty, discard the packet
                }
                bufferIndex = (bufferIndex + 1) % 100;
            }
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
