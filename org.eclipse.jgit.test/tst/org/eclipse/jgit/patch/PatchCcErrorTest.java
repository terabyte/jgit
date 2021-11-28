/*
 * Copyright (C) 2008, Google Inc.
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

package org.eclipse.jgit.patch;

import static java.lang.Integer.valueOf;
import static java.lang.Long.valueOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.junit.Test;

public class PatchCcErrorTest {
	@Test
	public void testError_CcTruncatedOld() throws IOException {
		final Patch p = parseTestPatchFile();
		assertEquals(1, p.getFiles().size());
		assertEquals(3, p.getErrors().size());
		{
			final FormatError e = p.getErrors().get(0);
			assertSame(FormatError.Severity.ERROR, e.getSeverity());
			assertEquals(MessageFormat.format(
					JGitText.get().truncatedHunkLinesMissingForAncestor,
					valueOf(1), valueOf(1)), e.getMessage());
			assertEquals(346, e.getOffset());
			assertTrue(e.getLineText().startsWith(
					"@@@ -55,12 -163,13 +163,15 @@@ public "));
		}
		{
			final FormatError e = p.getErrors().get(1);
			assertSame(FormatError.Severity.ERROR, e.getSeverity());
			assertEquals(MessageFormat.format(
					JGitText.get().truncatedHunkLinesMissingForAncestor,
					valueOf(2), valueOf(2)), e.getMessage());
			assertEquals(346, e.getOffset());
			assertTrue(e.getLineText().startsWith(
					"@@@ -55,12 -163,13 +163,15 @@@ public "));
		}
		{
			final FormatError e = p.getErrors().get(2);
			assertSame(FormatError.Severity.ERROR, e.getSeverity());
			assertEquals("Truncated hunk, at least 3 new lines is missing", e
					.getMessage());
			assertEquals(346, e.getOffset());
			assertTrue(e.getLineText().startsWith(
					"@@@ -55,12 -163,13 +163,15 @@@ public "));
		}
	}

	private Patch parseTestPatchFile() throws IOException {
		final String patchFile = JGitTestUtil.getName() + ".patch";
		final InputStream in = getClass().getResourceAsStream(patchFile);
		if (in == null) {
			fail("No " + patchFile + " test vector");
			return null; // Never happens
		}
		try {
			final Patch p = new Patch();
			p.parse(in);
			return p;
		} finally {
			in.close();
		}
	}

}
