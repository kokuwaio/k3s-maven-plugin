package io.kokuwa.maven.k3s.test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for slf4j logger to capture output.
 *
 * @author stephan@schnabel.org
 * @since 1.4.0
 */
@SuppressWarnings("resource")
public class LoggerCapturer {

	private static final List<String> messages = new ArrayList<>();
	static {
		System.setErr(new PrintStream(new OutputStream() {

			private StringBuilder line = new StringBuilder();

			@Override
			public void write(int b) throws IOException {
				if (b == '\n') {
					var string = line.toString();
					System.out.println(string);
					messages.add(string);
					line.setLength(0);
				} else if (b != '\r') {
					line.append((char) b);
				}
			}
		}));
	}

	public static void clear() {
		messages.clear();
	}

	public static List<String> getMessages() {
		return messages;
	}
}
