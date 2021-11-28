/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Imran M Yousuf <imyousuf@smartitengineering.com>
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.zip.Deflater;

import org.eclipse.jgit.errors.TooLargeObjectInPackException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.storage.file.ObjectDirectoryPackParser;
import org.eclipse.jgit.storage.file.PackFile;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.io.UnionInputStream;
import org.junit.After;
import org.junit.Test;

/**
 * Test indexing of git packs. A pack is read from a stream, copied
 * to a new pack and an index is created. Then the packs are tested
 * to make sure they contain the expected objects (well we don't test
 * for all of them unless the packs are very small).
 */
public class PackParserTest extends RepositoryTestCase {
	/**
	 * Test indexing one of the test packs in the egit repo. It has deltas.
	 *
	 * @throws IOException
	 */
	@Test
	public void test1() throws  IOException {
		File packFile = JGitTestUtil.getTestResourceFile("pack-34be9032ac282b11fa9babdc2b2a93ca996c9c2f.pack");
		final InputStream is = new FileInputStream(packFile);
		try {
			ObjectDirectoryPackParser p = (ObjectDirectoryPackParser) index(is);
			p.parse(NullProgressMonitor.INSTANCE);
			PackFile file = p.getPackFile();

			assertTrue(file.hasObject(ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904")));
			assertTrue(file.hasObject(ObjectId.fromString("540a36d136cf413e4b064c2b0e0a4db60f77feab")));
			assertTrue(file.hasObject(ObjectId.fromString("5b6e7c66c276e7610d4a73c70ec1a1f7c1003259")));
			assertTrue(file.hasObject(ObjectId.fromString("6ff87c4664981e4397625791c8ea3bbb5f2279a3")));
			assertTrue(file.hasObject(ObjectId.fromString("82c6b885ff600be425b4ea96dee75dca255b69e7")));
			assertTrue(file.hasObject(ObjectId.fromString("902d5476fa249b7abc9d84c611577a81381f0327")));
			assertTrue(file.hasObject(ObjectId.fromString("aabf2ffaec9b497f0950352b3e582d73035c2035")));
			assertTrue(file.hasObject(ObjectId.fromString("c59759f143fb1fe21c197981df75a7ee00290799")));
		} finally {
			is.close();
		}
	}

	/**
	 * This is just another pack. It so happens that we have two convenient pack to
	 * test with in the repository.
	 *
	 * @throws IOException
	 */
	@Test
	public void test2() throws  IOException {
		File packFile = JGitTestUtil.getTestResourceFile("pack-df2982f284bbabb6bdb59ee3fcc6eb0983e20371.pack");
		final InputStream is = new FileInputStream(packFile);
		try {
			ObjectDirectoryPackParser p = (ObjectDirectoryPackParser) index(is);
			p.parse(NullProgressMonitor.INSTANCE);
			PackFile file = p.getPackFile();

			assertTrue(file.hasObject(ObjectId.fromString("02ba32d3649e510002c21651936b7077aa75ffa9")));
			assertTrue(file.hasObject(ObjectId.fromString("0966a434eb1a025db6b71485ab63a3bfbea520b6")));
			assertTrue(file.hasObject(ObjectId.fromString("09efc7e59a839528ac7bda9fa020dc9101278680")));
			assertTrue(file.hasObject(ObjectId.fromString("0a3d7772488b6b106fb62813c4d6d627918d9181")));
			assertTrue(file.hasObject(ObjectId.fromString("1004d0d7ac26fbf63050a234c9b88a46075719d3")));
			assertTrue(file.hasObject(ObjectId.fromString("10da5895682013006950e7da534b705252b03be6")));
			assertTrue(file.hasObject(ObjectId.fromString("1203b03dc816ccbb67773f28b3c19318654b0bc8")));
			assertTrue(file.hasObject(ObjectId.fromString("15fae9e651043de0fd1deef588aa3fbf5a7a41c6")));
			assertTrue(file.hasObject(ObjectId.fromString("16f9ec009e5568c435f473ba3a1df732d49ce8c3")));
			assertTrue(file.hasObject(ObjectId.fromString("1fd7d579fb6ae3fe942dc09c2c783443d04cf21e")));
			assertTrue(file.hasObject(ObjectId.fromString("20a8ade77639491ea0bd667bf95de8abf3a434c8")));
			assertTrue(file.hasObject(ObjectId.fromString("2675188fd86978d5bc4d7211698b2118ae3bf658")));
			// and lots more...
		} finally {
			is.close();
		}
	}

	@Test
	public void testTinyThinPack() throws Exception {
		TestRepository d = new TestRepository<Repository>(db);
		RevBlob a = d.blob("a");

		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);

		packHeader(pack, 1);

		pack.write((Constants.OBJ_REF_DELTA) << 4 | 4);
		a.copyRawTo(pack);
		deflate(pack, new byte[] { 0x1, 0x1, 0x1, 'b' });

		digest(pack);

		PackParser p = index(new ByteArrayInputStream(pack.toByteArray()));
		p.setAllowThin(true);
		p.parse(NullProgressMonitor.INSTANCE);
	}

	@Test
	public void testPackWithDuplicateBlob() throws Exception {
		final byte[] data = Constants.encode("0123456789abcdefg");
		TestRepository<Repository> d = new TestRepository<Repository>(db);
		assertTrue(db.hasObject(d.blob(data)));

		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);
		packHeader(pack, 1);
		pack.write((Constants.OBJ_BLOB) << 4 | 0x80 | 1);
		pack.write(1);
		deflate(pack, data);
		digest(pack);

		PackParser p = index(new ByteArrayInputStream(pack.toByteArray()));
		p.setAllowThin(false);
		p.parse(NullProgressMonitor.INSTANCE);
	}

	@Test
	public void testPackWithTrailingGarbage() throws Exception {
		TestRepository d = new TestRepository<Repository>(db);
		RevBlob a = d.blob("a");

		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);
		packHeader(pack, 1);
		pack.write((Constants.OBJ_REF_DELTA) << 4 | 4);
		a.copyRawTo(pack);
		deflate(pack, new byte[] { 0x1, 0x1, 0x1, 'b' });
		digest(pack);

		PackParser p = index(new UnionInputStream(
				new ByteArrayInputStream(pack.toByteArray()),
				new ByteArrayInputStream(new byte[] { 0x7e })));
		p.setAllowThin(true);
		p.setCheckEofAfterPackFooter(true);
		try {
			p.parse(NullProgressMonitor.INSTANCE);
			fail("Pack with trailing garbage was accepted");
		} catch (IOException err) {
			assertEquals(
					MessageFormat.format(JGitText.get().expectedEOFReceived, "\\x7e"),
					err.getMessage());
		}
	}

	@Test
	public void testMaxObjectSizeFullBlob() throws Exception {
		TestRepository d = new TestRepository<Repository>(db);
		final byte[] data = Constants.encode("0123456789");
		d.blob(data);

		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);

		packHeader(pack, 1);
		pack.write((Constants.OBJ_BLOB) << 4 | 10);
		deflate(pack, data);
		digest(pack);

		PackParser p = index(new ByteArrayInputStream(pack.toByteArray()));
		p.setMaxObjectSizeLimit(11);
		p.parse(NullProgressMonitor.INSTANCE);

		p = index(new ByteArrayInputStream(pack.toByteArray()));
		p.setMaxObjectSizeLimit(10);
		p.parse(NullProgressMonitor.INSTANCE);

		p = index(new ByteArrayInputStream(pack.toByteArray()));
		p.setMaxObjectSizeLimit(9);
		try {
			p.parse(NullProgressMonitor.INSTANCE);
			fail("PackParser should have failed");
		} catch (TooLargeObjectInPackException e) {
			assertTrue(e.getMessage().contains("10")); // obj size
			assertTrue(e.getMessage().contains("9")); // max obj size
		}
	}

	@Test
	public void testMaxObjectSizeDeltaBlock() throws Exception {
		TestRepository d = new TestRepository<Repository>(db);
		RevBlob a = d.blob("a");

		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);

		packHeader(pack, 1);
		pack.write((Constants.OBJ_REF_DELTA) << 4 | 14);
		a.copyRawTo(pack);
		deflate(pack, new byte[] { 1, 11, 11, 'a', '0', '1', '2', '3', '4',
				'5', '6', '7', '8', '9' });
		digest(pack);

		PackParser p = index(new ByteArrayInputStream(pack.toByteArray()));
		p.setAllowThin(true);
		p.setMaxObjectSizeLimit(14);
		p.parse(NullProgressMonitor.INSTANCE);

		p = index(new ByteArrayInputStream(pack.toByteArray()));
		p.setAllowThin(true);
		p.setMaxObjectSizeLimit(13);
		try {
			p.parse(NullProgressMonitor.INSTANCE);
			fail("PackParser should have failed");
		} catch (TooLargeObjectInPackException e) {
			assertTrue(e.getMessage().contains("13")); // max obj size
			assertFalse(e.getMessage().contains("14")); // no delta size
		}
	}

	@Test
	public void testMaxObjectSizeDeltaResultSize() throws Exception {
		TestRepository d = new TestRepository<Repository>(db);
		RevBlob a = d.blob("0123456789");

		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);

		packHeader(pack, 1);
		pack.write((Constants.OBJ_REF_DELTA) << 4 | 4);
		a.copyRawTo(pack);
		deflate(pack, new byte[] { 10, 11, 1, 'a' });
		digest(pack);

		PackParser p = index(new ByteArrayInputStream(pack.toByteArray()));
		p.setAllowThin(true);
		p.setMaxObjectSizeLimit(11);
		p.parse(NullProgressMonitor.INSTANCE);

		p = index(new ByteArrayInputStream(pack.toByteArray()));
		p.setAllowThin(true);
		p.setMaxObjectSizeLimit(10);
		try {
			p.parse(NullProgressMonitor.INSTANCE);
			fail("PackParser should have failed");
		} catch (TooLargeObjectInPackException e) {
			assertTrue(e.getMessage().contains("11")); // result obj size
			assertTrue(e.getMessage().contains("10")); // max obj size
		}
	}

	@Test
	public void testNonMarkingInputStream() throws Exception {
		TestRepository d = new TestRepository<Repository>(db);
		RevBlob a = d.blob("a");

		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);
		packHeader(pack, 1);
		pack.write((Constants.OBJ_REF_DELTA) << 4 | 4);
		a.copyRawTo(pack);
		deflate(pack, new byte[] { 0x1, 0x1, 0x1, 'b' });
		digest(pack);

		InputStream in = new ByteArrayInputStream(pack.toByteArray()) {
			@Override
			public boolean markSupported() {
				return false;
			}

			@Override
			public void mark(int maxlength) {
				fail("Mark should not be called");
			}
		};

		PackParser p = index(in);
		p.setAllowThin(true);
		p.setCheckEofAfterPackFooter(false);
		p.setExpectDataAfterPackFooter(true);

		try {
			p.parse(NullProgressMonitor.INSTANCE);
			fail("PackParser should have failed");
		} catch (IOException e) {
			assertEquals(e.getMessage(),
					JGitText.get().inputStreamMustSupportMark);
		}
	}

	@Test
	public void testDataAfterPackFooterSingleRead() throws Exception {
		TestRepository d = new TestRepository<Repository>(db);
		RevBlob a = d.blob("a");

		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(32*1024);
		packHeader(pack, 1);
		pack.write((Constants.OBJ_REF_DELTA) << 4 | 4);
		a.copyRawTo(pack);
		deflate(pack, new byte[] { 0x1, 0x1, 0x1, 'b' });
		digest(pack);

		byte packData[] = pack.toByteArray();
		byte streamData[] = new byte[packData.length + 1];
		System.arraycopy(packData, 0, streamData, 0, packData.length);
		streamData[packData.length] = 0x7e;

		InputStream in = new ByteArrayInputStream(streamData);
		PackParser p = index(in);
		p.setAllowThin(true);
		p.setCheckEofAfterPackFooter(false);
		p.setExpectDataAfterPackFooter(true);

		p.parse(NullProgressMonitor.INSTANCE);

		assertEquals(0x7e, in.read());
	}

	@Test
	public void testDataAfterPackFooterSplitObjectRead() throws Exception {
		final byte[] data = Constants.encode("0123456789");

		// Build a pack ~17k
		int objects = 900;
		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(32 * 1024);
		packHeader(pack, objects);

		for (int i = 0; i < objects; i++) {
			pack.write((Constants.OBJ_BLOB) << 4 | 10);
			deflate(pack, data);
		}
		digest(pack);

		byte packData[] = pack.toByteArray();
		byte streamData[] = new byte[packData.length + 1];
		System.arraycopy(packData, 0, streamData, 0, packData.length);
		streamData[packData.length] = 0x7e;
		InputStream in = new ByteArrayInputStream(streamData);
		PackParser p = index(in);
		p.setAllowThin(true);
		p.setCheckEofAfterPackFooter(false);
		p.setExpectDataAfterPackFooter(true);

		p.parse(NullProgressMonitor.INSTANCE);

		assertEquals(0x7e, in.read());
	}

	@Test
	public void testDataAfterPackFooterSplitHeaderRead() throws Exception {
		TestRepository d = new TestRepository<Repository>(db);
		final byte[] data = Constants.encode("a");
		RevBlob b = d.blob(data);

		int objects = 248;
		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(32 * 1024);
		packHeader(pack, objects + 1);
		int offset = 13;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < offset; i++)
			sb.append(i);
		offset = sb.toString().length();
		int lenByte = (Constants.OBJ_BLOB) << 4 | (offset & 0x0F);
		offset >>= 4;
		if (offset > 0)
			lenByte |= 1 << 7;
		pack.write(lenByte);
		while (offset > 0) {
			lenByte = offset & 0x7F;
			offset >>= 6;
			if (offset > 0)
				lenByte |= 1 << 7;
			pack.write(lenByte);
		}
		deflate(pack, Constants.encode(sb.toString()));

		for (int i = 0; i < objects; i++) {
			// The last pack header written falls across the 8192 byte boundary
			// between [8189:8210]
			pack.write((Constants.OBJ_REF_DELTA) << 4 | 4);
			b.copyRawTo(pack);
			deflate(pack, new byte[] { 0x1, 0x1, 0x1, 'b' });
		}
		digest(pack);

		byte packData[] = pack.toByteArray();
		byte streamData[] = new byte[packData.length + 1];
		System.arraycopy(packData, 0, streamData, 0, packData.length);
		streamData[packData.length] = 0x7e;
		InputStream in = new ByteArrayInputStream(streamData);
		PackParser p = index(in);
		p.setAllowThin(true);
		p.setCheckEofAfterPackFooter(false);
		p.setExpectDataAfterPackFooter(true);

		p.parse(NullProgressMonitor.INSTANCE);

		assertEquals(0x7e, in.read());
	}

	private static void packHeader(TemporaryBuffer.Heap tinyPack, int cnt)
			throws IOException {
		final byte[] hdr = new byte[8];
		NB.encodeInt32(hdr, 0, 2);
		NB.encodeInt32(hdr, 4, cnt);

		tinyPack.write(Constants.PACK_SIGNATURE);
		tinyPack.write(hdr, 0, 8);
	}

	private static void deflate(TemporaryBuffer.Heap tinyPack,
			final byte[] content)
			throws IOException {
		final Deflater deflater = new Deflater();
		final byte[] buf = new byte[128];
		deflater.setInput(content, 0, content.length);
		deflater.finish();
		do {
			final int n = deflater.deflate(buf, 0, buf.length);
			if (n > 0)
				tinyPack.write(buf, 0, n);
		} while (!deflater.finished());
	}

	private static void digest(TemporaryBuffer.Heap buf) throws IOException {
		MessageDigest md = Constants.newMessageDigest();
		md.update(buf.toByteArray());
		buf.write(md.digest());
	}

	private ObjectInserter inserter;

	@After
	public void release() {
		if (inserter != null)
			inserter.release();
	}

	private PackParser index(InputStream in) throws IOException {
		if (inserter == null)
			inserter = db.newObjectInserter();
		return inserter.newPackParser(in);
	}
}
