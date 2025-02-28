# Caching Proxy Java

A lightweight HTTP caching proxy server implemented in Java that caches responses from origin servers to improve performance and reduce bandwidth usage.

## Features

- HTTP request caching using SHA-256 hashing for cache keys
- Transparent proxying of HTTP requests
- Full proxy mode to handle all types of requests (including HTTPS via tunneling)
- Support for most HTTP methods (GET, POST, PUT, etc.)
- HTTPS tunneling support via the CONNECT method
- Thread-based request handling for concurrent connections
- Simple command-line interface
- Cache management with option to clear all cached entries
- X-CACHE header to indicate cache hits and misses

## How It Works

This proxy server sits between client applications and origin servers. When a client sends a request:

1. The proxy calculates a unique cache key based on the request method, host, and URL path
2. If a cached response exists for that key, it returns the cached response directly (cache HIT)
3. If no cached response exists, it forwards the request to the origin server, caches the response, and returns it to the client (cache MISS)
4. Each response includes an X-CACHE header indicating whether it was served from cache

In full proxy mode, it can handle all traffic, including HTTPS connections through secure tunneling.

## Requirements

- Java 21 or higher

## Quick Start

A pre-built JAR file is included in the repository for quick usage:

```bash
java -jar caching-proxy.jar --port 8080 --origin http://example.com
```

## Installation

1. Clone this repository:
   ```bash
   git clone https://github.com/Daryl-03/caching_proxy_java.git
   cd caching_proxy_java
   ```

2. Compile the code:
   ```bash
   javac ProxyServer.java Main.java
   ```

3. Create a JAR file (optional):
   ```bash
   jar cvf caching-proxy.jar *.class
   ```

## Usage

### Running the Proxy

Run the proxy server with the following command:

```bash
java -jar caching-proxy.jar --port <PORT_NUMBER> --origin <ORIGIN_URL>
```

Or if not using a JAR:

```bash
java Main --port <PORT_NUMBER> --origin <ORIGIN_URL>
```

### Command Line Options

- `--port <number>`: (Required) The port on which the proxy server will listen
- `--origin <url>`: The origin server URL to which requests will be forwarded (required unless using --full-caching)
- `--full-caching`: Enable full proxy mode to handle all outgoing requests
- `--clear-cache`: Clear the proxy cache and exit
- `--help`, `-h`: Display help message

### Examples

1. Start the proxy on port 3000, forwarding requests to DummyJSON:
   ```bash
   java -jar caching-proxy.jar --port 3000 --origin http://dummyjson.com
   ```

2. Start the proxy in full caching mode:
   ```bash
   java -jar caching-proxy.jar --port 8080 --full-caching
   ```

3. Clear the cache:
   ```bash
   java -jar caching-proxy.jar --clear-cache
   ```

### Making Requests

#### Standard Mode (with specific origin)
When running in standard mode (with --origin specified), you need to make requests to the proxy as if it were the origin server:

```
http://localhost:<PORT>/<PATH_TO_RESOURCE>
```

For example, if your proxy is running on port 3000 and forwarding to dummyjson.com:
```
http://localhost:3000/products/1
```

You can use any web client like Postman, cURL, your browser, or other applications to make these requests.

#### Full Proxy Mode
When running in full proxy mode, configure your client (browser, system, etc.) to use the proxy as described in the "Configuring Clients" section below.

## Configuring Clients to Use the Proxy

### Browser Configuration

1. **Chrome**:
   - Settings → Advanced → System → Open your computer's proxy settings
   - Add "localhost" and your specified port

2. **Firefox**:
   - Settings → General → Network Settings → Manual proxy configuration
   - Set HTTP Proxy to "localhost" and the port to your specified port

### System-Wide Configuration

#### Windows
1. Open Settings → Network & Internet → Proxy
2. Enable "Use a proxy server"
3. Set the address to "localhost" and the port to your proxy port

#### macOS
1. System Preferences → Network → Advanced → Proxies
2. Check "Web Proxy (HTTP)" and "Secure Web Proxy (HTTPS)"
3. Set both to "localhost" and your proxy port

#### Linux
1. System Settings → Network → Network Proxy
2. Set Method to "Manual"
3. Enter "localhost" and your proxy port

## Full Proxy Mode

When running the proxy with the `--full-caching` option, the proxy will handle all HTTP/HTTPS requests from configured clients. This includes:

- HTTP requests to any origin server
- HTTPS requests through tunneling (CONNECT method)

This mode allows you to set up the proxy as a system-wide proxy for all your applications.

## Cache Management

The proxy stores cached responses in the `./cache` directory. Each response is saved as a file with a filename derived from the SHA-256 hash of the request details.

To clear the cache:
```bash
java -jar caching-proxy.jar --clear-cache
```

## Technical Details

### Caching Mechanism

- Cache keys are generated using SHA-256 hashing of the request method and URL
- Cache entries are serialized and stored as files in the cache directory
- Each cache entry contains the response body, headers, and status line

### Request Handling

- Each client connection is handled in a separate thread
- Requests are parsed to extract method, URL, HTTP version, headers, and body
- For cached responses, the proxy adds an X-CACHE: HIT header
- For non-cached responses, the proxy adds an X-CACHE: MISS header

### HTTPS Support

The proxy handles HTTPS connections via the CONNECT method:

1. Client sends a CONNECT request to establish a tunnel to the destination server
2. Proxy creates a direct connection to the destination server
3. Proxy relays encrypted data between client and server without inspection or caching (tunneling only)

## Limitations

- No cache invalidation mechanism (other than manual clearing)
- No cache expiration based on HTTP cache control headers
- No partial content caching (Range requests)
- No compression/decompression of cached content
- HTTPS traffic is tunneled but not cached (encrypted content passes through)
- Basic error handling

## Future Improvements

- Implement cache expiration based on HTTP cache-control headers
- Add support for Range requests and partial content caching
- Implement cache validation mechanisms (If-Modified-Since, ETag)
- Add cache size management and automatic pruning
- Improve error handling and logging
- Add authentication for proxy access
- Implement content compression/decompression
- Add HTTPS inspection and caching (using MITM techniques with custom certificates)
- Implement certificate management for HTTPS caching
- Add detailed metrics and monitoring dashboard

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is open source and available under the [MIT License].

## Acknowledgments

This project was inspired by the [Caching Server project on roadmap.sh](https://roadmap.sh/projects/caching-server). Check out roadmap.sh for more great project ideas and learning resources.