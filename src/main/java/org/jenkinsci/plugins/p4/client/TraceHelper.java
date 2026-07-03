package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.server.IOptionsServer;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class TraceHelper {

	private static Logger logger = Logger.getLogger(TraceHelper.class.getName());

	public static void applyTraceFlags(IOptionsServer server, String traceFlags, Logger log) {
		if (traceFlags == null || traceFlags.trim().isEmpty()) {
			return;
		}

		try {
			Map<String, Integer> flags = parseTraceFlags(traceFlags);
			if (flags.isEmpty()) {
				return;
			}

			for (Map.Entry<String, Integer> entry : flags.entrySet()) {
				String protocol = entry.getKey();
				int level = entry.getValue();
				server.setTrace(protocol, level);
				log.fine("Applied trace flag: " + protocol + "=" + level);
			}
		} catch (Exception e) {
			logger.warning("Failed to apply trace flags '" + traceFlags + "': " + e.getMessage());
		}
	}

	public static Map<String, Integer> parseTraceFlags(String traceFlags) {
		Map<String, Integer> flags = new HashMap<>();

		if (traceFlags == null || traceFlags.trim().isEmpty()) {
			return flags;
		}

		String[] pairs = traceFlags.split("[,;]");
		for (String pair : pairs) {
			pair = pair.trim();
			if (pair.isEmpty()) {
				continue;
			}

			String[] parts = pair.split("=");
			if (parts.length != 2) {
				logger.warning("Invalid trace flag format (expected 'protocol=level'): " + pair);
				continue;
			}

			String protocol = parts[0].trim();
			String levelStr = parts[1].trim();

			try {
				int level = Integer.parseInt(levelStr);
				flags.put(protocol, level);
			} catch (NumberFormatException e) {
				logger.warning("Invalid trace level (not a number): " + levelStr);
			}
		}

		return flags;
	}
}
