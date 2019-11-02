package Main;

import Utils.IPEndPoint;
import Utils.PoolInformation;

import java.util.HashMap;
import java.util.Map;

public class VoipServer {
    private String lightSailPublicIP;
    private int numOfSTUN;
    private boolean[] stunPoolStatus;
    private Map<String, IPEndPoint> sourcePool;

    public VoipServer(String lightSailPublicIP, int numOfSTUN) {
        this.lightSailPublicIP = lightSailPublicIP;
        this.numOfSTUN = numOfSTUN;
        stunPoolStatus = new boolean[numOfSTUN];
        sourcePool = new HashMap<>();
    }

    public void serverInit() {
        for (int i = 0; i < numOfSTUN; i++) {
            STUN stun = new STUN(7000 + i * 1000, stunPoolStatus, sourcePool,
                    lightSailPublicIP, i);
            new Thread(stun).start();
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please provide the LightSail Server IP address...");
            return;
        }
        VoipServer server = new VoipServer(args[0], 1);
        server.serverInit();
    }
}
