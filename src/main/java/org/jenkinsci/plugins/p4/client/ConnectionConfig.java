package org.jenkinsci.plugins.p4.client;

import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;

import java.io.Serializable;
import java.util.Objects;

public class ConnectionConfig implements Serializable {

	private static final long serialVersionUID = 1L;

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
		if (this == obj) return true;
		if (!(obj instanceof ConnectionConfig)) return false;
		ConnectionConfig comp = (ConnectionConfig) obj;
		return ssl == comp.ssl &&
				timeout == comp.timeout &&
				Objects.equals(p4port, comp.p4port) &&
				Objects.equals(serverUri, comp.serverUri) &&
				Objects.equals(trust, comp.trust) &&
				Objects.equals(p4host, comp.p4host) &&
				Objects.equals(userName, comp.userName) &&
				Objects.equals(traceFlags, comp.traceFlags);
	}

	@Override
	public int hashCode() {
		return Objects.hash(p4port, ssl, serverUri, trust, timeout, p4host, userName, traceFlags);
	}
}
