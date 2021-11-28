/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

public class PushCommandTest extends RepositoryTestCase {

	@Test
	public void testPush() throws JGitInternalException, IOException,
			GitAPIException, URISyntaxException {

		// create other repository
		Repository db2 = createWorkRepository();

		// setup the first repository
		final StoredConfig config = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		URIish uri = new URIish(db2.getDirectory().toURI().toURL());
		remoteConfig.addURI(uri);
		remoteConfig.update(config);
		config.save();

		Git git1 = new Git(db);
		// create some refs via commits and tag
		RevCommit commit = git1.commit().setMessage("initial commit").call();
		Ref tagRef = git1.tag().setName("tag").call();

		try {
			db2.resolve(commit.getId().getName() + "^{commit}");
			fail("id shouldn't exist yet");
		} catch (MissingObjectException e) {
			// we should get here
		}

		RefSpec spec = new RefSpec("refs/heads/master:refs/heads/x");
		git1.push().setRemote("test").setRefSpecs(spec)
				.call();

		assertEquals(commit.getId(),
				db2.resolve(commit.getId().getName() + "^{commit}"));
		assertEquals(tagRef.getObjectId(),
				db2.resolve(tagRef.getObjectId().getName()));
	}

	@Test
	public void testTrackingUpdate() throws Exception {
		Repository db2 = createBareRepository();

		String remote = "origin";
		String branch = "refs/heads/master";
		String trackingBranch = "refs/remotes/" + remote + "/master";

		Git git = new Git(db);

		RevCommit commit1 = git.commit().setMessage("Initial commit")
				.call();

		RefUpdate branchRefUpdate = db.updateRef(branch);
		branchRefUpdate.setNewObjectId(commit1.getId());
		branchRefUpdate.update();

		RefUpdate trackingBranchRefUpdate = db.updateRef(trackingBranch);
		trackingBranchRefUpdate.setNewObjectId(commit1.getId());
		trackingBranchRefUpdate.update();

		final StoredConfig config = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, remote);
		URIish uri = new URIish(db2.getDirectory().toURI().toURL());
		remoteConfig.addURI(uri);
		remoteConfig.addFetchRefSpec(new RefSpec("+refs/heads/*:refs/remotes/"
				+ remote + "/*"));
		remoteConfig.update(config);
		config.save();


		RevCommit commit2 = git.commit().setMessage("Commit to push").call();

		RefSpec spec = new RefSpec(branch + ":" + branch);
		Iterable<PushResult> resultIterable = git.push().setRemote(remote)
				.setRefSpecs(spec).call();

		PushResult result = resultIterable.iterator().next();
		TrackingRefUpdate trackingRefUpdate = result
				.getTrackingRefUpdate(trackingBranch);

		assertNotNull(trackingRefUpdate);
		assertEquals(trackingBranch, trackingRefUpdate.getLocalName());
		assertEquals(branch, trackingRefUpdate.getRemoteName());
		assertEquals(commit2.getId(), trackingRefUpdate.getNewObjectId());
		assertEquals(commit2.getId(), db.resolve(trackingBranch));
		assertEquals(commit2.getId(), db2.resolve(branch));
	}

	/**
	 * Check that pushes over file protocol lead to appropriate ref-updates.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushRefUpdate() throws Exception {
		Git git = new Git(db);
		Git git2 = new Git(createBareRepository());

		final StoredConfig config = git.getRepository().getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		URIish uri = new URIish(git2.getRepository().getDirectory().toURI()
				.toURL());
		remoteConfig.addURI(uri);
		remoteConfig.addPushRefSpec(new RefSpec("+refs/heads/*:refs/heads/*"));
		remoteConfig.update(config);
		config.save();

		writeTrashFile("f", "content of f");
		git.add().addFilepattern("f").call();
		RevCommit commit = git.commit().setMessage("adding f").call();

		assertEquals(null, git2.getRepository().resolve("refs/heads/master"));
		git.push().setRemote("test").call();
		assertEquals(commit.getId(),
				git2.getRepository().resolve("refs/heads/master"));

		git.branchCreate().setName("refs/heads/test").call();
		git.checkout().setName("refs/heads/test").call();


		for (int i = 0; i < 6; i++) {
			writeTrashFile("f" + i, "content of f" + i);
			git.add().addFilepattern("f" + i).call();
			commit = git.commit().setMessage("adding f" + i).call();
			git.push().setRemote("test").call();
			git2.getRepository().getAllRefs();
			assertEquals("failed to update on attempt " + i, commit.getId(),
					git2.getRepository().resolve("refs/heads/test"));

		}
	}

	/**
	 * Check that the push refspec is read from config.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushWithRefSpecFromConfig() throws Exception {
		Git git = new Git(db);
		Git git2 = new Git(createBareRepository());

		final StoredConfig config = git.getRepository().getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		URIish uri = new URIish(git2.getRepository().getDirectory().toURI()
				.toURL());
		remoteConfig.addURI(uri);
		remoteConfig.addPushRefSpec(new RefSpec("HEAD:refs/heads/newbranch"));
		remoteConfig.update(config);
		config.save();

		writeTrashFile("f", "content of f");
		git.add().addFilepattern("f").call();
		RevCommit commit = git.commit().setMessage("adding f").call();

		assertEquals(null, git2.getRepository().resolve("refs/heads/master"));
		git.push().setRemote("test").call();
		assertEquals(commit.getId(),
				git2.getRepository().resolve("refs/heads/newbranch"));


	}

	/**
	 * Check that only HEAD is pushed if no refspec is given.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushWithoutPushRefSpec() throws Exception {
		Git git = new Git(db);
		Git git2 = new Git(createBareRepository());

		final StoredConfig config = git.getRepository().getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		URIish uri = new URIish(git2.getRepository().getDirectory().toURI()
				.toURL());
		remoteConfig.addURI(uri);
		remoteConfig.addFetchRefSpec(new RefSpec(
				"+refs/heads/*:refs/remotes/origin/*"));
		remoteConfig.update(config);
		config.save();

		writeTrashFile("f", "content of f");
		git.add().addFilepattern("f").call();
		RevCommit commit = git.commit().setMessage("adding f").call();

		git.checkout().setName("not-pushed").setCreateBranch(true).call();
		git.checkout().setName("branchtopush").setCreateBranch(true).call();

		assertEquals(null,
				git2.getRepository().resolve("refs/heads/branchtopush"));
		assertEquals(null, git2.getRepository()
				.resolve("refs/heads/not-pushed"));
		assertEquals(null, git2.getRepository().resolve("refs/heads/master"));
		git.push().setRemote("test").call();
		assertEquals(commit.getId(),
				git2.getRepository().resolve("refs/heads/branchtopush"));
		assertEquals(null, git2.getRepository()
				.resolve("refs/heads/not-pushed"));
		assertEquals(null, git2.getRepository().resolve("refs/heads/master"));

	}
}
