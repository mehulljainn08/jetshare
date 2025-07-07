package p2p.java.p2p.utils;

import java.util.Random;

public class uploadUtils {
    public static int generatePort(){
        int DYNAMIC_STARTING_PORT = 49152;
        int DYNAMIC_ENDING_PORT = 65535;
        Random random = new Random();
        int port = random.nextInt(DYNAMIC_ENDING_PORT - DYNAMIC_STARTING_PORT) + DYNAMIC_STARTING_PORT;
        return port;
    }
}
