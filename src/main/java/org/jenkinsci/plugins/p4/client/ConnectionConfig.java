package org.jenkinsci.plugins.p4.client;

import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;

import java.io.Serializable;

public class ConnectionConfig implements Serializable {

	private static final long serialVersionUID = 2L;

	private final String p4port;
	private final boolean ssl;
	private final String serverUri;
	private final String trust;
	private final int timeout;
	private final String p4host;
	private final String userName;
	private final String traceFlags;

	public ConnectionConfig(P4BaseCredentials credential) {
		this.p4port = credential.getFullP4port();
		this.ssl = credential.getSsl() != null;
		this.trust = credential.getTrust();
		this.serverUri = credential.getP4JavaUri();
		this.timeout = credential.getTimeout();
		this.p4host = credential.getP4host();
		this.userName = credential.getUsername();
		this.traceFlags = credential.getTraceFlags();
	}

	public String getPort() {
		return p4port;
	}

	public boolean isSsl() {
		return ssl;
	}

	public String getTrust() {
		return trust;
	}

	public String getServerUri() {
		return serverUri;
	}

	public int getTimeout() {
		return timeout;
	}

	public String getP4Host() {
		return p4host;
	}

	public String getUserName() {
		return userName;
	}

	public String getTraceFlags() {
		return traceFlags;
	}

	public String toString() {
		return serverUri;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ConnectionConfig)) {
			return false;
		}
		ConnectionConfig comp = (ConnectionConfig) obj;

		if (ssl != comp.ssl || timeout != comp.timeout) {
			return false;
		}
		if (!p4port.equals(comp.p4port)) {
			return false;
		}
		if (!serverUri.equals(comp.serverUri)) {
			return false;
		}
		if (trust != null ? !trust.equals(comp.trust) : comp.trust != null) {
			return false;
		}
		if (p4host != null ? !p4host.equals(comp.p4host) : comp.p4host != null) {
			return false;
		}
		if (!userName.equals(comp.userName)) {
			return false;
		}
		return traceFlags != null ? traceFlags.equals(comp.traceFlags) : comp.traceFlags == null;
	}

	@Override
	public int hashCode() {
		int result = p4port.hashCode();
		result = 31 * result + (ssl ? 1 : 0);
		result = 31 * result + serverUri.hashCode();
		result = 31 * result + (trust != null ? trust.hashCode() : 0);
		result = 31 * result + timeout;
		result = 31 * result + (p4host != null ? p4host.hashCode() : 0);
		result = 31 * result + userName.hashCode();
		result = 31 * result + (traceFlags != null ? traceFlags.hashCode() : 0);
		return result;
	}
}
