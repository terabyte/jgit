/*
 * Copyright (C) 2012, Marc Strapetz
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.junit.After;
import org.junit.Test;

public class FileBasedConfigTest {

	private static final String USER = "user";

	private static final String NAME = "name";

	private static final String ALICE = "Alice";

	private static final String BOB = "Bob";

	private static final String CONTENT1 = "[" + USER + "]\n\t" + NAME + " = "
			+ ALICE + "\n";

	private static final String CONTENT2 = "[" + USER + "]\n\t" + NAME + " = "
			+ BOB + "\n";

	private final File trash = new File(new File("target"), "trash");

	@After
	public void tearDown() throws Exception {
		FileUtils.delete(trash, FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
	}

	@Test
	public void testSystemEncoding() throws IOException, ConfigInvalidException {
		final File file = createFile(CONTENT1.getBytes());
		final FileBasedConfig config = new FileBasedConfig(file, FS.DETECTED);
		config.load();
		assertEquals(ALICE, config.getString(USER, null, NAME));

		config.setString(USER, null, NAME, BOB);
		config.save();
		assertArrayEquals(CONTENT2.getBytes(), IO.readFully(file));
	}

	@Test
	public void testUTF8withoutBOM() throws IOException, ConfigInvalidException {
		final File file = createFile(CONTENT1.getBytes("UTF-8"));
		final FileBasedConfig config = new FileBasedConfig(file, FS.DETECTED);
		config.load();
		assertEquals(ALICE, config.getString(USER, null, NAME));

		config.setString(USER, null, NAME, BOB);
		config.save();
		assertArrayEquals(CONTENT2.getBytes(), IO.readFully(file));
	}

	@Test
	public void testUTF8withBOM() throws IOException, ConfigInvalidException {
		final ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
		bos1.write(0xEF);
		bos1.write(0xBB);
		bos1.write(0xBF);
		bos1.write(CONTENT1.getBytes("UTF-8"));

		final File file = createFile(bos1.toByteArray());
		final FileBasedConfig config = new FileBasedConfig(file, FS.DETECTED);
		config.load();
		assertEquals(ALICE, config.getString(USER, null, NAME));

		config.setString(USER, null, NAME, BOB);
		config.save();

		final ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
		bos2.write(0xEF);
		bos2.write(0xBB);
		bos2.write(0xBF);
		bos2.write(CONTENT2.getBytes("UTF-8"));
		assertArrayEquals(bos2.toByteArray(), IO.readFully(file));
	}

	@Test
	public void testLeadingWhitespaces() throws IOException, ConfigInvalidException {
		final ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
		bos1.write(" \n\t".getBytes());
		bos1.write(CONTENT1.getBytes());

		final File file = createFile(bos1.toByteArray());
		final FileBasedConfig config = new FileBasedConfig(file, FS.DETECTED);
		config.load();
		assertEquals(ALICE, config.getString(USER, null, NAME));

		config.setString(USER, null, NAME, BOB);
		config.save();

		final ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
		bos2.write(" \n\t".getBytes());
		bos2.write(CONTENT2.getBytes());
		assertArrayEquals(bos2.toByteArray(), IO.readFully(file));
	}

	private File createFile(byte[] content) throws IOException {
		trash.mkdirs();
		File f = File.createTempFile(getClass().getName(), null, trash);
		FileOutputStream os = new FileOutputStream(f, true);
		try {
			os.write(content);
		} finally {
			os.close();
		}
		return f;
	}
}
