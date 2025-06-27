package io.kokuwa.maven.k3s;

import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

public class Application {

	public static void main(String[] args) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
		server.createContext("/health", t -> {
			t.sendResponseHeaders(200, 15);
			var os = t.getResponseBody();
			os.write("{\"status\":\"UP\"}".getBytes());
			os.close();
		});
		server.setExecutor(null);
		server.start();
	}
}
