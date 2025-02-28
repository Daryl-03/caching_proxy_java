import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProxyServer {
    private final int port;
    private final String origin;
    private boolean fullProxyMode = false;
    private static final String CACHE_DIR = "./cache";

    public ProxyServer(int port, String origin) {
        this.port = port;
        this.origin = origin;
        File cacheDir = new File(CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdir();
        }
    }

    public void setFullProxyMode(boolean fullProxyMode) {
        this.fullProxyMode = fullProxyMode;
    }

    record CacheEntry(byte[] response, Map<String, List<String>> headers, String responseFirstLine) implements Serializable {
    }

    record HttpRequest(String method, String url, String version, Map<String, List<String>> headers, byte[] body) {
    }

    private void writeCacheEntryToFile(String key, CacheEntry cacheEntry) {
        try {
            File file = new File(CACHE_DIR + File.separator + key);
            if (!file.exists()) {
                file.createNewFile();
            }
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(file))) {
                objectOutputStream.writeObject(cacheEntry);
            }
        } catch (IOException e) {
            System.out.println("Error while writing cache entry to file: " + key);
            throw new RuntimeException(e);
        }
    }

    private CacheEntry readCacheEntryFromFile(File cacheFile) {
        try {
            try (ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(cacheFile))) {
                return (CacheEntry) objectInputStream.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Error while reading cache entry from file: " + cacheFile.getName());
            throw new RuntimeException(e);
        }
    }

    public static void clearCache() {
        File cacheDir = new File(CACHE_DIR);
        if (cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }

    public void startServer() {
        try(ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Caching proxy server started on port " + port);
            System.out.println("Forwarding requests to " + origin);
            System.out.println("Press Ctrl+C to stop the server");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread thread = new Thread(() -> handleClientRequest(clientSocket));
                thread.start();
            }
        } catch (Exception e) {
            System.out.println("Error while starting proxy server");
        }
    }

    public void handleClientRequest(Socket clientSocket) {
        try {
            BufferedReader proxyToClientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream proxyToClientOutputSteam = clientSocket.getOutputStream();

            HttpRequest request = parseRequest(proxyToClientReader);
            if(request == null) {
                System.err.println("Empty request");
                return;
            }

            if (request.method.equals("CONNECT")) {
                handleConnectMethod(clientSocket, request.url);
                return;
            }

            if (request.headers().get("Host") == null) {
                System.err.println("Host header not found");
                return;
            }

            String cacheKey = createRequestHash(request);
            File cacheFile = new File(CACHE_DIR + File.separator + cacheKey);

            if(cacheFile.exists()) {
                CacheEntry cacheEntry = readCacheEntryFromFile(cacheFile);
                sendResponseToClient(proxyToClientOutputSteam, cacheEntry, true);

            } else {
                CacheEntry response = sendRequestToOriginServer(request);

                System.out.println(response.headers.getClass());
                writeCacheEntryToFile(cacheKey, response);
                sendResponseToClient(proxyToClientOutputSteam, response, false);
            }

            proxyToClientOutputSteam.close();
            clientSocket.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String createRequestHash(HttpRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Add method and path to the digest
            digest.update(request.method.getBytes(StandardCharsets.UTF_8));
            digest.update((request.headers().get("Host").getFirst() + request.url).getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to a simple hash if SHA-256 is not available
            return String.valueOf(request.method.hashCode() + request.url.hashCode());
        }
    }

    private HttpRequest parseRequest(BufferedReader proxyToClientReader) throws IOException {
        String requestLine = proxyToClientReader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }

        String[] requestLineParts = requestLine.split(" ");
        String method = requestLineParts[0];
        String url = requestLineParts[1];
        String version = requestLineParts[2];

        // parse headers
        Map<String, List<String>> headers = new HashMap<>();
        while ((requestLine = proxyToClientReader.readLine()) != null && !requestLine.isEmpty()) {
            if(requestLine.contains(":")) {
                headers.put(requestLine.split(":")[0].trim(), List.of(requestLine.split(":")[1].trim().split(", ")));
            }
        }

        if (!fullProxyMode) {
            headers.put("Host", List.of(origin));
        }

        byte[] body = null;
        if((method.equalsIgnoreCase("post") || method.equalsIgnoreCase("put")) && headers.containsKey("Content-Length")) {
            int contentLength = Integer.parseInt(headers.get("Content-Length").get(0));
            char[] content = new char[contentLength];
            proxyToClientReader.read(content, 0, contentLength);
            if(contentLength > 0) {
                body = new String(content).getBytes();
            }
        }

        return new HttpRequest(method, url, version, headers, body);
    }

    private void sendResponseToClient(OutputStream proxyToClientOutputSteam, CacheEntry response, boolean fromCache) throws IOException {
        proxyToClientOutputSteam.write(response.responseFirstLine().getBytes());
        proxyToClientOutputSteam.write("\r\n".getBytes());
        response.headers().forEach((key, value) -> {
            try {
                proxyToClientOutputSteam.write((key + ": " + String.join(", ", value) + "\r\n").getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        proxyToClientOutputSteam.write(("X-CACHE: " + (fromCache ? "HIT" : "MISS") + "\r\n").getBytes());
        System.out.println("X-CACHE: " + (fromCache ? "HIT" : "MISS"));
        proxyToClientOutputSteam.write("Connection: close\r\n".getBytes());
        proxyToClientOutputSteam.write("\r\n".getBytes());

        if (response.response() != null && response.response().length > 0) {
            proxyToClientOutputSteam.write(response.response());
        }
        proxyToClientOutputSteam.flush();
    }

    private CacheEntry sendRequestToOriginServer(HttpRequest request) {
        try {
            String method = request.method();
            String url = request.url();
            String version = request.version();
            Map<String, List<String>> headers = request.headers();

            byte[] response = null;
            URL urlObj;
            String pathStart = headers.get("Host").get(0).startsWith("http") ? "" : "http://";
            URI uri = new URI( pathStart +
                    headers.get("Host").getFirst() +url);
            urlObj = URL.of(uri, null);

            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();

            connection.setRequestMethod(method);
            headers.forEach((key, value) -> {
                if(!key.equalsIgnoreCase("Host") && !key.equalsIgnoreCase("Connection") && !key.equalsIgnoreCase("Proxy-Connection")) {
                    connection.setRequestProperty(key, String.join(", ", value));
                }
            });
            connection.setDoInput(true);
            if(request.body() != null) {
                connection.setDoOutput(true);
                connection.getOutputStream().write(request.body());
            }

            String responseFirstLine = version + " " + connection.getResponseCode() + " " + connection.getResponseMessage();

            Map<String, List<String>> responseHeaders = new HashMap<>();
            connection.getHeaderFields().forEach((key, value) -> {
                if(key != null) {
                    responseHeaders.put(key, List.copyOf(value));
                }
            });

            InputStream inputStream;
            if(connection.getResponseCode() < 400) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }

            if(inputStream != null) {
                try(ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        byteArrayOutputStream.write(buffer, 0, bytesRead);
                    }
                    response = byteArrayOutputStream.toByteArray();
                } finally {
                    inputStream.close();
                }
            }
            return new CacheEntry(response, responseHeaders, responseFirstLine);

        } catch (MalformedURLException e) {
            System.out.println("Malformed URL: " + request.url);
            throw new RuntimeException(e);
        } catch (URISyntaxException | IOException e) {
            System.out.println("Error while sending request to origin server: " + request.url);
            throw new RuntimeException(e);
        }

    }

    private void handleConnectMethod(Socket clientSocket, String url) {
        try {
            // Parse the host and port from the URL (usually in format host:port)
            String[] hostPort = url.split(":");
            String host = hostPort[0];
            int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 443; // Default HTTPS port

            // Tell the client the connection is established
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream());
            out.println("HTTP/1.1 200 Connection Established");
            out.println("Proxy-Agent: MyProxy");
            out.println();
            out.flush();

            // Create a connection to the target server
            Socket serverSocket = new Socket(host, port);

            // Start two threads to relay data in both directions
            Thread serverToClient = new Thread(() -> {
                try {
                    relay(serverSocket.getInputStream(), clientSocket.getOutputStream());
                } catch (IOException e) {
                    // Connection closed
                }
            });

            Thread clientToServer = new Thread(() -> {
                try {
                    relay(clientSocket.getInputStream(), serverSocket.getOutputStream());
                } catch (IOException e) {
                    // Connection closed
                }
            });

            serverToClient.start();
            clientToServer.start();

            // Wait for the threads to finish
            serverToClient.join();
            clientToServer.join();

            // Close the sockets
            serverSocket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void relay(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
            out.flush();
        }
    }
}

