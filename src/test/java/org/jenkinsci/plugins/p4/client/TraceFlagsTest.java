package org.jenkinsci.plugins.p4.client;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.perforce.p4java.server.IOptionsServer;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class TraceFlagsTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-TraceFlagsTest-p4root";

	private static JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

	@BeforeAll
	static void beforeAll(JenkinsRule rule) {
		jenkins = rule;
	}

	@BeforeEach
	void beforeEach() throws Exception {
		SystemCredentialsProvider.getInstance().getCredentials().clear();
	}

	@Test
	void testNoTraceFlagsSet() throws Exception {
		String credId = "no-trace-cred";
		P4PasswordImpl credential = createCredentials("jenkins", "jenkins", p4d.getRshPort(), credId);

		ConnectionConfig config = new ConnectionConfig(credential);
		assertEquals("", config.getTraceFlags());

		try (ConnectionHelper p4 = new ConnectionHelper(jenkins.jenkins, credId, null)) {
			assertNotNull(p4.getConnection());
		}
	}

	@Test
	void testSingleTraceFlag() throws Exception {
		String credId = "single-trace-cred";
		P4PasswordImpl credential = createTraceFlagCredentials("jenkins", "jenkins",
				p4d.getRshPort(), credId, "rpc=3");

		ConnectionConfig config = new ConnectionConfig(credential);
		assertEquals("rpc=3", config.getTraceFlags());

		LogCapture logCapture = new LogCapture();
		try (ConnectionHelper p4 = new ConnectionHelper(jenkins.jenkins, credId, null)) {
			assertNotNull(p4.getConnection());
		}

		assertTrue(logCapture.hasMessage("Applied trace flag: rpc=3"),
				"Expected trace flag application log message");
	}

	@Test
	void testMultipleTraceFlags() throws Exception {
		String credId = "multi-trace-cred";
		P4PasswordImpl credential = createTraceFlagCredentials("jenkins", "jenkins",
				p4d.getRshPort(), credId, "rpc=3,time=1,ssl=2");

		ConnectionConfig config = new ConnectionConfig(credential);
		assertEquals("rpc=3,time=1,ssl=2", config.getTraceFlags());

		LogCapture logCapture = new LogCapture();
		try (ConnectionHelper p4 = new ConnectionHelper(jenkins.jenkins, credId, null)) {
			assertNotNull(p4.getConnection());
		}

		assertTrue(logCapture.hasMessage("Applied trace flag: rpc=3"));
		assertTrue(logCapture.hasMessage("Applied trace flag: time=1"));
		assertTrue(logCapture.hasMessage("Applied trace flag: ssl=2"));
	}

	@Test
	void testInvalidTraceFlagFormat() throws Exception {
		String credId = "invalid-format-cred";
		P4PasswordImpl credential = createTraceFlagCredentials("jenkins", "jenkins",
				p4d.getRshPort(), credId, "rpc=3,invalid_flag,time=1");

		LogCapture logCapture = new LogCapture();
		try (ConnectionHelper p4 = new ConnectionHelper(jenkins.jenkins, credId, null)) {
			assertNotNull(p4.getConnection());
		}

		assertTrue(logCapture.hasMessage("Invalid trace flag format"));
		assertTrue(logCapture.hasMessage("Applied trace flag: rpc=3"));
		assertTrue(logCapture.hasMessage("Applied trace flag: time=1"));
	}

	@Test
	void testInvalidTraceLevelNonInteger() throws Exception {
		String credId = "invalid-level-cred";
		P4PasswordImpl credential = createTraceFlagCredentials("jenkins", "jenkins",
				p4d.getRshPort(), credId, "rpc=abc,time=1");

		LogCapture logCapture = new LogCapture();
		try (ConnectionHelper p4 = new ConnectionHelper(jenkins.jenkins, credId, null)) {
			assertNotNull(p4.getConnection());
		}

		assertTrue(logCapture.hasMessage("Invalid trace level (not an integer)"));
		assertTrue(logCapture.hasMessage("Applied trace flag: time=1"));
	}

	@Test
	void testUnknownTraceFlag() throws Exception {
		String credId = "unknown-flag-cred";
		P4PasswordImpl credential = createTraceFlagCredentials("jenkins", "jenkins",
				p4d.getRshPort(), credId, "unknown=5,rpc=2");

		LogCapture logCapture = new LogCapture();
		try (ConnectionHelper p4 = new ConnectionHelper(jenkins.jenkins, credId, null)) {
			assertNotNull(p4.getConnection());
		}

		assertTrue(logCapture.hasMessage("Unknown trace flag"));
		assertTrue(logCapture.hasMessage("Applied trace flag: rpc=2"));
	}

	@Test
	void testEmptyTraceFlags() throws Exception {
		String credId = "empty-trace-cred";
		P4PasswordImpl credential = createTraceFlagCredentials("jenkins", "jenkins",
				p4d.getRshPort(), credId, "");

		ConnectionConfig config = new ConnectionConfig(credential);
		assertEquals("", config.getTraceFlags());

		try (ConnectionHelper p4 = new ConnectionHelper(jenkins.jenkins, credId, null)) {
			assertNotNull(p4.getConnection());
		}
	}

	@Test
	void testAllSupportedTraceFlags() throws Exception {
		String credId = "all-flags-cred";
		P4PasswordImpl credential = createTraceFlagCredentials("jenkins", "jenkins",
				p4d.getRshPort(), credId, "rpc=3,ssl=2,net=5,time=1");

		LogCapture logCapture = new LogCapture();
		try (ConnectionHelper p4 = new ConnectionHelper(jenkins.jenkins, credId, null)) {
			assertNotNull(p4.getConnection());
		}

		assertTrue(logCapture.hasMessage("Applied trace flag: rpc=3"));
		assertTrue(logCapture.hasMessage("Applied trace flag: ssl=2"));
		assertTrue(logCapture.hasMessage("Applied trace flag: net=5"));
		assertTrue(logCapture.hasMessage("Applied trace flag: time=1"));
	}

	@Test
	void testTraceFlagsWithWhitespace() throws Exception {
		String credId = "whitespace-cred";
		P4PasswordImpl credential = createTraceFlagCredentials("jenkins", "jenkins",
				p4d.getRshPort(), credId, " rpc = 3 , time = 1 ");

		LogCapture logCapture = new LogCapture();
		try (ConnectionHelper p4 = new ConnectionHelper(jenkins.jenkins, credId, null)) {
			assertNotNull(p4.getConnection());
		}

		assertTrue(logCapture.hasMessage("Applied trace flag: rpc=3"));
		assertTrue(logCapture.hasMessage("Applied trace flag: time=1"));
	}

	@Test
	void testTraceFlagsCleared() throws Exception {
		String credId1 = "trace-on-cred";
		P4PasswordImpl credential1 = createTraceFlagCredentials("jenkins", "jenkins",
				p4d.getRshPort(), credId1, "rpc=3");

		LogCapture logCapture1 = new LogCapture();
		try (ConnectionHelper p4 = new ConnectionHelper(jenkins.jenkins, credId1, null)) {
			assertNotNull(p4.getConnection());
		}
		assertTrue(logCapture1.hasMessage("Applied trace flag: rpc=3"));
		logCapture1.close();

		String credId2 = "trace-off-cred";
		P4PasswordImpl credential2 = createCredentials("jenkins", "jenkins",
				p4d.getRshPort(), credId2);

		LogCapture logCapture2 = new LogCapture();
		try (ConnectionHelper p4 = new ConnectionHelper(jenkins.jenkins, credId2, null)) {
			assertNotNull(p4.getConnection());
		}

		assertFalse(logCapture2.hasMessage("Applied trace flag"),
				"Expected no trace flag application when cleared");
		logCapture2.close();
	}

	private P4PasswordImpl createTraceFlagCredentials(String user, String password,
			String p4port, String id, String traceFlags) throws IOException {
		CredentialsScope scope = CredentialsScope.GLOBAL;
		P4PasswordImpl auth = new P4PasswordImpl(scope, id, "desc", p4port, null, user, "0", "0", null, password);
		auth.setTraceFlags(traceFlags);
		SystemCredentialsProvider.getInstance().getCredentials().add(auth);
		SystemCredentialsProvider.getInstance().save();
		return auth;
	}

	private static class LogCapture extends Handler {
		private final List<LogRecord> records = new ArrayList<>();
		private final Logger traceLogger = Logger.getLogger("org.jenkinsci.plugins.p4.trace");

		public LogCapture() {
			traceLogger.addHandler(this);
			traceLogger.setLevel(Level.ALL);
		}

		@Override
		public void publish(LogRecord record) {
			records.add(record);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
			traceLogger.removeHandler(this);
		}

		public boolean hasMessage(String substring) {
			return records.stream()
					.anyMatch(r -> r.getMessage() != null && r.getMessage().contains(substring));
		}
	}
}
