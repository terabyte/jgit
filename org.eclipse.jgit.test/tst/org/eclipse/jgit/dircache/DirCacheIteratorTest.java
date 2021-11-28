/*
 * Copyright (C) 2008-2009, Google Inc.
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

package org.eclipse.jgit.dircache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collections;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

public class DirCacheIteratorTest extends RepositoryTestCase {
	@Test
	public void testEmptyTree_NoTreeWalk() throws Exception {
		final DirCache dc = DirCache.newInCore();
		assertEquals(0, dc.getEntryCount());

		final DirCacheIterator i = new DirCacheIterator(dc);
		assertTrue(i.eof());
	}

	@Test
	public void testEmptyTree_WithTreeWalk() throws Exception {
		final DirCache dc = DirCache.newInCore();
		assertEquals(0, dc.getEntryCount());

		final TreeWalk tw = new TreeWalk(db);
		tw.addTree(new DirCacheIterator(dc));
		assertFalse(tw.next());
	}

	@Test
	public void testNoSubtree_NoTreeWalk() throws Exception {
		final DirCache dc = DirCache.newInCore();

		final String[] paths = { "a.", "a0b" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(FileMode.REGULAR_FILE);
		}

		final DirCacheBuilder b = dc.builder();
		for (int i = 0; i < ents.length; i++)
			b.add(ents[i]);
		b.finish();

		final DirCacheIterator i = new DirCacheIterator(dc);
		int pathIdx = 0;
		for (; !i.eof(); i.next(1)) {
			assertEquals(pathIdx, i.ptr);
			assertSame(ents[pathIdx], i.getDirCacheEntry());
			pathIdx++;
		}
		assertEquals(paths.length, pathIdx);
	}

	@Test
	public void testNoSubtree_WithTreeWalk() throws Exception {
		final DirCache dc = DirCache.newInCore();

		final String[] paths = { "a.", "a0b" };
		final FileMode[] modes = { FileMode.EXECUTABLE_FILE, FileMode.GITLINK };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(modes[i]);
		}

		final DirCacheBuilder b = dc.builder();
		for (int i = 0; i < ents.length; i++)
			b.add(ents[i]);
		b.finish();

		final DirCacheIterator i = new DirCacheIterator(dc);
		final TreeWalk tw = new TreeWalk(db);
		tw.addTree(i);
		int pathIdx = 0;
		while (tw.next()) {
			assertSame(i, tw.getTree(0, DirCacheIterator.class));
			assertEquals(pathIdx, i.ptr);
			assertSame(ents[pathIdx], i.getDirCacheEntry());
			assertEquals(paths[pathIdx], tw.getPathString());
			assertEquals(modes[pathIdx].getBits(), tw.getRawMode(0));
			assertSame(modes[pathIdx], tw.getFileMode(0));
			pathIdx++;
		}
		assertEquals(paths.length, pathIdx);
	}

	@Test
	public void testSingleSubtree_NoRecursion() throws Exception {
		final DirCache dc = DirCache.newInCore();

		final String[] paths = { "a.", "a/b", "a/c", "a/d", "a0b" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(FileMode.REGULAR_FILE);
		}

		final DirCacheBuilder b = dc.builder();
		for (int i = 0; i < ents.length; i++)
			b.add(ents[i]);
		b.finish();

		final String[] expPaths = { "a.", "a", "a0b" };
		final FileMode[] expModes = { FileMode.REGULAR_FILE, FileMode.TREE,
				FileMode.REGULAR_FILE };
		final int expPos[] = { 0, -1, 4 };

		final DirCacheIterator i = new DirCacheIterator(dc);
		final TreeWalk tw = new TreeWalk(db);
		tw.addTree(i);
		tw.setRecursive(false);
		int pathIdx = 0;
		while (tw.next()) {
			assertSame(i, tw.getTree(0, DirCacheIterator.class));
			assertEquals(expModes[pathIdx].getBits(), tw.getRawMode(0));
			assertSame(expModes[pathIdx], tw.getFileMode(0));
			assertEquals(expPaths[pathIdx], tw.getPathString());

			if (expPos[pathIdx] >= 0) {
				assertEquals(expPos[pathIdx], i.ptr);
				assertSame(ents[expPos[pathIdx]], i.getDirCacheEntry());
			} else {
				assertSame(FileMode.TREE, tw.getFileMode(0));
			}

			pathIdx++;
		}
		assertEquals(expPaths.length, pathIdx);
	}

	@Test
	public void testSingleSubtree_Recursive() throws Exception {
		final DirCache dc = DirCache.newInCore();

		final FileMode mode = FileMode.REGULAR_FILE;
		final String[] paths = { "a.", "a/b", "a/c", "a/d", "a0b" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(mode);
		}

		final DirCacheBuilder b = dc.builder();
		for (int i = 0; i < ents.length; i++)
			b.add(ents[i]);
		b.finish();

		final DirCacheIterator i = new DirCacheIterator(dc);
		final TreeWalk tw = new TreeWalk(db);
		tw.addTree(i);
		tw.setRecursive(true);
		int pathIdx = 0;
		while (tw.next()) {
			final DirCacheIterator c = tw.getTree(0, DirCacheIterator.class);
			assertNotNull(c);
			assertEquals(pathIdx, c.ptr);
			assertSame(ents[pathIdx], c.getDirCacheEntry());
			assertEquals(paths[pathIdx], tw.getPathString());
			assertEquals(mode.getBits(), tw.getRawMode(0));
			assertSame(mode, tw.getFileMode(0));
			pathIdx++;
		}
		assertEquals(paths.length, pathIdx);
	}

	@Test
	public void testTwoLevelSubtree_Recursive() throws Exception {
		final DirCache dc = DirCache.newInCore();

		final FileMode mode = FileMode.REGULAR_FILE;
		final String[] paths = { "a.", "a/b", "a/c/e", "a/c/f", "a/d", "a0b" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(mode);
		}

		final DirCacheBuilder b = dc.builder();
		for (int i = 0; i < ents.length; i++)
			b.add(ents[i]);
		b.finish();

		final TreeWalk tw = new TreeWalk(db);
		tw.addTree(new DirCacheIterator(dc));
		tw.setRecursive(true);
		int pathIdx = 0;
		while (tw.next()) {
			final DirCacheIterator c = tw.getTree(0, DirCacheIterator.class);
			assertNotNull(c);
			assertEquals(pathIdx, c.ptr);
			assertSame(ents[pathIdx], c.getDirCacheEntry());
			assertEquals(paths[pathIdx], tw.getPathString());
			assertEquals(mode.getBits(), tw.getRawMode(0));
			assertSame(mode, tw.getFileMode(0));
			pathIdx++;
		}
		assertEquals(paths.length, pathIdx);
	}

	@Test
	public void testReset() throws Exception {
		final DirCache dc = DirCache.newInCore();

		final FileMode mode = FileMode.REGULAR_FILE;
		final String[] paths = { "a.", "a/b", "a/c/e", "a/c/f", "a/d", "a0b" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(mode);
		}

		final DirCacheBuilder b = dc.builder();
		for (int i = 0; i < ents.length; i++)
			b.add(ents[i]);
		b.finish();

		DirCacheIterator dci = new DirCacheIterator(dc);
		assertFalse(dci.eof());
		assertEquals("a.", dci.getEntryPathString());
		dci.next(1);
		assertFalse(dci.eof());
		assertEquals("a", dci.getEntryPathString());
		dci.next(1);
		assertFalse(dci.eof());
		assertEquals("a0b", dci.getEntryPathString());
		dci.next(1);
		assertTrue(dci.eof());

		// same entries the second time
		dci.reset();
		assertFalse(dci.eof());
		assertEquals("a.", dci.getEntryPathString());
		dci.next(1);
		assertFalse(dci.eof());
		assertEquals("a", dci.getEntryPathString());
		dci.next(1);
		assertFalse(dci.eof());
		assertEquals("a0b", dci.getEntryPathString());
		dci.next(1);
		assertTrue(dci.eof());

		// Step backwards
		dci.back(1);
		assertFalse(dci.eof());
		assertEquals("a0b", dci.getEntryPathString());
		dci.back(1);
		assertFalse(dci.eof());
		assertEquals("a", dci.getEntryPathString());
		dci.back(1);
		assertFalse(dci.eof());
		assertEquals("a.", dci.getEntryPathString());
		assertTrue(dci.first());

		// forward
		assertFalse(dci.eof());
		assertEquals("a.", dci.getEntryPathString());
		dci.next(1);
		assertFalse(dci.eof());
		assertEquals("a", dci.getEntryPathString());
		dci.next(1);
		assertFalse(dci.eof());
		assertEquals("a0b", dci.getEntryPathString());
		dci.next(1);
		assertTrue(dci.eof());

		// backwqrd halways, and forward again
		dci.back(1);
		assertFalse(dci.eof());
		assertEquals("a0b", dci.getEntryPathString());
		dci.back(1);
		assertFalse(dci.eof());
		assertEquals("a", dci.getEntryPathString());

		dci.next(1);
		assertFalse(dci.eof());
		assertEquals("a0b", dci.getEntryPathString());
		dci.next(1);
		assertTrue(dci.eof());

		dci.reset(); // a.
		dci.next(1); // a
		AbstractTreeIterator sti = dci.createSubtreeIterator(null);
		assertEquals("a/b", sti.getEntryPathString());
		sti.next(1);
		assertEquals("a/c", sti.getEntryPathString());
		sti.next(1);
		assertEquals("a/d", sti.getEntryPathString());
		sti.back(2);
		assertEquals("a/b", sti.getEntryPathString());

	}

	@Test
	public void testBackBug396127() throws Exception {
		final DirCache dc = DirCache.newInCore();

		final FileMode mode = FileMode.REGULAR_FILE;
		final String[] paths = { "git-gui/po/fr.po",
				"git_remote_helpers/git/repo.py" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(mode);
		}

		final DirCacheBuilder b = dc.builder();
		for (int i = 0; i < ents.length; i++)
			b.add(ents[i]);
		b.finish();

		DirCacheIterator dci = new DirCacheIterator(dc);
		assertFalse(dci.eof());
		assertEquals("git-gui", dci.getEntryPathString());
		dci.next(1);
		assertFalse(dci.eof());
		assertEquals("git_remote_helpers", dci.getEntryPathString());
		dci.back(1);
		assertFalse(dci.eof());
		assertEquals("git-gui", dci.getEntryPathString());
		dci.next(1);
		assertEquals("git_remote_helpers", dci.getEntryPathString());
		dci.next(1);
		assertTrue(dci.eof());

	}

	@Test
	public void testTwoLevelSubtree_FilterPath() throws Exception {
		final DirCache dc = DirCache.newInCore();

		final FileMode mode = FileMode.REGULAR_FILE;
		final String[] paths = { "a.", "a/b", "a/c/e", "a/c/f", "a/d", "a0b" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(mode);
		}

		final DirCacheBuilder b = dc.builder();
		for (int i = 0; i < ents.length; i++)
			b.add(ents[i]);
		b.finish();

		final TreeWalk tw = new TreeWalk(db);
		for (int victimIdx = 0; victimIdx < paths.length; victimIdx++) {
			tw.reset();
			tw.addTree(new DirCacheIterator(dc));
			tw.setFilter(PathFilterGroup.createFromStrings(Collections
					.singleton(paths[victimIdx])));
			tw.setRecursive(tw.getFilter().shouldBeRecursive());
			assertTrue(tw.next());
			final DirCacheIterator c = tw.getTree(0, DirCacheIterator.class);
			assertNotNull(c);
			assertEquals(victimIdx, c.ptr);
			assertSame(ents[victimIdx], c.getDirCacheEntry());
			assertEquals(paths[victimIdx], tw.getPathString());
			assertEquals(mode.getBits(), tw.getRawMode(0));
			assertSame(mode, tw.getFileMode(0));
			assertFalse(tw.next());
		}
	}

	@Test
	public void testRemovedSubtree() throws Exception {
		final File path = JGitTestUtil
				.getTestResourceFile("dircache.testRemovedSubtree");

		final DirCache dc = DirCache.read(path, FS.DETECTED);
		assertEquals(2, dc.getEntryCount());

		final TreeWalk tw = new TreeWalk(db);
		tw.setRecursive(true);
		tw.addTree(new DirCacheIterator(dc));

		assertTrue(tw.next());
		assertEquals("a/a", tw.getPathString());
		assertSame(FileMode.REGULAR_FILE, tw.getFileMode(0));

		assertTrue(tw.next());
		assertEquals("q", tw.getPathString());
		assertSame(FileMode.REGULAR_FILE, tw.getFileMode(0));

		assertFalse(tw.next());
	}
}
