package org.jenkinsci.plugins.p4.client;

import hudson.util.StreamTaskListener;
import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.jenkinsci.plugins.p4.SampleServerExtension;
import org.jenkinsci.plugins.p4.changes.P4ChangeRef;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.populate.ForceCleanImpl;
import org.jenkinsci.plugins.p4.populate.Populate;
import org.jenkinsci.plugins.p4.workspace.ManualWorkspaceImpl;
import org.jenkinsci.plugins.p4.workspace.WorkspaceSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for P4JENKINS-184.
 *
 * A populate with have:false emits 'p4 sync -p', which silently bypasses any
 * file the server believes is already synced (as happens against a read-only
 * replica), reporting the files 'added' while writing nothing to disk. A force
 * populate must combine '-p' with '-f' so the server ignores the 'already
 * synced' state and re-fetches the archive content.
 *
 * This reproduces the mechanism on the single-master harness: sync to populate
 * the have-list, wipe the workspace on disk, then re-sync with a force populate
 * ('-p -f'). Without the fix ('-p' only) the have-list still reports the files
 * synced so the disk stays empty; with the fix the archive content is
 * re-fetched. The 7000-file forwarding-replica run is the manual verification
 * named in the ticket's Definition of Done.
 */
@WithJenkins
class PopulateForceReplicaTest extends DefaultEnvironment {

	private static final String P4ROOT = "tmp-PopulateForceReplicaTest-p4root";

	private JenkinsRule jenkins;

	@RegisterExtension
	private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

	@BeforeEach
	void beforeEach(JenkinsRule rule) throws Exception {
		jenkins = rule;
		createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
	}

	@Test
	void testForcePopulateReFetchesBypassedFiles() throws Exception {
		// Submit a handful of files to a fresh folder.
		String[] files = {"file1.txt", "file2.txt", "file3.txt"};
		for (String file : files) {
			submitFile(jenkins, "//depot/BigFolder/" + file, "content of " + file);
		}

		// Client mapping the folder into a known workspace root on disk.
		String client = "replica-force.ws";
		String view = "//depot/BigFolder/... //" + client + "/...";
		WorkspaceSpec spec = new WorkspaceSpec(true, true, false, false, false, false,
				null, "LOCAL", view, null, null, null, true);
		ManualWorkspaceImpl workspace = new ManualWorkspaceImpl("none", true, client, spec, false);
		workspace.setExpand(new HashMap<>());

		File wsRoot = new File("target/" + client).getAbsoluteFile();
		wsRoot.mkdirs();
		workspace.setRootPath(wsRoot.toString());

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamTaskListener listener = new StreamTaskListener(out, StandardCharsets.UTF_8);

		try (ClientHelper p4 = new ClientHelper(jenkins.getInstance(), CREDENTIAL, listener, workspace)) {
			P4Ref head = new P4ChangeRef(Long.parseLong(p4.getCounter("change")));

			// Sync with a have:true force populate to write files and populate the have-list.
			Populate haveList = new ForceCleanImpl(true, false, null, null);
			p4.syncFiles(head, haveList);
			for (String file : files) {
				assertTrue(new File(wsRoot, file).exists(), file + " missing after initial sync");
			}

			// Wipe the synced files from disk: the have-list still reports them synced,
			// mirroring a read-only replica where '-p' would bypass the transfer.
			for (String file : files) {
				assertTrue(new File(wsRoot, file).delete(), file + " could not be deleted");
				assertFalse(new File(wsRoot, file).exists(), file + " should be deleted");
			}

			// Isolate the log of the force populate for the totalFileCount assertion.
			listener.getLogger().flush();
			out.reset();

			// have:false force populate ('-p -f') must re-fetch content despite the have-list.
			Populate populate = new ForceCleanImpl(false, false, null, null);
			p4.syncFiles(head, populate);
			listener.getLogger().flush();
		}

		// AC-1/AC-2/AC-3: every reported file is actually written back to disk.
		for (String file : files) {
			assertTrue(new File(wsRoot, file).exists(), file + " missing after force populate");
		}

		// AC-4: the reported totalFileCount matches the number of files written.
		String log = out.toString(StandardCharsets.UTF_8);
		assertTrue(log.contains("... totalFileCount " + files.length),
				"totalFileCount should match the number of files written, log was:\n" + log);
	}
}
