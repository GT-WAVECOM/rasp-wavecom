package Main;

import Utils.*;

import javax.crypto.spec.DESedeKeySpec;
import java.net.*;
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
    private InetAddress heartBeatAddress;
    private int heartBeatPort;
    private byte[] heartbeat;
    private Map<String, PoolInformation> sourcePool;

    public STUN(int port, boolean[] stunPoolStatus,
                Map<String, PoolInformation> sourcePool,
                String lightSailPublicIP, int index) {
        listeningPort = port;
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

            //start listening to the port
            byte[] buffer = new byte[512];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                communicationPort.receive(packet);
                DebugMessage.log(TAG, packet.getLength() + "");
                if (packet.getLength() == 6) {
                    IPEndPoint phoneAdd = byte2addr(packet.getData());
                    DebugMessage.log(TAG, phoneAdd.toString());
                    heartBeatAddress = phoneAdd.getIpAddress();
                    heartBeatPort = phoneAdd.getPort();
                    heartbeat = "1".getBytes();
                } else if (packet.getLength() == 1) { // either response from stun (1 -> phone offline) or heartbeat (2) from phone
                    String msg = new String(packet.getData(), 0, packet.getLength());
                } else if (packet.getLength() == 8) { // LC STOP
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    DebugMessage.log(TAG, msg);
                    heartBeatAddress = InetAddress.getByName(lightSailPublicIP);
                    heartBeatPort = 7000;
                    heartbeat = "rasp 1".getBytes();
                }

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
