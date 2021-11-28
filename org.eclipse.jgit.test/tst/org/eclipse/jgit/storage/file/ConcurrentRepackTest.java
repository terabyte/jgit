/*
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.io.SafeBufferedOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ConcurrentRepackTest extends RepositoryTestCase {
	@Before
	public void setUp() throws Exception {
		WindowCacheConfig windowCacheConfig = new WindowCacheConfig();
		windowCacheConfig.setPackedGitOpenFiles(1);
		WindowCache.reconfigure(windowCacheConfig);
		super.setUp();
	}

	@After
	public void tearDown() throws Exception {
		super.tearDown();
		WindowCacheConfig windowCacheConfig = new WindowCacheConfig();
		WindowCache.reconfigure(windowCacheConfig);
	}

	@Test
	public void testObjectInNewPack() throws IncorrectObjectTypeException,
			IOException {
		// Create a new object in a new pack, and test that it is present.
		//
		final Repository eden = createBareRepository();
		final RevObject o1 = writeBlob(eden, "o1");
		pack(eden, o1);
		assertEquals(o1.name(), parse(o1).name());
	}

	@Test
	public void testObjectMovedToNewPack1()
			throws IncorrectObjectTypeException, IOException {
		// Create an object and pack it. Then remove that pack and put the
		// object into a different pack file, with some other object. We
		// still should be able to access the objects.
		//
		final Repository eden = createBareRepository();
		final RevObject o1 = writeBlob(eden, "o1");
		final File[] out1 = pack(eden, o1);
		assertEquals(o1.name(), parse(o1).name());

		final RevObject o2 = writeBlob(eden, "o2");
		pack(eden, o2, o1);

		// Force close, and then delete, the old pack.
		//
		whackCache();
		delete(out1);

		// Now here is the interesting thing. Will git figure the new
		// object exists in the new pack, and not the old one.
		//
		assertEquals(o2.name(), parse(o2).name());
		assertEquals(o1.name(), parse(o1).name());
	}

	@Test
	public void testObjectMovedWithinPack()
			throws IncorrectObjectTypeException, IOException {
		// Create an object and pack it.
		//
		final Repository eden = createBareRepository();
		final RevObject o1 = writeBlob(eden, "o1");
		final File[] out1 = pack(eden, o1);
		assertEquals(o1.name(), parse(o1).name());

		// Force close the old pack.
		//
		whackCache();

		// Now overwrite the old pack in place. This method of creating a
		// different pack under the same file name is partially broken. We
		// should also have a different file name because the list of objects
		// within the pack has been modified.
		//
		final RevObject o2 = writeBlob(eden, "o2");
		final PackWriter pw = new PackWriter(eden);
		pw.addObject(o2);
		pw.addObject(o1);
		write(out1, pw);
		pw.release();

		// Try the old name, then the new name. The old name should cause the
		// pack to reload when it opens and the index and pack mismatch.
		//
		assertEquals(o1.name(), parse(o1).name());
		assertEquals(o2.name(), parse(o2).name());
	}

	@Test
	public void testObjectMovedToNewPack2()
			throws IncorrectObjectTypeException, IOException {
		// Create an object and pack it. Then remove that pack and put the
		// object into a different pack file, with some other object. We
		// still should be able to access the objects.
		//
		final Repository eden = createBareRepository();
		final RevObject o1 = writeBlob(eden, "o1");
		final File[] out1 = pack(eden, o1);
		assertEquals(o1.name(), parse(o1).name());

		final ObjectLoader load1 = db.open(o1, Constants.OBJ_BLOB);
		assertNotNull(load1);

		final RevObject o2 = writeBlob(eden, "o2");
		pack(eden, o2, o1);

		// Force close, and then delete, the old pack.
		//
		whackCache();
		delete(out1);

		// Now here is the interesting thing... can the loader we made
		// earlier still resolve the object, even though its underlying
		// pack is gone, but the object still exists.
		//
		final ObjectLoader load2 = db.open(o1, Constants.OBJ_BLOB);
		assertNotNull(load2);
		assertNotSame(load1, load2);

		final byte[] data2 = load2.getCachedBytes();
		final byte[] data1 = load1.getCachedBytes();
		assertNotNull(data2);
		assertNotNull(data1);
		assertNotSame(data1, data2); // cache should be per-pack, not per object
		assertArrayEquals(data1, data2);
		assertEquals(load2.getType(), load1.getType());
	}

	private static void whackCache() {
		final WindowCacheConfig config = new WindowCacheConfig();
		config.setPackedGitOpenFiles(1);
		WindowCache.reconfigure(config);
	}

	private RevObject parse(final AnyObjectId id)
			throws MissingObjectException, IOException {
		return new RevWalk(db).parseAny(id);
	}

	private File[] pack(final Repository src, final RevObject... list)
			throws IOException {
		final PackWriter pw = new PackWriter(src);
		for (final RevObject o : list) {
			pw.addObject(o);
		}

		final ObjectId name = pw.computeName();
		final File packFile = fullPackFileName(name, ".pack");
		final File idxFile = fullPackFileName(name, ".idx");
		final File[] files = new File[] { packFile, idxFile };
		write(files, pw);
		pw.release();
		return files;
	}

	private static void write(final File[] files, final PackWriter pw)
			throws IOException {
		final long begin = files[0].getParentFile().lastModified();
		NullProgressMonitor m = NullProgressMonitor.INSTANCE;
		OutputStream out;

		out = new SafeBufferedOutputStream(new FileOutputStream(files[0]));
		try {
			pw.writePack(m, m, out);
		} finally {
			out.close();
		}

		out = new SafeBufferedOutputStream(new FileOutputStream(files[1]));
		try {
			pw.writeIndex(out);
		} finally {
			out.close();
		}

		touch(begin, files[0].getParentFile());
	}

	private static void delete(final File[] list) throws IOException {
		final long begin = list[0].getParentFile().lastModified();
		for (final File f : list) {
			FileUtils.delete(f);
			assertFalse(f + " was removed", f.exists());
		}
		touch(begin, list[0].getParentFile());
	}

	private static void touch(final long begin, final File dir) {
		while (begin >= dir.lastModified()) {
			try {
				Thread.sleep(25);
			} catch (InterruptedException ie) {
				//
			}
			dir.setLastModified(System.currentTimeMillis());
		}
	}

	private File fullPackFileName(final ObjectId name, final String suffix) {
		final File packdir = new File(db.getObjectDatabase().getDirectory(), "pack");
		return new File(packdir, "pack-" + name.name() + suffix);
	}

	private RevObject writeBlob(final Repository repo, final String data)
			throws IOException {
		final RevWalk revWalk = new RevWalk(repo);
		final byte[] bytes = Constants.encode(data);
		final ObjectInserter inserter = repo.newObjectInserter();
		final ObjectId id;
		try {
			id = inserter.insert(Constants.OBJ_BLOB, bytes);
			inserter.flush();
		} finally {
			inserter.release();
		}
		try {
			parse(id);
			fail("Object " + id.name() + " should not exist in test repository");
		} catch (MissingObjectException e) {
			// Ok
		}
		return revWalk.lookupBlob(id);
	}
}
