package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.impl.mapbased.server.Server;
import com.perforce.p4java.server.IOptionsServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class TraceHelper {

	public static class ParseResult {
		private final Map<String, Integer> flags;
		private final List<String> warnings;

		public ParseResult(Map<String, Integer> flags, List<String> warnings) {
			this.flags = flags;
			this.warnings = warnings;
		}

		public Map<String, Integer> getFlags() {
			return flags;
		}

		public List<String> getWarnings() {
			return warnings;
		}
	}

	public static void applyTraceFlags(IOptionsServer server, String traceFlags, Logger log) {
		if (traceFlags == null || traceFlags.trim().isEmpty()) {
			return;
		}

		try {
			ParseResult result = parseTraceFlags(traceFlags);

			for (String warning : result.getWarnings()) {
				log.warning(warning);
			}

			if (result.getFlags().isEmpty()) {
				return;
			}

			// Cast to Server implementation to access setServerProtocolDebugLevel
			Server serverImpl = (Server) server;

			for (Map.Entry<String, Integer> entry : result.getFlags().entrySet()) {
				String protocol = entry.getKey();
				int level = entry.getValue();
				serverImpl.setServerProtocolDebugLevel(protocol, level);
				log.fine("Applied trace flag: " + protocol + "=" + level);
			}
		} catch (Exception e) {
			log.warning("Failed to apply trace flags '" + traceFlags + "': " + e.getMessage());
		}
	}

	public static ParseResult parseTraceFlags(String traceFlags) {
		Map<String, Integer> flags = new HashMap<>();
		List<String> warnings = new ArrayList<>();

		if (traceFlags == null || traceFlags.trim().isEmpty()) {
			return new ParseResult(flags, warnings);
		}

		String[] pairs = traceFlags.split("[,;]");
		for (String pair : pairs) {
			pair = pair.trim();
			if (pair.isEmpty()) {
				continue;
			}

			String[] parts = pair.split("=");
			if (parts.length != 2) {
				warnings.add("Invalid trace flag format (expected 'protocol=level'): " + pair);
				continue;
			}

			String protocol = parts[0].trim();
			String levelStr = parts[1].trim();

			try {
				int level = Integer.parseInt(levelStr);
				flags.put(protocol, level);
			} catch (NumberFormatException e) {
				warnings.add("Invalid trace level (not a number): " + levelStr);
			}
		}

		return new ParseResult(flags, warnings);
	}
}
