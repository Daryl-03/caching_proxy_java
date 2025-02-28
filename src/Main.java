import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        Map<String, String> options = parseArgs(args);
        if (options.containsKey("help")) {
            printUsage();
            System.exit(0);
        }

        if (options.containsKey("clear-cache")) {
            ProxyServer.clearCache();
            System.exit(0);
        }

        if (!options.containsKey("port")) {
            System.out.println("Port is required");
            printUsage();
            System.exit(1);
        }

        if (!options.containsKey("full-caching") && !options.containsKey("origin")) {
            System.out.println("Origin URL is required");
            printUsage();
            System.exit(1);
        }
        int port = Integer.parseInt(options.get("port"));
        String origin = options.get("origin");

        ProxyServer proxyServer = new ProxyServer(port, origin);
        if (options.containsKey("full-caching")) {
            proxyServer.setFullProxyMode(true);
        }
        proxyServer.startServer();
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar caching-proxy.jar [OPTIONS]");
        System.out.println("Options:");
        System.out.println("  --port <number>      Port on which the proxy server will run");
        System.out.println("  --origin <url>       URL of the server to which requests will be forwarded");
        System.out.println("  --full-caching         Handles all outgoing requests");
        System.out.println("  --clear-cache        Clear the cache");
        System.out.println("  --help, -h           Show this help message");
        System.out.println("\nExample:");
        System.out.println("  java -jar caching-proxy.jar --port 3000 --origin http://dummyjson.com");
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                    if(i + 1 >= args.length || !args[i + 1].matches("\\d+")) {
                        System.out.println("Port number is missing");
                        printUsage();
                        System.exit(1);
                    }
                    options.put("port", args[++i]);
                    break;
                case "--origin":
                    if(i + 1 >= args.length) {
                        System.out.println("Origin URL is missing");
                        printUsage();
                        System.exit(1);
                    }
                    options.put("origin", args[++i]);
                    break;
                case "--clear-cache":
                    options.put("clear-cache", "true");
                    break;
                case "--full-caching":
                    options.put("full-caching", "true");
                    break;
                case "--help":
                case "-h":
                    options.put("help", "true");
                    break;
                default:
                    System.out.println("Invalid option: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }
        return options;
    }
}