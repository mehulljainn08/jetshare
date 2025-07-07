package p2p.java.p2p.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class FileTransferHandler implements Runnable{

    private final Socket clientSocket;
    private final String filePath;

    public FileTransferHandler(Socket clientSocket, String filePath) {
        this.clientSocket = clientSocket;
        this.filePath = filePath;
    }

    
    @Override
public void run() {
    try (
        FileInputStream fis = new FileInputStream(filePath);
        OutputStream os = clientSocket.getOutputStream()
    ) {
        String fileName = new File(filePath).getName();
        long fileSize = new File(filePath).length();

        
        String header = String.format("%-100s%-20d", fileName, fileSize); // 100 bytes name, 20 bytes size
        byte[] headerBytes = header.getBytes("UTF-8");
        byte[] paddedHeader = new byte[256];
        System.arraycopy(headerBytes, 0, paddedHeader, 0, headerBytes.length);
        os.write(paddedHeader);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }

        System.out.println("File sent: " + fileName);

    } catch (Exception e) {
        System.out.println("Error sending file: " + e.getMessage());
    } finally {
        try {
            clientSocket.close();
        } catch (IOException e) {
            System.out.println(" Error closing socket: " + e.getMessage());
        }
    }
}


}
