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

package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.TimeZone;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.junit.Test;

public class RevCommitParseTest extends RepositoryTestCase {
	@Test
	public void testParse_NoParents() throws Exception {
		final ObjectId treeId = id("9788669ad918b6fcce64af8882fc9a81cb6aba67");
		final String authorName = "A U. Thor";
		final String authorEmail = "a_u_thor@example.com";
		final int authorTime = 1218123387;
		final String authorTimeZone = "+0700";

		final String committerName = "C O. Miter";
		final String committerEmail = "comiter@example.com";
		final int committerTime = 1218123390;
		final String committerTimeZone = "-0500";
		final StringBuilder body = new StringBuilder();

		body.append("tree ");
		body.append(treeId.name());
		body.append("\n");

		body.append("author ");
		body.append(authorName);
		body.append(" <");
		body.append(authorEmail);
		body.append("> ");
		body.append(authorTime);
		body.append(" ");
		body.append(authorTimeZone);
		body.append(" \n");

		body.append("committer ");
		body.append(committerName);
		body.append(" <");
		body.append(committerEmail);
		body.append("> ");
		body.append(committerTime);
		body.append(" ");
		body.append(committerTimeZone);
		body.append("\n");

		body.append("\n");

		final RevWalk rw = new RevWalk(db);
		final RevCommit c;

		c = new RevCommit(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		assertNull(c.getTree());
		assertNull(c.parents);

		c.parseCanonical(rw, body.toString().getBytes("UTF-8"));
		assertNotNull(c.getTree());
		assertEquals(treeId, c.getTree().getId());
		assertSame(rw.lookupTree(treeId), c.getTree());

		assertNotNull(c.parents);
		assertEquals(0, c.parents.length);
		assertEquals("", c.getFullMessage());

		final PersonIdent cAuthor = c.getAuthorIdent();
		assertNotNull(cAuthor);
		assertEquals(authorName, cAuthor.getName());
		assertEquals(authorEmail, cAuthor.getEmailAddress());
		assertEquals((long)authorTime * 1000, cAuthor.getWhen().getTime());
		assertEquals(TimeZone.getTimeZone("GMT" + authorTimeZone), cAuthor.getTimeZone());

		final PersonIdent cCommitter = c.getCommitterIdent();
		assertNotNull(cCommitter);
		assertEquals(committerName, cCommitter.getName());
		assertEquals(committerEmail, cCommitter.getEmailAddress());
		assertEquals((long)committerTime * 1000, cCommitter.getWhen().getTime());
		assertEquals(TimeZone.getTimeZone("GMT" + committerTimeZone), cCommitter.getTimeZone());
	}

	private RevCommit create(final String msg) throws Exception {
		final StringBuilder b = new StringBuilder();
		b.append("tree 9788669ad918b6fcce64af8882fc9a81cb6aba67\n");
		b.append("author A U. Thor <a_u_thor@example.com> 1218123387 +0700\n");
		b.append("committer C O. Miter <c@example.com> 1218123390 -0500\n");
		b.append("\n");
		b.append(msg);

		final RevCommit c;
		c = new RevCommit(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		c.parseCanonical(new RevWalk(db), b.toString().getBytes("UTF-8"));
		return c;
	}

	@Test
	public void testParse_WeirdHeaderOnlyCommit() throws Exception {
		final StringBuilder b = new StringBuilder();
		b.append("tree 9788669ad918b6fcce64af8882fc9a81cb6aba67\n");
		b.append("author A U. Thor <a_u_thor@example.com> 1218123387 +0700\n");
		b.append("committer C O. Miter <c@example.com> 1218123390 -0500\n");

		final RevCommit c;
		c = new RevCommit(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		c.parseCanonical(new RevWalk(db), b.toString().getBytes("UTF-8"));

		assertEquals("", c.getFullMessage());
		assertEquals("", c.getShortMessage());
	}

	@Test
	public void testParse_incompleteAuthorAndCommitter() throws Exception {
		final StringBuilder b = new StringBuilder();
		b.append("tree 9788669ad918b6fcce64af8882fc9a81cb6aba67\n");
		b.append("author <a_u_thor@example.com> 1218123387 +0700\n");
		b.append("committer <> 1218123390 -0500\n");

		final RevCommit c;
		c = new RevCommit(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));
		c.parseCanonical(new RevWalk(db), b.toString().getBytes("UTF-8"));

		assertEquals(new PersonIdent("", "a_u_thor@example.com", 1218123387000l, 7), c.getAuthorIdent());
		assertEquals(new PersonIdent("", "", 1218123390000l, -5), c.getCommitterIdent());
	}

	@Test
	public void testParse_implicit_UTF8_encoded() throws Exception {
		final ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write("tree 9788669ad918b6fcce64af8882fc9a81cb6aba67\n".getBytes("UTF-8"));
		b.write("author F\u00f6r fattare <a_u_thor@example.com> 1218123387 +0700\n".getBytes("UTF-8"));
		b.write("committer C O. Miter <c@example.com> 1218123390 -0500\n".getBytes("UTF-8"));
		b.write("\n".getBytes("UTF-8"));
		b.write("Sm\u00f6rg\u00e5sbord\n".getBytes("UTF-8"));
		b.write("\n".getBytes("UTF-8"));
		b.write("\u304d\u308c\u3044\n".getBytes("UTF-8"));
		final RevCommit c;
		c = new RevCommit(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67")); // bogus id
		c.parseCanonical(new RevWalk(db), b.toByteArray());

		assertSame(Constants.CHARSET, c.getEncoding());
		assertEquals("F\u00f6r fattare", c.getAuthorIdent().getName());
		assertEquals("Sm\u00f6rg\u00e5sbord", c.getShortMessage());
		assertEquals("Sm\u00f6rg\u00e5sbord\n\n\u304d\u308c\u3044\n", c.getFullMessage());
	}

	@Test
	public void testParse_implicit_mixed_encoded() throws Exception {
		final ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write("tree 9788669ad918b6fcce64af8882fc9a81cb6aba67\n".getBytes("UTF-8"));
		b.write("author F\u00f6r fattare <a_u_thor@example.com> 1218123387 +0700\n".getBytes("ISO-8859-1"));
		b.write("committer C O. Miter <c@example.com> 1218123390 -0500\n".getBytes("UTF-8"));
		b.write("\n".getBytes("UTF-8"));
		b.write("Sm\u00f6rg\u00e5sbord\n".getBytes("UTF-8"));
		b.write("\n".getBytes("UTF-8"));
		b.write("\u304d\u308c\u3044\n".getBytes("UTF-8"));
		final RevCommit c;
		c = new RevCommit(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67")); // bogus id
		c.parseCanonical(new RevWalk(db), b.toByteArray());

		assertSame(Constants.CHARSET, c.getEncoding());
		assertEquals("F\u00f6r fattare", c.getAuthorIdent().getName());
		assertEquals("Sm\u00f6rg\u00e5sbord", c.getShortMessage());
		assertEquals("Sm\u00f6rg\u00e5sbord\n\n\u304d\u308c\u3044\n", c.getFullMessage());
	}

	/**
	 * Test parsing of a commit whose encoding is given and works.
	 *
	 * @throws Exception
	 */
	@Test
	public void testParse_explicit_encoded() throws Exception {
		final ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write("tree 9788669ad918b6fcce64af8882fc9a81cb6aba67\n".getBytes("EUC-JP"));
		b.write("author F\u00f6r fattare <a_u_thor@example.com> 1218123387 +0700\n".getBytes("EUC-JP"));
		b.write("committer C O. Miter <c@example.com> 1218123390 -0500\n".getBytes("EUC-JP"));
		b.write("encoding euc_JP\n".getBytes("EUC-JP"));
		b.write("\n".getBytes("EUC-JP"));
		b.write("\u304d\u308c\u3044\n".getBytes("EUC-JP"));
		b.write("\n".getBytes("EUC-JP"));
		b.write("Hi\n".getBytes("EUC-JP"));
		final RevCommit c;
		c = new RevCommit(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67")); // bogus id
		c.parseCanonical(new RevWalk(db), b.toByteArray());

		assertEquals("EUC-JP", c.getEncoding().name());
		assertEquals("F\u00f6r fattare", c.getAuthorIdent().getName());
		assertEquals("\u304d\u308c\u3044", c.getShortMessage());
		assertEquals("\u304d\u308c\u3044\n\nHi\n", c.getFullMessage());
	}

	/**
	 * This is a twisted case, but show what we expect here. We can revise the
	 * expectations provided this case is updated.
	 *
	 * What happens here is that an encoding us given, but data is not encoded
	 * that way (and we can detect it), so we try other encodings.
	 *
	 * @throws Exception
	 */
	@Test
	public void testParse_explicit_bad_encoded() throws Exception {
		final ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write("tree 9788669ad918b6fcce64af8882fc9a81cb6aba67\n".getBytes("UTF-8"));
		b.write("author F\u00f6r fattare <a_u_thor@example.com> 1218123387 +0700\n".getBytes("ISO-8859-1"));
		b.write("committer C O. Miter <c@example.com> 1218123390 -0500\n".getBytes("UTF-8"));
		b.write("encoding EUC-JP\n".getBytes("UTF-8"));
		b.write("\n".getBytes("UTF-8"));
		b.write("\u304d\u308c\u3044\n".getBytes("UTF-8"));
		b.write("\n".getBytes("UTF-8"));
		b.write("Hi\n".getBytes("UTF-8"));
		final RevCommit c;
		c = new RevCommit(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67")); // bogus id
		c.parseCanonical(new RevWalk(db), b.toByteArray());

		assertEquals("EUC-JP", c.getEncoding().name());
		assertEquals("F\u00f6r fattare", c.getAuthorIdent().getName());
		assertEquals("\u304d\u308c\u3044", c.getShortMessage());
		assertEquals("\u304d\u308c\u3044\n\nHi\n", c.getFullMessage());
	}

	/**
	 * This is a twisted case too, but show what we expect here. We can revise the
	 * expectations provided this case is updated.
	 *
	 * What happens here is that an encoding us given, but data is not encoded
	 * that way (and we can detect it), so we try other encodings. Here data could
	 * actually be decoded in the stated encoding, but we override using UTF-8.
	 *
	 * @throws Exception
	 */
	@Test
	public void testParse_explicit_bad_encoded2() throws Exception {
		final ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write("tree 9788669ad918b6fcce64af8882fc9a81cb6aba67\n".getBytes("UTF-8"));
		b.write("author F\u00f6r fattare <a_u_thor@example.com> 1218123387 +0700\n".getBytes("UTF-8"));
		b.write("committer C O. Miter <c@example.com> 1218123390 -0500\n".getBytes("UTF-8"));
		b.write("encoding ISO-8859-1\n".getBytes("UTF-8"));
		b.write("\n".getBytes("UTF-8"));
		b.write("\u304d\u308c\u3044\n".getBytes("UTF-8"));
		b.write("\n".getBytes("UTF-8"));
		b.write("Hi\n".getBytes("UTF-8"));
		final RevCommit c;
		c = new RevCommit(id("9473095c4cb2f12aefe1db8a355fe3fafba42f67")); // bogus id
		c.parseCanonical(new RevWalk(db), b.toByteArray());

		assertEquals("ISO-8859-1", c.getEncoding().name());
		assertEquals("F\u00f6r fattare", c.getAuthorIdent().getName());
		assertEquals("\u304d\u308c\u3044", c.getShortMessage());
		assertEquals("\u304d\u308c\u3044\n\nHi\n", c.getFullMessage());
	}

	@Test
	public void testParse_NoMessage() throws Exception {
		final String msg = "";
		final RevCommit c = create(msg);
		assertEquals(msg, c.getFullMessage());
		assertEquals(msg, c.getShortMessage());
	}

	@Test
	public void testParse_OnlyLFMessage() throws Exception {
		final RevCommit c = create("\n");
		assertEquals("\n", c.getFullMessage());
		assertEquals("", c.getShortMessage());
	}

	@Test
	public void testParse_ShortLineOnlyNoLF() throws Exception {
		final String shortMsg = "This is a short message.";
		final RevCommit c = create(shortMsg);
		assertEquals(shortMsg, c.getFullMessage());
		assertEquals(shortMsg, c.getShortMessage());
	}

	@Test
	public void testParse_ShortLineOnlyEndLF() throws Exception {
		final String shortMsg = "This is a short message.";
		final String fullMsg = shortMsg + "\n";
		final RevCommit c = create(fullMsg);
		assertEquals(fullMsg, c.getFullMessage());
		assertEquals(shortMsg, c.getShortMessage());
	}

	@Test
	public void testParse_ShortLineOnlyEmbeddedLF() throws Exception {
		final String fullMsg = "This is a\nshort message.";
		final String shortMsg = fullMsg.replace('\n', ' ');
		final RevCommit c = create(fullMsg);
		assertEquals(fullMsg, c.getFullMessage());
		assertEquals(shortMsg, c.getShortMessage());
	}

	@Test
	public void testParse_ShortLineOnlyEmbeddedAndEndingLF() throws Exception {
		final String fullMsg = "This is a\nshort message.\n";
		final String shortMsg = "This is a short message.";
		final RevCommit c = create(fullMsg);
		assertEquals(fullMsg, c.getFullMessage());
		assertEquals(shortMsg, c.getShortMessage());
	}

	@Test
	public void testParse_GitStyleMessage() throws Exception {
		final String shortMsg = "This fixes a bug.";
		final String body = "We do it with magic and pixie dust and stuff.\n"
				+ "\n" + "Signed-off-by: A U. Thor <author@example.com>\n";
		final String fullMsg = shortMsg + "\n" + "\n" + body;
		final RevCommit c = create(fullMsg);
		assertEquals(fullMsg, c.getFullMessage());
		assertEquals(shortMsg, c.getShortMessage());
	}

	@Test
	public void testParse_PublicParseMethod()
			throws UnsupportedEncodingException {
		ObjectInserter.Formatter fmt = new ObjectInserter.Formatter();
		CommitBuilder src = new CommitBuilder();
		src.setTreeId(fmt.idFor(Constants.OBJ_TREE, new byte[] {}));
		src.setAuthor(author);
		src.setCommitter(committer);
		src.setMessage("Test commit\n\nThis is a test.\n");

		RevCommit p = RevCommit.parse(src.build());
		assertEquals(src.getTreeId(), p.getTree());
		assertEquals(0, p.getParentCount());
		assertEquals(author, p.getAuthorIdent());
		assertEquals(committer, p.getCommitterIdent());
		assertEquals("Test commit", p.getShortMessage());
		assertEquals(src.getMessage(), p.getFullMessage());
	}

	private static ObjectId id(final String str) {
		return ObjectId.fromString(str);
	}
}
