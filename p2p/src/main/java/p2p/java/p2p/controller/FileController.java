// File: p2p/java/p2p/controller/FileController.java
package p2p.java.p2p.controller;

import com.sun.net.httpserver.*;
import org.apache.commons.io.IOUtils;
import p2p.java.p2p.service.FileSharer;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

public class FileController {
    private final FileSharer fileSharer;
    private final HttpServer server;
    private final String uploadDir;
    private final ExecutorService executorService;

    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.uploadDir = System.getProperty("java.io.tmpdir") + File.separator + "uploads";
        this.executorService = Executors.newFixedThreadPool(10);

        File uploadDirFile = new File(uploadDir);
        if (!uploadDirFile.exists()) uploadDirFile.mkdirs();

        server.createContext("/upload", new UploadHandler(uploadDir, fileSharer));
        server.createContext("/download", new DownloadHandler());
        server.createContext("/", new CORSHandler());
        server.setExecutor(executorService);
    }

    public void start() {
        server.start();
        System.out.println("File server started on port: " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        executorService.shutdown();
        System.out.println("File server stopped.");
    }

    private static class CORSHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String response = "NOT FOUND";
            exchange.sendResponseHeaders(404, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private static class UploadHandler implements HttpHandler {
        private final String uploadDir;
        private final FileSharer fileSharer;

        public UploadHandler(String uploadDir, FileSharer fileSharer) {
            this.uploadDir = uploadDir;
            this.fileSharer = fileSharer;
        }

        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "Method not allowed");
                return;
            }

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.contains("multipart/form-data")) {
                sendResponse(exchange, 400, "Invalid content type");
                return;
            }

            try {
                String boundary = contentType.split("boundary=")[1];
                byte[] data = IOUtils.toByteArray(exchange.getRequestBody());
                Multiparser.ParseResult result = new Multiparser(data, boundary).parse();

                String uniqueFileName = UUID.randomUUID() + "_" + result.fileName;
                String filePath = uploadDir + File.separator + uniqueFileName;
                try (FileOutputStream fos = new FileOutputStream(filePath)) {
                    fos.write(result.fileContent);
                }

                int port = fileSharer.offerFile(filePath);
                new Thread(() -> fileSharer.startFileServer(port)).start();

                String jsonResponse = "{\"port\": " + port + "}";
                headers.add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes());
                }

            } catch (Exception e) {
                sendResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.sendResponseHeaders(statusCode, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }

        private static class Multiparser {
            private final byte[] data;
            private final String boundary;

            public Multiparser(byte[] data, String boundary) {
                this.data = data;
                this.boundary = boundary;
            }

            public ParseResult parse() {
                try {
                    String s = new String(data);
                    String filenameMarker = "filename=\"";
                    int start = s.indexOf(filenameMarker) + filenameMarker.length();
                    int end = s.indexOf("\"", start);
                    String filename = s.substring(start, end);

                    int headerEnd = s.indexOf("\r\n\r\n", end) + 4;
                    byte[] boundaryBytes = ("\r\n--" + boundary).getBytes();
                    int contentEnd = findSequence(data, boundaryBytes, headerEnd);

                    byte[] fileContent = Arrays.copyOfRange(data, headerEnd, contentEnd);
                    return new ParseResult(filename, fileContent);
                } catch (Exception e) {
                    throw new RuntimeException("Multipart parsing failed", e);
                }
            }

            private int findSequence(byte[] data, byte[] sequence, int start) {
                outer:
                for (int i = start; i < data.length - sequence.length; i++) {
                    for (int j = 0; j < sequence.length; j++) {
                        if (data[i + j] != sequence[j]) continue outer;
                    }
                    return i;
                }
                return data.length;
            }

            static class ParseResult {
                String fileName;
                byte[] fileContent;

                ParseResult(String fileName, byte[] fileContent) {
                    this.fileName = fileName;
                    this.fileContent = fileContent;
                }
            }
        }
    }

    private static class DownloadHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "Method not allowed");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String portStr = path.substring(path.lastIndexOf('/') + 1);

            try (Socket socket = new Socket("localhost", Integer.parseInt(portStr))) {
                InputStream in = socket.getInputStream();

                byte[] header = new byte[256];
                in.read(header);
                String headerStr = new String(header, "UTF-8");
                String fileName = headerStr.substring(0, 100).trim();
                long fileSize = Long.parseLong(headerStr.substring(100, 120).trim());

                File temp = File.createTempFile("download_", "_" + fileName);
                try (FileOutputStream fos = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalRead = 0;
                    while ((bytesRead = in.read(buffer)) != -1 && totalRead < fileSize) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                }

                headers.add("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                headers.add("Content-Type", Files.probeContentType(temp.toPath()));
                exchange.sendResponseHeaders(200, temp.length());
                try (OutputStream os = exchange.getResponseBody(); FileInputStream fis = new FileInputStream(temp)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = fis.read(buffer)) != -1) os.write(buffer, 0, read);
                }
                temp.delete();
            } catch (Exception e) {
                sendResponse(exchange, 500, "Download error: " + e.getMessage());
            }
        }

        private void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
            exchange.sendResponseHeaders(code, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}
