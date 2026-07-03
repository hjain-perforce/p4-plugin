package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.PropertyDefs;
import com.perforce.p4java.exception.ConnectionException;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.mapbased.rpc.RpcPropertyDefs;
import com.perforce.p4java.option.UsageOptions;
import com.perforce.p4java.server.IOptionsServer;
import com.perforce.p4java.server.ServerFactory;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * Connection Factory
 * <p>
 * Provides concurrent connections to the Perforce Server
 *
 * @author pallen
 */
public class ConnectionFactory {

	private static Logger logger = Logger.getLogger(ConnectionFactory.class.getName());
	private static Logger traceLogger = Logger.getLogger("org.jenkinsci.plugins.p4.trace");

	private static IOptionsServer currentP4;

	/**
	 * Returns existing connection
	 *
	 * @return Server connection object
	 */
	public static IOptionsServer getConnection() {
		return currentP4;
	}

	/**
	 * Creates a server connection; provides a connection to the Perforce
	 * Server, initially client is undefined.
	 *
	 * @param config Connection configuration
	 * @return Server connection object
	 * @throws Exception push up stack
	 */
	public static IOptionsServer getConnection(ConnectionConfig config)
			throws Exception {

		IOptionsServer iserver = getRawConnection(config);

		// Connect and update current P4 connection
		try {
			iserver.connect();
		} catch (ConnectionException e) {
			if (config.isSsl() && StringUtils.isNotEmpty(config.getTrust())) {
				addTrust(iserver, config);
				iserver.connect();
			} else {
				throw e;
			}
		}
		currentP4 = iserver;
		return iserver;
	}

	// Add trust for SSL connections
	private static void addTrust(IOptionsServer iserver, ConnectionConfig config) throws P4JavaException {
		String serverTrust = iserver.getTrust();
		if (!serverTrust.equalsIgnoreCase(config.getTrust())) {
			logger.warning("Trust mismatch! Server fingerprint: " + serverTrust);
		} else {
			iserver.addTrust(config.getTrust());
			logger.fine("addTrust - ok: " + config.getTrust());
		}
	}

	public static FormValidation testConnection(ConnectionConfig config) {

		// Test for SSL connections
		try {
			IOptionsServer iserver = getRawConnection(config);
			if (config.isSsl() && StringUtils.isNotEmpty(config.getTrust())) {
				String serverTrust = iserver.getTrust();
				if (!serverTrust.equalsIgnoreCase(config.getTrust())) {
					return FormValidation.error("Trust mismatch! Server fingerprint: " + serverTrust);
				} else {
					iserver.addTrust(config.getTrust());
				}
			}
		} catch (Exception e) {
			StringBuffer sb = new StringBuffer();
			sb.append("Unable to connect to: ");
			sb.append(config.getServerUri());
			sb.append("\n");
			sb.append(e.getMessage());
			return FormValidation.error(sb.toString());
		}

		return FormValidation.ok();
	}

	private static IOptionsServer getRawConnection(ConnectionConfig config)
			throws Exception {
		Properties props = new Properties(System.getProperties());

		// Identify ourselves in server log files.
		Identifier id = new Identifier();
		props.put(PropertyDefs.PROG_NAME_KEY, id.getProduct());
		props.put(PropertyDefs.PROG_VERSION_KEY, id.getVersion());

		// Allow p4 admin commands.
		props.put(RpcPropertyDefs.RPC_RELAX_CMD_NAME_CHECKS_NICK, "true");

		// disable timeout for slow servers / large db lock times
		String timeout = String.valueOf(config.getTimeout());
		props.put(RpcPropertyDefs.RPC_SOCKET_SO_TIMEOUT_NICK, timeout);

		// enable graph depot and AndMaps
		props.put(PropertyDefs.ENABLE_GRAPH_SHORT_FORM, "true");
		props.put(PropertyDefs.ENABLE_ANDMAPS_SHORT_FORM, "true");

		props.put(RpcPropertyDefs.RPC_SECURE_SOCKET_ENABLED_PROTOCOLS_NICK, "TLSv1.3,TLSv1.2");

		// Apply trace flags if configured
		applyTraceFlags(props, config);

		// Set P4HOST if defined
		UsageOptions opts = new UsageOptions(props);
		String p4host = config.getP4Host();
		if (p4host != null && !p4host.isEmpty()) {
			opts.setHostName(p4host);
		}

		// Get a server connection
		String serverUri = config.getServerUri();
		IOptionsServer iServer = ServerFactory.getOptionsServer(serverUri, props, opts);
		iServer.setUserName(config.getUserName());

		return iServer;
	}

	private static void applyTraceFlags(Properties props, ConnectionConfig config) {
		String traceFlags = config.getTraceFlags();
		if (traceFlags == null || traceFlags.trim().isEmpty()) {
			return;
		}

		try {
			// Parse trace flags: format is "flag=level,flag2=level2" e.g., "rpc=3,time=1"
			// Build P4DEBUG environment variable format
			StringBuilder debugFlags = new StringBuilder();

			String[] flags = traceFlags.split(",");
			for (String flag : flags) {
				flag = flag.trim();
				if (flag.isEmpty()) {
					continue;
				}

				String[] parts = flag.split("=");
				if (parts.length != 2) {
					traceLogger.warning("Invalid trace flag format (expected flag=level): " + flag);
					continue;
				}

				String flagName = parts[0].trim().toLowerCase();
				String level = parts[1].trim();

				// Validate level is numeric
				try {
					Integer.parseInt(level);
				} catch (NumberFormatException e) {
					traceLogger.warning("Invalid trace level (not an integer) for flag " + flagName + ": \"" + parts[1] + "\"");
					continue;
				}

				// Build trace flag string (supported: rpc, ssl, net, time)
				switch (flagName) {
					case "rpc":
					case "ssl":
					case "net":
					case "time":
						if (debugFlags.length() > 0) {
							debugFlags.append(",");
						}
						debugFlags.append(flagName).append("=").append(level);
						traceLogger.info("Applied trace flag: " + flagName + "=" + level);
						break;
					default:
						traceLogger.warning("Unknown trace flag (supported: rpc, ssl, net, time): " + flagName);
				}
			}

			// Set P4DEBUG property if we have valid flags
			if (debugFlags.length() > 0) {
				String p4debug = debugFlags.toString();
				props.put("P4DEBUG", p4debug);
				traceLogger.info("Set P4DEBUG=" + p4debug);
			}
		} catch (Exception e) {
			traceLogger.warning("Error applying trace flags (continuing without trace): " + e.getMessage());
		}
	}
}
