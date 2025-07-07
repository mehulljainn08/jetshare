package p2p.java.p2p.service;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

import p2p.java.p2p.utils.uploadUtils;

public class FileSharer {
    public HashMap<Integer, String> availableFiles;

    public FileSharer() {
        this.availableFiles = new HashMap<>();
    }
    public int offerFile(String filePath) {
        int port;
        while(true){
            port =uploadUtils.generatePort();
            if(!availableFiles.containsKey(port)) {
                availableFiles.put(port,filePath);
                return port;
            }

        }

    }

    public void startFileServer(int port) {
        String filePath = availableFiles.get(port);
        if (filePath == null) {
            System.out.println("No file available on port: " + port);
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("File server started on port: " + port + " serving file: " + new File(filePath).getName());

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                Thread fileTransferThread = new Thread(new FileTransferHandler(clientSocket, filePath));
                fileTransferThread.start();
            }

        } catch (Exception e) {
            System.out.println("Error starting file server on port: " + port);
            e.printStackTrace();
        }
    }

}
