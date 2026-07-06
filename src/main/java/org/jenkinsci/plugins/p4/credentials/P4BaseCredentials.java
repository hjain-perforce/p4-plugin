package org.jenkinsci.plugins.p4.credentials;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class P4BaseCredentials extends BaseStandardCredentials implements P4Credentials {

	private static final long serialVersionUID = 1L;

	/**
	 * Known P4 trace protocols that may be supplied via the advanced trace flags
	 * (e.g. '-vssl', '-vrpc', '-vnet', '-vtime' at the command line). Any token
	 * whose key is not in this set is silently ignored (JENKINS: P4JENKINS-175).
	 */
	private static final Set<String> KNOWN_TRACE_PROTOCOLS = new HashSet<>(Arrays.asList(
			"ssl", "rpc", "net", "time", "track", "server", "cmd", "db", "diff",
			"dbstat", "peek", "rpl", "ob", "lbr", "map", "handle", "netdbg"));
	
	@NonNull
	private final String p4port;

	@CheckForNull
	private final TrustImpl ssl;

	@NonNull
	private final String username;

	@CheckForNull
	private final String retry;

	@CheckForNull
	private final String timeout;

	@CheckForNull
	private final String p4host;

	@CheckForNull
	private String tick;

	@DataBoundSetter
	public void setTick(String tick) {
		this.tick = tick;
	}

	private boolean sessionEnabled;

	@DataBoundSetter
	public void setSessionEnabled(boolean sessionEnabled) {
		this.sessionEnabled = sessionEnabled;
	}

	private long sessionLife;

	@DataBoundSetter
	public void setSessionLife(long sessionLife) {
		this.sessionLife = sessionLife;
	}

	@CheckForNull
	private String traceFlags;

	@DataBoundSetter
	public void setTraceFlags(String traceFlags) {
		this.traceFlags = Util.fixEmptyAndTrim(traceFlags);
	}

	/**
	 * Constructor.
	 *
	 * @param scope       the scope.
	 * @param id          the id.
	 * @param description the description.
	 * @param p4port      Perforce port
	 * @param ssl         Perforce SSL options
	 * @param username    Perforce username
	 * @param retry       Perforce connection retry option
	 * @param timeout     Perforce connection timeout option
	 * @param p4host      Perforce HOST (optional)
	 */
	public P4BaseCredentials(CredentialsScope scope, String id,
	                         String description, @NonNull String p4port,
	                         @CheckForNull TrustImpl ssl, @NonNull String username,
	                         @CheckForNull String retry, @CheckForNull String timeout,
	                         @CheckForNull String p4host) {
		super(scope, id, description);
		this.p4port = Util.fixNull(p4port);
		this.ssl = ssl;
		this.username = Util.fixNull(username);
		this.retry = retry;
		this.timeout = timeout;
		this.p4host = p4host;
	}

	public String getP4port() {
		return p4port;
	}

	/**
	 * @return p4port including 'ssl:' if set JENKINS-62253
	 */
	public String getFullP4port() {
		if(ssl == null) {
			return p4port;
		}
		return "ssl:" + p4port;
	}

	public String getP4JavaUri() {

		if (ssl != null) {
			return "p4javassl://" + p4port;
		}
		if (p4port.startsWith("rsh:")) {
			String trim = p4port.substring(4, p4port.length());
			return "p4jrsh://" + trim + " --java";
		}
		return "p4java://" + p4port;
	}

	public TrustImpl getSsl() {
		return ssl;
	}

    public boolean isSslEnabled() {
        return (ssl == null) ? false : true;
    }

	@CheckForNull
	public String getTrust() {
		return (ssl == null) ? null : ssl.getTrust();
	}

	public String getUsername() {
		return username;
	}

	public int getRetry() {
		if (retry != null && !retry.isEmpty()) {
			return Integer.parseInt(retry);
		} else {
			return 0;
		}
	}

	public int getTimeout() {
		if (timeout != null && !timeout.isEmpty()) {
			return Integer.parseInt(timeout);
		} else {
			return 0;
		}
	}

	public String getP4host() {
		return (p4host == null) ? "" : p4host;
	}

	public int getTick() {
		if (tick != null && !tick.isEmpty()) {
			return Integer.parseInt(tick);
		} else {
			return 0;
		}
	}

	public boolean isSessionEnabled() {
		return sessionEnabled;
	}

	public long getSessionLife() {
		return sessionLife;
	}

	/**
	 * @return the raw advanced trace flags string (empty when off by default).
	 */
	public String getTraceFlags() {
		return (traceFlags == null) ? "" : traceFlags;
	}

	/**
	 * @return the configured trace flags parsed into a validated map of
	 * protocol name to level (e.g. {rpc=3, time=1}). Never null.
	 */
	public Map<String, Integer> getTraceProtocols() {
		return parseTraceFlags(traceFlags);
	}

	/**
	 * Parse a comma separated 'key=level' trace flag string (e.g.
	 * 'ssl=2,rpc=3,net=5,time=1') into a validated map. Tokens are silently
	 * dropped when the key is not a known P4 trace protocol or the level is not
	 * a non-negative integer, so an invalid string never throws (P4JENKINS-175
	 * AC-4).
	 *
	 * @param traceFlags raw trace flags string (may be null or empty)
	 * @return ordered map of protocol to level; empty when nothing is set
	 */
	public static Map<String, Integer> parseTraceFlags(String traceFlags) {
		Map<String, Integer> protocols = new LinkedHashMap<>();
		if (traceFlags == null) {
			return protocols;
		}
		for (String token : traceFlags.split(",")) {
			String trimmed = token.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			int eq = trimmed.indexOf('=');
			if (eq <= 0 || eq == trimmed.length() - 1) {
				// malformed token, e.g. 'rpc', '=3' or 'rpc='
				continue;
			}
			String key = trimmed.substring(0, eq).trim().toLowerCase(Locale.ENGLISH);
			String value = trimmed.substring(eq + 1).trim();
			if (!KNOWN_TRACE_PROTOCOLS.contains(key)) {
				continue;
			}
			try {
				int level = Integer.parseInt(value);
				if (level >= 0) {
					protocols.put(key, level);
				}
			} catch (NumberFormatException e) {
				// non-integer level, ignore token
			}
		}
		return protocols;
	}

	/**
	 * Form validation shared by the credential descriptors. Empty input is valid
	 * (trace flags are off by default) and unrecognised tokens produce only a
	 * warning, never an error, so an invalid string can still be saved
	 * (P4JENKINS-175 AC-4).
	 *
	 * @param value raw trace flags string from the form
	 * @return validation result
	 */
	public static FormValidation checkTraceFlags(String value) {
		String trimmed = Util.fixEmptyAndTrim(value);
		if (trimmed == null) {
			return FormValidation.ok();
		}
		int tokens = 0;
		for (String token : trimmed.split(",")) {
			if (!token.trim().isEmpty()) {
				tokens++;
			}
		}
		Map<String, Integer> parsed = parseTraceFlags(trimmed);
		if (parsed.size() < tokens) {
			return FormValidation.warning("Some trace flags were not recognised and will be ignored. "
					+ "Use 'ssl=2,rpc=3,net=5,time=1' style values.");
		}
		return FormValidation.ok();
	}
}
