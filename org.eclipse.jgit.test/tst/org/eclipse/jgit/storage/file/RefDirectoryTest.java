/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.storage.file;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.eclipse.jgit.lib.Ref.Storage.LOOSE;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.events.ListenerHandle;
import org.eclipse.jgit.events.RefsChangedEvent;
import org.eclipse.jgit.events.RefsChangedListener;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.junit.Before;
import org.junit.Test;

public class RefDirectoryTest extends LocalDiskRepositoryTestCase {
	private Repository diskRepo;

	private TestRepository<Repository> repo;

	private RefDirectory refdir;

	private RevCommit A;

	private RevCommit B;

	private RevTag v1_0;

	@Before
	public void setUp() throws Exception {
		super.setUp();

		diskRepo = createBareRepository();
		refdir = (RefDirectory) diskRepo.getRefDatabase();

		repo = new TestRepository<Repository>(diskRepo);
		A = repo.commit().create();
		B = repo.commit(repo.getRevWalk().parseCommit(A));
		v1_0 = repo.tag("v1_0", B);
		repo.getRevWalk().parseBody(v1_0);
	}

	@Test
	public void testCreate() throws IOException {
		// setUp above created the directory. We just have to test it.
		File d = diskRepo.getDirectory();
		assertSame(diskRepo, refdir.getRepository());

		assertTrue(new File(d, "refs").isDirectory());
		assertTrue(new File(d, "logs").isDirectory());
		assertTrue(new File(d, "logs/refs").isDirectory());
		assertFalse(new File(d, "packed-refs").exists());

		assertTrue(new File(d, "refs/heads").isDirectory());
		assertTrue(new File(d, "refs/tags").isDirectory());
		assertEquals(2, new File(d, "refs").list().length);
		assertEquals(0, new File(d, "refs/heads").list().length);
		assertEquals(0, new File(d, "refs/tags").list().length);

		assertTrue(new File(d, "logs/refs/heads").isDirectory());
		assertFalse(new File(d, "logs/HEAD").exists());
		assertEquals(0, new File(d, "logs/refs/heads").list().length);

		assertEquals("ref: refs/heads/master\n", read(new File(d, HEAD)));
	}

	@Test
	public void testGetRefs_EmptyDatabase() throws IOException {
		Map<String, Ref> all;

		all = refdir.getRefs(RefDatabase.ALL);
		assertTrue("no references", all.isEmpty());

		all = refdir.getRefs(R_HEADS);
		assertTrue("no references", all.isEmpty());

		all = refdir.getRefs(R_TAGS);
		assertTrue("no references", all.isEmpty());
	}

	@Test
	public void testGetRefs_HeadOnOneBranch() throws IOException {
		Map<String, Ref> all;
		Ref head, master;

		writeLooseRef("refs/heads/master", A);

		all = refdir.getRefs(RefDatabase.ALL);
		assertEquals(2, all.size());
		assertTrue("has HEAD", all.containsKey(HEAD));
		assertTrue("has master", all.containsKey("refs/heads/master"));

		head = all.get(HEAD);
		master = all.get("refs/heads/master");

		assertEquals(HEAD, head.getName());
		assertTrue(head.isSymbolic());
		assertSame(LOOSE, head.getStorage());
		assertSame("uses same ref as target", master, head.getTarget());

		assertEquals("refs/heads/master", master.getName());
		assertFalse(master.isSymbolic());
		assertSame(LOOSE, master.getStorage());
		assertEquals(A, master.getObjectId());
	}

	@Test
	public void testGetRefs_DeatchedHead1() throws IOException {
		Map<String, Ref> all;
		Ref head;

		writeLooseRef(HEAD, A);

		all = refdir.getRefs(RefDatabase.ALL);
		assertEquals(1, all.size());
		assertTrue("has HEAD", all.containsKey(HEAD));

		head = all.get(HEAD);

		assertEquals(HEAD, head.getName());
		assertFalse(head.isSymbolic());
		assertSame(LOOSE, head.getStorage());
		assertEquals(A, head.getObjectId());
	}

	@Test
	public void testGetRefs_DeatchedHead2() throws IOException {
		Map<String, Ref> all;
		Ref head, master;

		writeLooseRef(HEAD, A);
		writeLooseRef("refs/heads/master", B);

		all = refdir.getRefs(RefDatabase.ALL);
		assertEquals(2, all.size());

		head = all.get(HEAD);
		master = all.get("refs/heads/master");

		assertEquals(HEAD, head.getName());
		assertFalse(head.isSymbolic());
		assertSame(LOOSE, head.getStorage());
		assertEquals(A, head.getObjectId());

		assertEquals("refs/heads/master", master.getName());
		assertFalse(master.isSymbolic());
		assertSame(LOOSE, master.getStorage());
		assertEquals(B, master.getObjectId());
	}

	@Test
	public void testGetRefs_DeeplyNestedBranch() throws IOException {
		String name = "refs/heads/a/b/c/d/e/f/g/h/i/j/k";
		Map<String, Ref> all;
		Ref r;

		writeLooseRef(name, A);

		all = refdir.getRefs(RefDatabase.ALL);
		assertEquals(1, all.size());

		r = all.get(name);
		assertEquals(name, r.getName());
		assertFalse(r.isSymbolic());
		assertSame(LOOSE, r.getStorage());
		assertEquals(A, r.getObjectId());
	}

	@Test
	public void testGetRefs_HeadBranchNotBorn() throws IOException {
		Map<String, Ref> all;
		Ref a, b;

		writeLooseRef("refs/heads/A", A);
		writeLooseRef("refs/heads/B", B);

		all = refdir.getRefs(RefDatabase.ALL);
		assertEquals(2, all.size());
		assertFalse("no HEAD", all.containsKey(HEAD));

		a = all.get("refs/heads/A");
		b = all.get("refs/heads/B");

		assertEquals(A, a.getObjectId());
		assertEquals(B, b.getObjectId());

		assertEquals("refs/heads/A", a.getName());
		assertEquals("refs/heads/B", b.getName());
	}

	@Test
	public void testGetRefs_LooseOverridesPacked() throws IOException {
		Map<String, Ref> heads;
		Ref a;

		writeLooseRef("refs/heads/master", B);
		writePackedRef("refs/heads/master", A);

		heads = refdir.getRefs(R_HEADS);
		assertEquals(1, heads.size());

		a = heads.get("master");
		assertEquals("refs/heads/master", a.getName());
		assertEquals(B, a.getObjectId());
	}

	@Test
	public void testGetRefs_IgnoresGarbageRef1() throws IOException {
		Map<String, Ref> heads;
		Ref a;

		writeLooseRef("refs/heads/A", A);
		write(new File(diskRepo.getDirectory(), "refs/heads/bad"), "FAIL\n");

		heads = refdir.getRefs(RefDatabase.ALL);
		assertEquals(1, heads.size());

		a = heads.get("refs/heads/A");
		assertEquals("refs/heads/A", a.getName());
		assertEquals(A, a.getObjectId());
	}

	@Test
	public void testGetRefs_IgnoresGarbageRef2() throws IOException {
		Map<String, Ref> heads;
		Ref a;

		writeLooseRef("refs/heads/A", A);
		write(new File(diskRepo.getDirectory(), "refs/heads/bad"), "");

		heads = refdir.getRefs(RefDatabase.ALL);
		assertEquals(1, heads.size());

		a = heads.get("refs/heads/A");
		assertEquals("refs/heads/A", a.getName());
		assertEquals(A, a.getObjectId());
	}

	@Test
	public void testGetRefs_IgnoresGarbageRef3() throws IOException {
		Map<String, Ref> heads;
		Ref a;

		writeLooseRef("refs/heads/A", A);
		write(new File(diskRepo.getDirectory(), "refs/heads/bad"), "\n");

		heads = refdir.getRefs(RefDatabase.ALL);
		assertEquals(1, heads.size());

		a = heads.get("refs/heads/A");
		assertEquals("refs/heads/A", a.getName());
		assertEquals(A, a.getObjectId());
	}

	@Test
	public void testGetRefs_IgnoresGarbageRef4() throws IOException {
		Map<String, Ref> heads;
		Ref a, b, c;

		writeLooseRef("refs/heads/A", A);
		writeLooseRef("refs/heads/B", B);
		writeLooseRef("refs/heads/C", A);
		heads = refdir.getRefs(RefDatabase.ALL);
		assertEquals(3, heads.size());
		assertTrue(heads.containsKey("refs/heads/A"));
		assertTrue(heads.containsKey("refs/heads/B"));
		assertTrue(heads.containsKey("refs/heads/C"));

		writeLooseRef("refs/heads/B", "FAIL\n");

		heads = refdir.getRefs(RefDatabase.ALL);
		assertEquals(2, heads.size());

		a = heads.get("refs/heads/A");
		b = heads.get("refs/heads/B");
		c = heads.get("refs/heads/C");

		assertEquals("refs/heads/A", a.getName());
		assertEquals(A, a.getObjectId());

		assertNull("no refs/heads/B", b);

		assertEquals("refs/heads/C", c.getName());
		assertEquals(A, c.getObjectId());
	}

	@Test
	public void testGetRefs_InvalidName() throws IOException {
		writeLooseRef("refs/heads/A", A);

		assertTrue("empty refs/heads", refdir.getRefs("refs/heads").isEmpty());
		assertTrue("empty objects", refdir.getRefs("objects").isEmpty());
		assertTrue("empty objects/", refdir.getRefs("objects/").isEmpty());
	}

	@Test
	public void testReadNotExistingBranchConfig() throws IOException {
		assertNull("find branch config", refdir.getRef("config"));
		assertNull("find branch config", refdir.getRef("refs/heads/config"));
	}

	@Test
	public void testReadBranchConfig() throws IOException {
		writeLooseRef("refs/heads/config", A);

		assertNotNull("find branch config", refdir.getRef("config"));
	}

	@Test
	public void testGetRefs_HeadsOnly_AllLoose() throws IOException {
		Map<String, Ref> heads;
		Ref a, b;

		writeLooseRef("refs/heads/A", A);
		writeLooseRef("refs/heads/B", B);
		writeLooseRef("refs/tags/v1.0", v1_0);

		heads = refdir.getRefs(R_HEADS);
		assertEquals(2, heads.size());

		a = heads.get("A");
		b = heads.get("B");

		assertEquals("refs/heads/A", a.getName());
		assertEquals("refs/heads/B", b.getName());

		assertEquals(A, a.getObjectId());
		assertEquals(B, b.getObjectId());
	}

	@Test
	public void testGetRefs_HeadsOnly_AllPacked1() throws IOException {
		Map<String, Ref> heads;
		Ref a;

		deleteLooseRef(HEAD);
		writePackedRef("refs/heads/A", A);

		heads = refdir.getRefs(R_HEADS);
		assertEquals(1, heads.size());

		a = heads.get("A");

		assertEquals("refs/heads/A", a.getName());
		assertEquals(A, a.getObjectId());
	}

	@Test
	public void testGetRefs_HeadsOnly_SymrefToPacked() throws IOException {
		Map<String, Ref> heads;
		Ref master, other;

		writeLooseRef("refs/heads/other", "ref: refs/heads/master\n");
		writePackedRef("refs/heads/master", A);

		heads = refdir.getRefs(R_HEADS);
		assertEquals(2, heads.size());

		master = heads.get("master");
		other = heads.get("other");

		assertEquals("refs/heads/master", master.getName());
		assertEquals(A, master.getObjectId());

		assertEquals("refs/heads/other", other.getName());
		assertEquals(A, other.getObjectId());
		assertSame(master, other.getTarget());
	}

	@Test
	public void testGetRefs_HeadsOnly_Mixed() throws IOException {
		Map<String, Ref> heads;
		Ref a, b;

		writeLooseRef("refs/heads/A", A);
		writeLooseRef("refs/heads/B", B);
		writePackedRef("refs/tags/v1.0", v1_0);

		heads = refdir.getRefs(R_HEADS);
		assertEquals(2, heads.size());

		a = heads.get("A");
		b = heads.get("B");

		assertEquals("refs/heads/A", a.getName());
		assertEquals("refs/heads/B", b.getName());

		assertEquals(A, a.getObjectId());
		assertEquals(B, b.getObjectId());
	}

	@Test
	public void testGetRefs_TagsOnly_AllLoose() throws IOException {
		Map<String, Ref> tags;
		Ref a;

		writeLooseRef("refs/heads/A", A);
		writeLooseRef("refs/tags/v1.0", v1_0);

		tags = refdir.getRefs(R_TAGS);
		assertEquals(1, tags.size());

		a = tags.get("v1.0");

		assertEquals("refs/tags/v1.0", a.getName());
		assertEquals(v1_0, a.getObjectId());
	}

	@Test
	public void testGetRefs_LooseSortedCorrectly() throws IOException {
		Map<String, Ref> refs;

		writeLooseRef("refs/heads/project1/A", A);
		writeLooseRef("refs/heads/project1-B", B);

		refs = refdir.getRefs(RefDatabase.ALL);
		assertEquals(2, refs.size());
		assertEquals(A, refs.get("refs/heads/project1/A").getObjectId());
		assertEquals(B, refs.get("refs/heads/project1-B").getObjectId());
	}

	@Test
	public void testGetRefs_LooseSorting_Bug_348834() throws IOException {
		Map<String, Ref> refs;

		writeLooseRef("refs/heads/my/a+b", A);
		writeLooseRef("refs/heads/my/a/b/c", B);

		final int[] count = new int[1];

		ListenerHandle listener = Repository.getGlobalListenerList()
				.addRefsChangedListener(new RefsChangedListener() {

					public void onRefsChanged(RefsChangedEvent event) {
						count[0]++;
					}
				});

		refs = refdir.getRefs(RefDatabase.ALL);
		refs = refdir.getRefs(RefDatabase.ALL);
		listener.remove();
		assertEquals(1, count[0]); // Bug 348834 multiple RefsChangedEvents
		assertEquals(2, refs.size());
		assertEquals(A, refs.get("refs/heads/my/a+b").getObjectId());
		assertEquals(B, refs.get("refs/heads/my/a/b/c").getObjectId());

	}

	@Test
	public void testGetRefs_TagsOnly_AllPacked() throws IOException {
		Map<String, Ref> tags;
		Ref a;

		deleteLooseRef(HEAD);
		writePackedRef("refs/tags/v1.0", v1_0);

		tags = refdir.getRefs(R_TAGS);
		assertEquals(1, tags.size());

		a = tags.get("v1.0");

		assertEquals("refs/tags/v1.0", a.getName());
		assertEquals(v1_0, a.getObjectId());
	}

	@Test
	public void testGetRefs_DiscoversNewLoose1() throws IOException {
		Map<String, Ref> orig, next;
		Ref orig_r, next_r;

		writeLooseRef("refs/heads/master", A);
		orig = refdir.getRefs(RefDatabase.ALL);

		writeLooseRef("refs/heads/next", B);
		next = refdir.getRefs(RefDatabase.ALL);

		assertEquals(2, orig.size());
		assertEquals(3, next.size());

		assertFalse(orig.containsKey("refs/heads/next"));
		assertTrue(next.containsKey("refs/heads/next"));

		orig_r = orig.get("refs/heads/master");
		next_r = next.get("refs/heads/master");
		assertEquals(A, orig_r.getObjectId());
		assertSame("uses cached instance", orig_r, next_r);
		assertSame("same HEAD", orig_r, orig.get(HEAD).getTarget());
		assertSame("same HEAD", orig_r, next.get(HEAD).getTarget());

		next_r = next.get("refs/heads/next");
		assertSame(LOOSE, next_r.getStorage());
		assertEquals(B, next_r.getObjectId());
	}

	@Test
	public void testGetRefs_DiscoversNewLoose2() throws IOException {
		Map<String, Ref> orig, next, news;

		writeLooseRef("refs/heads/pu", A);
		orig = refdir.getRefs(RefDatabase.ALL);

		writeLooseRef("refs/heads/new/B", B);
		news = refdir.getRefs("refs/heads/new/");
		next = refdir.getRefs(RefDatabase.ALL);

		assertEquals(1, orig.size());
		assertEquals(2, next.size());
		assertEquals(1, news.size());

		assertTrue(orig.containsKey("refs/heads/pu"));
		assertTrue(next.containsKey("refs/heads/pu"));
		assertFalse(news.containsKey("refs/heads/pu"));

		assertFalse(orig.containsKey("refs/heads/new/B"));
		assertTrue(next.containsKey("refs/heads/new/B"));
		assertTrue(news.containsKey("B"));
	}

	@Test
	public void testGetRefs_DiscoversModifiedLoose() throws IOException {
		Map<String, Ref> all;

		writeLooseRef("refs/heads/master", A);
		all = refdir.getRefs(RefDatabase.ALL);
		assertEquals(A, all.get(HEAD).getObjectId());

		writeLooseRef("refs/heads/master", B);
		all = refdir.getRefs(RefDatabase.ALL);
		assertEquals(B, all.get(HEAD).getObjectId());
	}

	@Test
	public void testGetRef_DiscoversModifiedLoose() throws IOException {
		Map<String, Ref> all;

		writeLooseRef("refs/heads/master", A);
		all = refdir.getRefs(RefDatabase.ALL);
		assertEquals(A, all.get(HEAD).getObjectId());

		writeLooseRef("refs/heads/master", B);

		Ref master = refdir.getRef("refs/heads/master");
		assertEquals(B, master.getObjectId());
	}

	@Test
	public void testGetRefs_DiscoversDeletedLoose1() throws IOException {
		Map<String, Ref> orig, next;
		Ref orig_r, next_r;

		writeLooseRef("refs/heads/B", B);
		writeLooseRef("refs/heads/master", A);
		orig = refdir.getRefs(RefDatabase.ALL);

		deleteLooseRef("refs/heads/B");
		next = refdir.getRefs(RefDatabase.ALL);

		assertEquals(3, orig.size());
		assertEquals(2, next.size());

		assertTrue(orig.containsKey("refs/heads/B"));
		assertFalse(next.containsKey("refs/heads/B"));

		orig_r = orig.get("refs/heads/master");
		next_r = next.get("refs/heads/master");
		assertEquals(A, orig_r.getObjectId());
		assertSame("uses cached instance", orig_r, next_r);
		assertSame("same HEAD", orig_r, orig.get(HEAD).getTarget());
		assertSame("same HEAD", orig_r, next.get(HEAD).getTarget());

		orig_r = orig.get("refs/heads/B");
		assertSame(LOOSE, orig_r.getStorage());
		assertEquals(B, orig_r.getObjectId());
	}

	@Test
	public void testGetRef_DiscoversDeletedLoose() throws IOException {
		Map<String, Ref> all;

		writeLooseRef("refs/heads/master", A);
		all = refdir.getRefs(RefDatabase.ALL);
		assertEquals(A, all.get(HEAD).getObjectId());

		deleteLooseRef("refs/heads/master");
		assertNull(refdir.getRef("refs/heads/master"));
		assertTrue(refdir.getRefs(RefDatabase.ALL).isEmpty());
	}

	@Test
	public void testGetRefs_DiscoversDeletedLoose2() throws IOException {
		Map<String, Ref> orig, next;

		writeLooseRef("refs/heads/master", A);
		writeLooseRef("refs/heads/pu", B);
		orig = refdir.getRefs(RefDatabase.ALL);

		deleteLooseRef("refs/heads/pu");
		next = refdir.getRefs(RefDatabase.ALL);

		assertEquals(3, orig.size());
		assertEquals(2, next.size());

		assertTrue(orig.containsKey("refs/heads/pu"));
		assertFalse(next.containsKey("refs/heads/pu"));
	}

	@Test
	public void testGetRefs_DiscoversDeletedLoose3() throws IOException {
		Map<String, Ref> orig, next;

		writeLooseRef("refs/heads/master", A);
		writeLooseRef("refs/heads/next", B);
		writeLooseRef("refs/heads/pu", B);
		writeLooseRef("refs/tags/v1.0", v1_0);
		orig = refdir.getRefs(RefDatabase.ALL);

		deleteLooseRef("refs/heads/pu");
		deleteLooseRef("refs/heads/next");
		next = refdir.getRefs(RefDatabase.ALL);

		assertEquals(5, orig.size());
		assertEquals(3, next.size());

		assertTrue(orig.containsKey("refs/heads/pu"));
		assertTrue(orig.containsKey("refs/heads/next"));
		assertFalse(next.containsKey("refs/heads/pu"));
		assertFalse(next.containsKey("refs/heads/next"));
	}

	@Test
	public void testGetRefs_DiscoversDeletedLoose4() throws IOException {
		Map<String, Ref> orig, next;
		Ref orig_r, next_r;

		writeLooseRef("refs/heads/B", B);
		writeLooseRef("refs/heads/master", A);
		orig = refdir.getRefs(RefDatabase.ALL);

		deleteLooseRef("refs/heads/master");
		next = refdir.getRefs("refs/heads/");

		assertEquals(3, orig.size());
		assertEquals(1, next.size());

		assertTrue(orig.containsKey("refs/heads/B"));
		assertTrue(orig.containsKey("refs/heads/master"));
		assertTrue(next.containsKey("B"));
		assertFalse(next.containsKey("master"));

		orig_r = orig.get("refs/heads/B");
		next_r = next.get("B");
		assertEquals(B, orig_r.getObjectId());
		assertSame("uses cached instance", orig_r, next_r);
	}

	@Test
	public void testGetRefs_DiscoversDeletedLoose5() throws IOException {
		Map<String, Ref> orig, next;

		writeLooseRef("refs/heads/master", A);
		writeLooseRef("refs/heads/pu", B);
		orig = refdir.getRefs(RefDatabase.ALL);

		deleteLooseRef("refs/heads/pu");
		writeLooseRef("refs/tags/v1.0", v1_0);
		next = refdir.getRefs(RefDatabase.ALL);

		assertEquals(3, orig.size());
		assertEquals(3, next.size());

		assertTrue(orig.containsKey("refs/heads/pu"));
		assertFalse(orig.containsKey("refs/tags/v1.0"));
		assertFalse(next.containsKey("refs/heads/pu"));
		assertTrue(next.containsKey("refs/tags/v1.0"));
	}

	@Test
	public void testGetRefs_SkipsLockFiles() throws IOException {
		Map<String, Ref> all;

		writeLooseRef("refs/heads/master", A);
		writeLooseRef("refs/heads/pu.lock", B);
		all = refdir.getRefs(RefDatabase.ALL);

		assertEquals(2, all.size());

		assertTrue(all.containsKey(HEAD));
		assertTrue(all.containsKey("refs/heads/master"));
		assertFalse(all.containsKey("refs/heads/pu.lock"));
	}

	@Test
	public void testGetRefs_CycleInSymbolicRef() throws IOException {
		Map<String, Ref> all;
		Ref r;

		writeLooseRef("refs/1", "ref: refs/2\n");
		writeLooseRef("refs/2", "ref: refs/3\n");
		writeLooseRef("refs/3", "ref: refs/4\n");
		writeLooseRef("refs/4", "ref: refs/5\n");
		writeLooseRef("refs/5", "ref: refs/end\n");
		writeLooseRef("refs/end", A);

		all = refdir.getRefs(RefDatabase.ALL);
		r = all.get("refs/1");
		assertNotNull("has 1", r);

		assertEquals("refs/1", r.getName());
		assertEquals(A, r.getObjectId());
		assertTrue(r.isSymbolic());

		r = r.getTarget();
		assertEquals("refs/2", r.getName());
		assertEquals(A, r.getObjectId());
		assertTrue(r.isSymbolic());

		r = r.getTarget();
		assertEquals("refs/3", r.getName());
		assertEquals(A, r.getObjectId());
		assertTrue(r.isSymbolic());

		r = r.getTarget();
		assertEquals("refs/4", r.getName());
		assertEquals(A, r.getObjectId());
		assertTrue(r.isSymbolic());

		r = r.getTarget();
		assertEquals("refs/5", r.getName());
		assertEquals(A, r.getObjectId());
		assertTrue(r.isSymbolic());

		r = r.getTarget();
		assertEquals("refs/end", r.getName());
		assertEquals(A, r.getObjectId());
		assertFalse(r.isSymbolic());

		writeLooseRef("refs/5", "ref: refs/6\n");
		writeLooseRef("refs/6", "ref: refs/end\n");
		all = refdir.getRefs(RefDatabase.ALL);
		r = all.get("refs/1");
		assertNull("mising 1 due to cycle", r);
	}

	@Test
	public void testGetRefs_PackedNotPeeled_Sorted() throws IOException {
		Map<String, Ref> all;

		writePackedRefs("" + //
				A.name() + " refs/heads/master\n" + //
				B.name() + " refs/heads/other\n" + //
				v1_0.name() + " refs/tags/v1.0\n");
		all = refdir.getRefs(RefDatabase.ALL);

		assertEquals(4, all.size());
		final Ref head = all.get(HEAD);
		final Ref master = all.get("refs/heads/master");
		final Ref other = all.get("refs/heads/other");
		final Ref tag = all.get("refs/tags/v1.0");

		assertEquals(A, master.getObjectId());
		assertFalse(master.isPeeled());
		assertNull(master.getPeeledObjectId());

		assertEquals(B, other.getObjectId());
		assertFalse(other.isPeeled());
		assertNull(other.getPeeledObjectId());

		assertSame(master, head.getTarget());
		assertEquals(A, head.getObjectId());
		assertFalse(head.isPeeled());
		assertNull(head.getPeeledObjectId());

		assertEquals(v1_0, tag.getObjectId());
		assertFalse(tag.isPeeled());
		assertNull(tag.getPeeledObjectId());
	}

	@Test
	public void testGetRef_PackedNotPeeled_WrongSort() throws IOException {
		writePackedRefs("" + //
				v1_0.name() + " refs/tags/v1.0\n" + //
				B.name() + " refs/heads/other\n" + //
				A.name() + " refs/heads/master\n");

		final Ref head = refdir.getRef(HEAD);
		final Ref master = refdir.getRef("refs/heads/master");
		final Ref other = refdir.getRef("refs/heads/other");
		final Ref tag = refdir.getRef("refs/tags/v1.0");

		assertEquals(A, master.getObjectId());
		assertFalse(master.isPeeled());
		assertNull(master.getPeeledObjectId());

		assertEquals(B, other.getObjectId());
		assertFalse(other.isPeeled());
		assertNull(other.getPeeledObjectId());

		assertSame(master, head.getTarget());
		assertEquals(A, head.getObjectId());
		assertFalse(head.isPeeled());
		assertNull(head.getPeeledObjectId());

		assertEquals(v1_0, tag.getObjectId());
		assertFalse(tag.isPeeled());
		assertNull(tag.getPeeledObjectId());
	}

	@Test
	public void testGetRefs_PackedWithPeeled() throws IOException {
		Map<String, Ref> all;

		writePackedRefs("# pack-refs with: peeled \n" + //
				A.name() + " refs/heads/master\n" + //
				B.name() + " refs/heads/other\n" + //
				v1_0.name() + " refs/tags/v1.0\n" + //
				"^" + v1_0.getObject().name() + "\n");
		all = refdir.getRefs(RefDatabase.ALL);

		assertEquals(4, all.size());
		final Ref head = all.get(HEAD);
		final Ref master = all.get("refs/heads/master");
		final Ref other = all.get("refs/heads/other");
		final Ref tag = all.get("refs/tags/v1.0");

		assertEquals(A, master.getObjectId());
		assertTrue(master.isPeeled());
		assertNull(master.getPeeledObjectId());

		assertEquals(B, other.getObjectId());
		assertTrue(other.isPeeled());
		assertNull(other.getPeeledObjectId());

		assertSame(master, head.getTarget());
		assertEquals(A, head.getObjectId());
		assertTrue(head.isPeeled());
		assertNull(head.getPeeledObjectId());

		assertEquals(v1_0, tag.getObjectId());
		assertTrue(tag.isPeeled());
		assertEquals(v1_0.getObject(), tag.getPeeledObjectId());
	}

	@Test
	public void test_repack() throws Exception {
		Map<String, Ref> all;

		writePackedRefs("# pack-refs with: peeled \n" + //
				A.name() + " refs/heads/master\n" + //
				B.name() + " refs/heads/other\n" + //
				v1_0.name() + " refs/tags/v1.0\n" + //
				"^" + v1_0.getObject().name() + "\n");
		all = refdir.getRefs(RefDatabase.ALL);

		assertEquals(4, all.size());
		assertEquals(Storage.LOOSE, all.get(HEAD).getStorage());
		assertEquals(Storage.PACKED, all.get("refs/heads/master").getStorage());
		assertEquals(A.getId(), all.get("refs/heads/master").getObjectId());
		assertEquals(Storage.PACKED, all.get("refs/heads/other").getStorage());
		assertEquals(Storage.PACKED, all.get("refs/tags/v1.0").getStorage());

		repo.update("refs/heads/master", B.getId());
		RevTag v0_1 = repo.tag("v0.1", A);
		repo.update("refs/tags/v0.1", v0_1);

		all = refdir.getRefs(RefDatabase.ALL);
		assertEquals(5, all.size());
		assertEquals(Storage.LOOSE, all.get(HEAD).getStorage());
		// Why isn't the next ref LOOSE_PACKED?
		assertEquals(Storage.LOOSE, all.get("refs/heads/master")
				.getStorage());
		assertEquals(B.getId(), all.get("refs/heads/master").getObjectId());
		assertEquals(Storage.PACKED, all.get("refs/heads/other").getStorage());
		assertEquals(Storage.PACKED, all.get("refs/tags/v1.0").getStorage());
		assertEquals(Storage.LOOSE, all.get("refs/tags/v0.1").getStorage());
		assertEquals(v0_1.getId(), all.get("refs/tags/v0.1").getObjectId());

		all = refdir.getRefs(RefDatabase.ALL);
		refdir.pack(new ArrayList<String>(all.keySet()));

		all = refdir.getRefs(RefDatabase.ALL);
		assertEquals(5, all.size());
		assertEquals(Storage.LOOSE, all.get(HEAD).getStorage());
		// Why isn't the next ref LOOSE_PACKED?
		assertEquals(Storage.PACKED, all.get("refs/heads/master").getStorage());
		assertEquals(B.getId(), all.get("refs/heads/master").getObjectId());
		assertEquals(Storage.PACKED, all.get("refs/heads/other").getStorage());
		assertEquals(Storage.PACKED, all.get("refs/tags/v1.0").getStorage());
		assertEquals(Storage.PACKED, all.get("refs/tags/v0.1").getStorage());
		assertEquals(v0_1.getId(), all.get("refs/tags/v0.1").getObjectId());
	}

	@Test
	public void testGetRef_EmptyDatabase() throws IOException {
		Ref r;

		r = refdir.getRef(HEAD);
		assertTrue(r.isSymbolic());
		assertSame(LOOSE, r.getStorage());
		assertEquals("refs/heads/master", r.getTarget().getName());
		assertSame(NEW, r.getTarget().getStorage());
		assertNull(r.getTarget().getObjectId());

		assertNull(refdir.getRef("refs/heads/master"));
		assertNull(refdir.getRef("refs/tags/v1.0"));
		assertNull(refdir.getRef("FETCH_HEAD"));
		assertNull(refdir.getRef("NOT.A.REF.NAME"));
		assertNull(refdir.getRef("master"));
		assertNull(refdir.getRef("v1.0"));
	}

	@Test
	public void testGetRef_FetchHead() throws IOException {
		// This is an odd special case where we need to make sure we read
		// exactly the first 40 bytes of the file and nothing further on
		// that line, or the remainder of the file.
		write(new File(diskRepo.getDirectory(), "FETCH_HEAD"), A.name()
				+ "\tnot-for-merge"
				+ "\tbranch 'master' of git://egit.eclipse.org/jgit\n");

		Ref r = refdir.getRef("FETCH_HEAD");
		assertFalse(r.isSymbolic());
		assertEquals(A, r.getObjectId());
		assertEquals("FETCH_HEAD", r.getName());
		assertFalse(r.isPeeled());
		assertNull(r.getPeeledObjectId());
	}

	@Test
	public void testGetRef_AnyHeadWithGarbage() throws IOException {
		write(new File(diskRepo.getDirectory(), "refs/heads/A"), A.name()
				+ "012345 . this is not a standard reference\n"
				+ "#and even more junk\n");

		Ref r = refdir.getRef("refs/heads/A");
		assertFalse(r.isSymbolic());
		assertEquals(A, r.getObjectId());
		assertEquals("refs/heads/A", r.getName());
		assertFalse(r.isPeeled());
		assertNull(r.getPeeledObjectId());
	}

	@Test
	public void testGetRefs_CorruptSymbolicReference() throws IOException {
		String name = "refs/heads/A";
		writeLooseRef(name, "ref: \n");
		assertTrue(refdir.getRefs(RefDatabase.ALL).isEmpty());
	}

	@Test
	public void testGetRef_CorruptSymbolicReference() throws IOException {
		String name = "refs/heads/A";
		writeLooseRef(name, "ref: \n");
		try {
			refdir.getRef(name);
			fail("read an invalid reference");
		} catch (IOException err) {
			String msg = err.getMessage();
			assertEquals("Not a ref: " + name + ": ref:", msg);
		}
	}

	@Test
	public void testGetRefs_CorruptObjectIdReference() throws IOException {
		String name = "refs/heads/A";
		String content = "zoo" + A.name();
		writeLooseRef(name, content + "\n");
		assertTrue(refdir.getRefs(RefDatabase.ALL).isEmpty());
	}

	@Test
	public void testGetRef_CorruptObjectIdReference() throws IOException {
		String name = "refs/heads/A";
		String content = "zoo" + A.name();
		writeLooseRef(name, content + "\n");
		try {
			refdir.getRef(name);
			fail("read an invalid reference");
		} catch (IOException err) {
			String msg = err.getMessage();
			assertEquals("Not a ref: " + name + ": " + content, msg);
		}
	}

	@Test
	public void testIsNameConflicting() throws IOException {
		writeLooseRef("refs/heads/a/b", A);
		writePackedRef("refs/heads/q", B);

		// new references cannot replace an existing container
		assertTrue(refdir.isNameConflicting("refs"));
		assertTrue(refdir.isNameConflicting("refs/heads"));
		assertTrue(refdir.isNameConflicting("refs/heads/a"));

		// existing reference is not conflicting
		assertFalse(refdir.isNameConflicting("refs/heads/a/b"));

		// new references are not conflicting
		assertFalse(refdir.isNameConflicting("refs/heads/a/d"));
		assertFalse(refdir.isNameConflicting("refs/heads/master"));

		// existing reference must not be used as a container
		assertTrue(refdir.isNameConflicting("refs/heads/a/b/c"));
		assertTrue(refdir.isNameConflicting("refs/heads/q/master"));
	}

	@Test
	public void testPeelLooseTag() throws IOException {
		writeLooseRef("refs/tags/v1_0", v1_0);
		writeLooseRef("refs/tags/current", "ref: refs/tags/v1_0\n");

		final Ref tag = refdir.getRef("refs/tags/v1_0");
		final Ref cur = refdir.getRef("refs/tags/current");

		assertEquals(v1_0, tag.getObjectId());
		assertFalse(tag.isSymbolic());
		assertFalse(tag.isPeeled());
		assertNull(tag.getPeeledObjectId());

		assertEquals(v1_0, cur.getObjectId());
		assertTrue(cur.isSymbolic());
		assertFalse(cur.isPeeled());
		assertNull(cur.getPeeledObjectId());

		final Ref tag_p = refdir.peel(tag);
		final Ref cur_p = refdir.peel(cur);

		assertNotSame(tag, tag_p);
		assertFalse(tag_p.isSymbolic());
		assertTrue(tag_p.isPeeled());
		assertEquals(v1_0, tag_p.getObjectId());
		assertEquals(v1_0.getObject(), tag_p.getPeeledObjectId());
		assertSame(tag_p, refdir.peel(tag_p));

		assertNotSame(cur, cur_p);
		assertEquals("refs/tags/current", cur_p.getName());
		assertTrue(cur_p.isSymbolic());
		assertEquals("refs/tags/v1_0", cur_p.getTarget().getName());
		assertTrue(cur_p.isPeeled());
		assertEquals(v1_0, cur_p.getObjectId());
		assertEquals(v1_0.getObject(), cur_p.getPeeledObjectId());

		// reuses cached peeling later, but not immediately due to
		// the implementation so we have to fetch it once.
		final Ref tag_p2 = refdir.getRef("refs/tags/v1_0");
		assertFalse(tag_p2.isSymbolic());
		assertTrue(tag_p2.isPeeled());
		assertEquals(v1_0, tag_p2.getObjectId());
		assertEquals(v1_0.getObject(), tag_p2.getPeeledObjectId());

		assertSame(tag_p2, refdir.getRef("refs/tags/v1_0"));
		assertSame(tag_p2, refdir.getRef("refs/tags/current").getTarget());
		assertSame(tag_p2, refdir.peel(tag_p2));
	}

	@Test
	public void testPeelCommit() throws IOException {
		writeLooseRef("refs/heads/master", A);

		Ref master = refdir.getRef("refs/heads/master");
		assertEquals(A, master.getObjectId());
		assertFalse(master.isPeeled());
		assertNull(master.getPeeledObjectId());

		Ref master_p = refdir.peel(master);
		assertNotSame(master, master_p);
		assertEquals(A, master_p.getObjectId());
		assertTrue(master_p.isPeeled());
		assertNull(master_p.getPeeledObjectId());

		// reuses cached peeling later, but not immediately due to
		// the implementation so we have to fetch it once.
		Ref master_p2 = refdir.getRef("refs/heads/master");
		assertNotSame(master, master_p2);
		assertEquals(A, master_p2.getObjectId());
		assertTrue(master_p2.isPeeled());
		assertNull(master_p2.getPeeledObjectId());
		assertSame(master_p2, refdir.peel(master_p2));
	}

	@Test
	public void testRefsChangedStackOverflow() throws Exception {
		final FileRepository newRepo = createBareRepository();
		final RefDatabase refDb = newRepo.getRefDatabase();
		File packedRefs = new File(newRepo.getDirectory(), "packed-refs");
		assertTrue(packedRefs.createNewFile());
		final AtomicReference<StackOverflowError> error = new AtomicReference<StackOverflowError>();
		final AtomicReference<IOException> exception = new AtomicReference<IOException>();
		final AtomicInteger changeCount = new AtomicInteger();
		newRepo.getListenerList().addRefsChangedListener(
				new RefsChangedListener() {

					public void onRefsChanged(RefsChangedEvent event) {
						try {
							refDb.getRefs("ref");
							changeCount.incrementAndGet();
						} catch (StackOverflowError soe) {
							error.set(soe);
						} catch (IOException ioe) {
							exception.set(ioe);
						}
					}
				});
		refDb.getRefs("ref");
		refDb.getRefs("ref");
		assertNull(error.get());
		assertNull(exception.get());
		assertEquals(1, changeCount.get());
	}

	private void writeLooseRef(String name, AnyObjectId id) throws IOException {
		writeLooseRef(name, id.name() + "\n");
	}

	private void writeLooseRef(String name, String content) throws IOException {
		write(new File(diskRepo.getDirectory(), name), content);
	}

	private void writePackedRef(String name, AnyObjectId id) throws IOException {
		writePackedRefs(id.name() + " " + name + "\n");
	}

	private void writePackedRefs(String content) throws IOException {
		File pr = new File(diskRepo.getDirectory(), "packed-refs");
		write(pr, content);

		final long now = System.currentTimeMillis();
		final int oneHourAgo = 3600 * 1000;
		pr.setLastModified(now - oneHourAgo);
	}

	private void deleteLooseRef(String name) {
		File path = new File(diskRepo.getDirectory(), name);
		assertTrue("deleted " + name, path.delete());
	}
}
