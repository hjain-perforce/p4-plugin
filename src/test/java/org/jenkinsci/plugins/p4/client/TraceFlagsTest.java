package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.Log;
import com.perforce.p4java.server.callback.ILogCallback;
import com.perforce.p4java.server.callback.ILogCallback.LogTraceLevel;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.console.P4TraceLogging;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class TraceFlagsTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-TraceFlagsTest-p4root";

	private static final String PLUGIN_LOGGER = "org.jenkinsci.plugins.p4";

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

	@BeforeEach
	void beforeEach(JenkinsRule rule) {
		// Ensure a clean global trace callback before each test.
		Log.setLogCallback(null);
	}

	@AfterEach
	void afterEach() {
		// Never leak the global trace callback into other tests.
		Log.setLogCallback(null);
	}

	// --- Parser (AC-4: invalid tokens ignored, no exception) ---

	@Test
	void testParseValidFlags() {
		Map<String, Integer> protocols = P4BaseCredentials.parseTraceFlags("ssl=2,rpc=3,net=5,time=1");
		assertEquals(4, protocols.size());
		assertEquals(2, protocols.get("ssl"));
		assertEquals(3, protocols.get("rpc"));
		assertEquals(5, protocols.get("net"));
		assertEquals(1, protocols.get("time"));
	}

	@Test
	void testParseNullAndEmpty() {
		assertTrue(P4BaseCredentials.parseTraceFlags(null).isEmpty());
		assertTrue(P4BaseCredentials.parseTraceFlags("").isEmpty());
		assertTrue(P4BaseCredentials.parseTraceFlags("  ,  ,").isEmpty());
	}

	@Test
	void testParseIgnoresInvalidTokens() {
		// unknown protocol, non-integer level, malformed tokens and negative levels are all dropped.
		Map<String, Integer> protocols = P4BaseCredentials.parseTraceFlags(
				"bogus=1,rpc=notint,=3,net=,time,ssl=-1, rpc=3 ");
		assertEquals(1, protocols.size());
		assertEquals(3, protocols.get("rpc"));
	}

	@Test
	void testParseIsCaseInsensitiveAndTrimmed() {
		Map<String, Integer> protocols = P4BaseCredentials.parseTraceFlags(" RPC = 3 , TIME=1");
		assertEquals(2, protocols.size());
		assertEquals(3, protocols.get("rpc"));
		assertEquals(1, protocols.get("time"));
	}

	// --- Level mapping ---

	@Test
	void testToTraceLevel() {
		assertEquals(LogTraceLevel.NONE, P4TraceLogging.toTraceLevel(0));
		assertEquals(LogTraceLevel.COARSE, P4TraceLogging.toTraceLevel(1));
		assertEquals(LogTraceLevel.FINE, P4TraceLogging.toTraceLevel(2));
		assertEquals(LogTraceLevel.SUPERFINE, P4TraceLogging.toTraceLevel(3));
		assertEquals(LogTraceLevel.ALL, P4TraceLogging.toTraceLevel(5));
		assertEquals(LogTraceLevel.NONE, P4TraceLogging.toTraceLevel(-1));
	}

	// --- Credential round-trip (default off) ---

	@Test
	void testCredentialTraceFlagsRoundTrip() {
		P4PasswordImpl credential = new P4PasswordImpl(null, "id", "desc", "localhost:1666",
				null, "user", "0", "0", null, "pass");

		// Off by default.
		assertEquals("", credential.getTraceFlags());
		assertTrue(credential.getTraceProtocols().isEmpty());

		credential.setTraceFlags("  rpc=3,time=1  ");
		assertEquals("rpc=3,time=1", credential.getTraceFlags());
		Map<String, Integer> protocols = credential.getTraceProtocols();
		assertEquals(2, protocols.size());
		assertEquals(3, protocols.get("rpc"));

		// Clearing returns to off.
		credential.setTraceFlags("");
		assertEquals("", credential.getTraceFlags());
		assertTrue(credential.getTraceProtocols().isEmpty());
	}

	// --- Descriptor form validation (AC-4: invalid string still savable) ---

	@Test
	void testDoCheckTraceFlags() {
		P4PasswordImpl.DescriptorImpl descriptor = new P4PasswordImpl.DescriptorImpl();
		assertEquals(FormValidation.Kind.OK, descriptor.doCheckTraceFlags("").kind);
		assertEquals(FormValidation.Kind.OK, descriptor.doCheckTraceFlags(null).kind);
		assertEquals(FormValidation.Kind.OK, descriptor.doCheckTraceFlags("rpc=3,time=1").kind);
		// Unrecognised token warns but never errors, so the value can still be saved.
		assertEquals(FormValidation.Kind.WARNING, descriptor.doCheckTraceFlags("bogus=1").kind);
	}

	// --- Callback routes into the plugin Logger ---

	@Test
	void testP4TraceLoggingRoutesToPluginLogger() {
		Logger logger = Logger.getLogger(PLUGIN_LOGGER);
		Level previous = logger.getLevel();
		CapturingHandler handler = new CapturingHandler();
		handler.setLevel(Level.ALL);
		logger.setLevel(Level.ALL);
		logger.addHandler(handler);
		try {
			P4TraceLogging callback = new P4TraceLogging(LogTraceLevel.ALL);
			assertEquals(LogTraceLevel.ALL, callback.getTraceLevel());

			callback.internalError("boom-error");
			callback.internalWarn("boom-warn");
			callback.internalInfo("boom-info");
			callback.internalStats("boom-stats");
			callback.internalTrace(LogTraceLevel.FINE, "boom-trace");
			callback.internalException(new RuntimeException("boom-ex"));

			List<String> messages = new ArrayList<>();
			for (LogRecord record : handler.records) {
				messages.add(record.getMessage());
			}
			assertTrue(messages.contains("boom-error"));
			assertTrue(messages.contains("boom-warn"));
			assertTrue(messages.contains("boom-info"));
			assertTrue(messages.contains("boom-stats"));
			assertTrue(messages.contains("boom-trace"));
		} finally {
			logger.removeHandler(handler);
			logger.setLevel(previous);
		}
	}

	@Test
	void testP4TraceLoggingRejectsNullLevel() {
		// A null level indicates a programming error and must be rejected explicitly.
		assertThrows(NullPointerException.class, () -> new P4TraceLogging(null));
	}

	// --- Integration: callback wired into the live connection ---

	@Test
	void testTraceFlagsRegisterCallbackOnConnection() throws Exception {
		P4PasswordImpl credential = createCredentials("jenkins", "jenkins", p4d.getRshPort(), "traceOn");
		credential.setTraceFlags("rpc=3,time=1");

		try (ConnectionHelper p4 = new ConnectionHelper(credential)) {
			assertTrue(p4.isConnected());

			// AC-1: a trace callback is registered on the connection used by the plugin.
			ILogCallback callback = Log.getLogCallback();
			assertTrue(callback instanceof P4TraceLogging);
			// The global callback uses the highest configured level: max(rpc=3, time=1)
			// == 3 -> SUPERFINE. Picking min (1 -> COARSE) would fail this assertion.
			assertEquals(LogTraceLevel.SUPERFINE, callback.getTraceLevel());

			// Running a command through the plugin connection does not throw.
			p4.login();
		}
	}

	@Test
	void testNoTraceFlagsRegistersNoCallback() throws Exception {
		P4PasswordImpl credential = createCredentials("jenkins", "jenkins", p4d.getRshPort(), "traceOff");

		try (ConnectionHelper p4 = new ConnectionHelper(credential)) {
			assertTrue(p4.isConnected());
			// AC-2: no flags -> no callback, behaviour unchanged.
			assertNull(Log.getLogCallback());
		}
	}

	@Test
	void testClearingTraceFlagsRemovesCallback() throws Exception {
		// Enable first so a callback is installed globally.
		P4PasswordImpl on = createCredentials("jenkins", "jenkins", p4d.getRshPort(), "traceEnable");
		on.setTraceFlags("rpc=3");
		try (ConnectionHelper p4 = new ConnectionHelper(on)) {
			assertTrue(Log.getLogCallback() instanceof P4TraceLogging);
		}

		// AC-3: a subsequent connection with cleared flags removes the callback.
		P4PasswordImpl off = createCredentials("jenkins", "jenkins", p4d.getRshPort(), "traceClear");
		assertEquals("", off.getTraceFlags());
		try (ConnectionHelper p4 = new ConnectionHelper(off)) {
			assertNull(Log.getLogCallback());
		}
	}

	@Test
	void testInvalidTraceFlagsDoNotBreakConnection() throws Exception {
		// AC-4: an invalid trace flag string must not fail the connection.
		P4PasswordImpl credential = createCredentials("jenkins", "jenkins", p4d.getRshPort(), "traceInvalid");
		credential.setTraceFlags("this is not valid,rpc=notanumber");

		try (ConnectionHelper p4 = new ConnectionHelper(credential)) {
			assertTrue(p4.isConnected());
			// All tokens dropped -> no callback, connection still fine.
			assertNull(Log.getLogCallback());
		}
	}

	private static class CapturingHandler extends Handler {
		private final List<LogRecord> records = new ArrayList<>();

		@Override
		public void publish(LogRecord record) {
			records.add(record);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}
	}
}
