/*
 * Copyright (C) 2010, Red Hat Inc.
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
package org.eclipse.jgit.ignore;

import static org.eclipse.jgit.junit.Assert.assertEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

/**
 * Tests ignore node behavior on the local filesystem.
 */
public class IgnoreNodeTest extends RepositoryTestCase {
	private static final FileMode D = FileMode.TREE;

	private static final FileMode F = FileMode.REGULAR_FILE;

	private static final boolean ignored = true;

	private static final boolean tracked = false;

	private TreeWalk walk;

	@Test
	public void testRules() throws IOException {
		writeIgnoreFile(".git/info/exclude", "*~", "/out");

		writeIgnoreFile(".gitignore", "*.o", "/config");
		writeTrashFile("config/secret", "");
		writeTrashFile("mylib.c", "");
		writeTrashFile("mylib.c~", "");
		writeTrashFile("mylib.o", "");

		writeTrashFile("out/object/foo.exe", "");
		writeIgnoreFile("src/config/.gitignore", "lex.out");
		writeTrashFile("src/config/lex.out", "");
		writeTrashFile("src/config/config.c", "");
		writeTrashFile("src/config/config.c~", "");
		writeTrashFile("src/config/old/lex.out", "");

		beginWalk();
		assertEntry(F, tracked, ".gitignore");
		assertEntry(D, ignored, "config");
		assertEntry(F, ignored, "config/secret");
		assertEntry(F, tracked, "mylib.c");
		assertEntry(F, ignored, "mylib.c~");
		assertEntry(F, ignored, "mylib.o");

		assertEntry(D, ignored, "out");
		assertEntry(D, ignored, "out/object");
		assertEntry(F, ignored, "out/object/foo.exe");

		assertEntry(D, tracked, "src");
		assertEntry(D, tracked, "src/config");
		assertEntry(F, tracked, "src/config/.gitignore");
		assertEntry(F, tracked, "src/config/config.c");
		assertEntry(F, ignored, "src/config/config.c~");
		assertEntry(F, ignored, "src/config/lex.out");
		assertEntry(D, tracked, "src/config/old");
		assertEntry(F, ignored, "src/config/old/lex.out");
	}

	@Test
	public void testNegation() throws IOException {
		writeIgnoreFile(".gitignore", "*.o");
		writeIgnoreFile("src/a/b/.gitignore", "!keep.o");
		writeTrashFile("src/a/b/keep.o", "");
		writeTrashFile("src/a/b/nothere.o", "");

		beginWalk();
		assertEntry(F, tracked, ".gitignore");
		assertEntry(D, tracked, "src");
		assertEntry(D, tracked, "src/a");
		assertEntry(D, tracked, "src/a/b");
		assertEntry(F, tracked, "src/a/b/.gitignore");
		assertEntry(F, tracked, "src/a/b/keep.o");
		assertEntry(F, ignored, "src/a/b/nothere.o");
	}

	@Test
	public void testSlashOnlyMatchesDirectory() throws IOException {
		writeIgnoreFile(".gitignore", "out/");
		writeTrashFile("out", "");

		beginWalk();
		assertEntry(F, tracked, ".gitignore");
		assertEntry(F, tracked, "out");

		FileUtils.delete(new File(trash, "out"));
		writeTrashFile("out/foo", "");

		beginWalk();
		assertEntry(F, tracked, ".gitignore");
		assertEntry(D, ignored, "out");
		assertEntry(F, ignored, "out/foo");
	}

	@Test
	public void testWithSlashDoesNotMatchInSubDirectory() throws IOException {
		writeIgnoreFile(".gitignore", "a/b");
		writeTrashFile("a/a", "");
		writeTrashFile("a/b", "");
		writeTrashFile("src/a/a", "");
		writeTrashFile("src/a/b", "");

		beginWalk();
		assertEntry(F, tracked, ".gitignore");
		assertEntry(D, tracked, "a");
		assertEntry(F, tracked, "a/a");
		assertEntry(F, ignored, "a/b");
		assertEntry(D, tracked, "src");
		assertEntry(D, tracked, "src/a");
		assertEntry(F, tracked, "src/a/a");
		assertEntry(F, tracked, "src/a/b");
	}

	private void beginWalk() throws CorruptObjectException {
		walk = new TreeWalk(db);
		walk.addTree(new FileTreeIterator(db));
	}

	private void assertEntry(FileMode type, boolean entryIgnored,
			String pathName) throws IOException {
		assertTrue("walk has entry", walk.next());
		assertEquals(pathName, walk.getPathString());
		assertEquals(type, walk.getFileMode(0));

		WorkingTreeIterator itr = walk.getTree(0, WorkingTreeIterator.class);
		assertNotNull("has tree", itr);
		assertEquals("is ignored", entryIgnored, itr.isEntryIgnored());
		if (D.equals(type))
			walk.enterSubtree();
	}

	private void writeIgnoreFile(String name, String... rules)
			throws IOException {
		StringBuilder data = new StringBuilder();
		for (String line : rules)
			data.append(line + "\n");
		writeTrashFile(name, data.toString());
	}
}
