package org.jenkinsci.plugins.p4.client;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.perforce.p4java.server.IOptionsServer;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.StaticWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.Workspace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class TraceTest extends DefaultEnvironment {

	private static final Logger LOGGER = Logger.getLogger(TraceTest.class.getName());
	private static final String P4ROOT = "tmp-TraceTest-p4root";
	private static final String TRACE_CREDENTIAL = "trace-cred";

	private static JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

	@BeforeAll
	static void beforeAll(JenkinsRule rule) {
		jenkins = rule;
	}

	@BeforeEach
	void beforeEach() throws Exception {
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	void testParseTraceFlags_Valid() {
		Map<String, Integer> flags = TraceHelper.parseTraceFlags("rpc=3,time=1");
		assertEquals(2, flags.size());
		assertEquals(Integer.valueOf(3), flags.get("rpc"));
		assertEquals(Integer.valueOf(1), flags.get("time"));
	}

	@Test
	void testParseTraceFlags_Semicolon() {
		Map<String, Integer> flags = TraceHelper.parseTraceFlags("rpc=3;time=1;ssl=2");
		assertEquals(3, flags.size());
		assertEquals(Integer.valueOf(3), flags.get("rpc"));
		assertEquals(Integer.valueOf(1), flags.get("time"));
		assertEquals(Integer.valueOf(2), flags.get("ssl"));
	}

	@Test
	void testParseTraceFlags_Whitespace() {
		Map<String, Integer> flags = TraceHelper.parseTraceFlags(" rpc = 3 , time = 1 ");
		assertEquals(2, flags.size());
		assertEquals(Integer.valueOf(3), flags.get("rpc"));
		assertEquals(Integer.valueOf(1), flags.get("time"));
	}

	@Test
	void testParseTraceFlags_Empty() {
		Map<String, Integer> flags = TraceHelper.parseTraceFlags("");
		assertTrue(flags.isEmpty());

		flags = TraceHelper.parseTraceFlags(null);
		assertTrue(flags.isEmpty());

		flags = TraceHelper.parseTraceFlags("   ");
		assertTrue(flags.isEmpty());
	}

	@Test
	void testParseTraceFlags_InvalidFormat() {
		TestHandler handler = new TestHandler();
		Logger tracelog = Logger.getLogger(TraceHelper.class.getName());
		tracelog.addHandler(handler);
		tracelog.setLevel(Level.WARNING);

		Map<String, Integer> flags = TraceHelper.parseTraceFlags("invalid!!!,rpc=3");
		assertEquals(1, flags.size());
		assertEquals(Integer.valueOf(3), flags.get("rpc"));
		assertThat(handler.getLogBuffer(), containsString("Invalid trace flag format"));

		tracelog.removeHandler(handler);
	}

	@Test
	void testParseTraceFlags_InvalidLevel() {
		TestHandler handler = new TestHandler();
		Logger tracelog = Logger.getLogger(TraceHelper.class.getName());
		tracelog.addHandler(handler);
		tracelog.setLevel(Level.WARNING);

		Map<String, Integer> flags = TraceHelper.parseTraceFlags("rpc=abc,time=1");
		assertEquals(1, flags.size());
		assertEquals(Integer.valueOf(1), flags.get("time"));
		assertThat(handler.getLogBuffer(), containsString("Invalid trace level"));

		tracelog.removeHandler(handler);
	}

	@Test
	void testApplyTraceFlags_Valid() throws Exception {
		P4PasswordImpl credential = new P4PasswordImpl(
				CredentialsScope.GLOBAL, TRACE_CREDENTIAL, "desc", p4d.getRshPort(),
				null, "jenkins", "0", "0", null, "jenkins");
		credential.setTraceFlags("rpc=3");

		TestHandler handler = new TestHandler();
		Logger connLogger = Logger.getLogger(ConnectionFactory.class.getName());
		connLogger.addHandler(handler);
		connLogger.setLevel(Level.FINE);

		ConnectionConfig config = new ConnectionConfig(credential);
		IOptionsServer server = ConnectionFactory.getConnection(config);
		assertNotNull(server);

		String logOutput = handler.getLogBuffer();
		assertThat(logOutput, containsString("Applied trace flag: rpc=3"));

		connLogger.removeHandler(handler);
	}

	@Test
	void testTraceFlags_NoFlagsConfigured() throws Exception {
		P4PasswordImpl credential = new P4PasswordImpl(
				CredentialsScope.GLOBAL, TRACE_CREDENTIAL, "desc", p4d.getRshPort(),
				null, "jenkins", "0", "0", null, "jenkins");

		TestHandler handler = new TestHandler();
		Logger connLogger = Logger.getLogger(ConnectionFactory.class.getName());
		connLogger.addHandler(handler);
		connLogger.setLevel(Level.FINE);

		ConnectionConfig config = new ConnectionConfig(credential);
		IOptionsServer server = ConnectionFactory.getConnection(config);
		assertNotNull(server);

		String logOutput = handler.getLogBuffer();
		assertFalse(logOutput.contains("Applied trace flag"), "No trace flags should be applied");

		connLogger.removeHandler(handler);
	}

	@Test
	void testTraceFlags_InBuild() throws Exception {
		P4PasswordImpl credential = new P4PasswordImpl(
				CredentialsScope.GLOBAL, TRACE_CREDENTIAL, "desc", p4d.getRshPort(),
				null, "jenkins", "0", "0", null, "jenkins");
		credential.setTraceFlags("rpc=2,time=1");
		SystemCredentialsProvider.getInstance().getCredentials().add(credential);
		SystemCredentialsProvider.getInstance().save();

		TestHandler handler = new TestHandler();
		Logger connLogger = Logger.getLogger(ConnectionFactory.class.getName());
		connLogger.addHandler(handler);
		connLogger.setLevel(Level.FINE);

		FreeStyleProject project = jenkins.createFreeStyleProject("TraceFlags");
		Workspace workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(TRACE_CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		Cause.UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		String logOutput = handler.getLogBuffer();
		assertThat(logOutput, containsString("Applied trace flag: rpc=2"));
		assertThat(logOutput, containsString("Applied trace flag: time=1"));

		connLogger.removeHandler(handler);
	}

	@Test
	void testTraceFlags_ClearedAfterSet() throws Exception {
		P4PasswordImpl credential = new P4PasswordImpl(
				CredentialsScope.GLOBAL, TRACE_CREDENTIAL, "desc", p4d.getRshPort(),
				null, "jenkins", "0", "0", null, "jenkins");
		credential.setTraceFlags("rpc=3");
		SystemCredentialsProvider.getInstance().getCredentials().add(credential);
		SystemCredentialsProvider.getInstance().save();

		TestHandler handler = new TestHandler();
		Logger connLogger = Logger.getLogger(ConnectionFactory.class.getName());
		connLogger.addHandler(handler);
		connLogger.setLevel(Level.FINE);

		FreeStyleProject project = jenkins.createFreeStyleProject("TraceFlagsCleared");
		Workspace workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(TRACE_CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		Cause.UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		String logOutput = handler.getLogBuffer();
		assertThat(logOutput, containsString("Applied trace flag: rpc=3"));

		handler.clear();
		credential.setTraceFlags(null);
		SystemCredentialsProvider.getInstance().save();

		build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());

		logOutput = handler.getLogBuffer();
		assertFalse(logOutput.contains("Applied trace flag"), "No trace flags should be applied after clearing");

		connLogger.removeHandler(handler);
	}

	@Test
	void testTraceFlags_InvalidDoesNotFailBuild() throws Exception {
		P4PasswordImpl credential = new P4PasswordImpl(
				CredentialsScope.GLOBAL, TRACE_CREDENTIAL, "desc", p4d.getRshPort(),
				null, "jenkins", "0", "0", null, "jenkins");
		credential.setTraceFlags("invalid!!!garbage");
		SystemCredentialsProvider.getInstance().getCredentials().add(credential);
		SystemCredentialsProvider.getInstance().save();

		FreeStyleProject project = jenkins.createFreeStyleProject("InvalidTraceFlags");
		Workspace workspace = new StaticWorkspaceImpl("none", false, defaultClient());
		Populate populate = new AutoCleanImpl();
		PerforceScm scm = new PerforceScm(TRACE_CREDENTIAL, workspace, populate);
		project.setScm(scm);
		project.save();

		Cause.UserIdCause cause = new Cause.UserIdCause();
		FreeStyleBuild build = project.scheduleBuild2(0, cause).get();
		assertEquals(Result.SUCCESS, build.getResult());
	}
}
