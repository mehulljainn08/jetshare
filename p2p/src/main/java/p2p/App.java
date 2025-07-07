package p2p;

import java.io.IOException;

import p2p.java.p2p.controller.FileController;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException {
        try{
            FileController controller = new FileController(8080);
            controller.start();
            System.out.println("Server started on port 8080");
            System.out.println("UI available at http://localhost:3000");
            Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    controller.stop();
                    System.out.println("Server stopped");
                })
            );
            
        }catch(Exception e){
            System.out.println("Error starting the server: " + e.getMessage());
        }
        
    }
}
