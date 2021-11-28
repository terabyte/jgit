/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008-2009, Johannes E. Schindelin <johannes.schindelin@gmx.de>
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

package org.eclipse.jgit.diff;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.IntList;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A Sequence supporting UNIX formatted text in byte[] format.
 * <p>
 * Elements of the sequence are the lines of the file, as delimited by the UNIX
 * newline character ('\n'). The file content is treated as 8 bit binary text,
 * with no assumptions or requirements on character encoding.
 * <p>
 * Note that the first line of the file is element 0, as defined by the Sequence
 * interface API. Traditionally in a text editor a patch file the first line is
 * line number 1. Callers may need to subtract 1 prior to invoking methods if
 * they are converting from "line number" to "element index".
 */
public class RawText extends Sequence {
	/** A Rawtext of length 0 */
	public static final RawText EMPTY_TEXT = new RawText(new byte[0]);

	/** Number of bytes to check for heuristics in {@link #isBinary(byte[])} */
	private static final int FIRST_FEW_BYTES = 8000;

	/** The file content for this sequence. */
	protected final byte[] content;

	/** Map of line number to starting position within {@link #content}. */
	protected final IntList lines;

	/**
	 * Create a new sequence from an existing content byte array.
	 * <p>
	 * The entire array (indexes 0 through length-1) is used as the content.
	 *
	 * @param input
	 *            the content array. The array is never modified, so passing
	 *            through cached arrays is safe.
	 */
	public RawText(final byte[] input) {
		content = input;
		lines = RawParseUtils.lineMap(content, 0, content.length);
	}

	/**
	 * Create a new sequence from a file.
	 * <p>
	 * The entire file contents are used.
	 *
	 * @param file
	 *            the text file.
	 * @throws IOException
	 *             if Exceptions occur while reading the file
	 */
	public RawText(File file) throws IOException {
		this(IO.readFully(file));
	}

	/** @return total number of items in the sequence. */
	public int size() {
		// The line map is always 2 entries larger than the number of lines in
		// the file. Index 0 is padded out/unused. The last index is the total
		// length of the buffer, and acts as a sentinel.
		//
		return lines.size() - 2;
	}

	/**
	 * Write a specific line to the output stream, without its trailing LF.
	 * <p>
	 * The specified line is copied as-is, with no character encoding
	 * translation performed.
	 * <p>
	 * If the specified line ends with an LF ('\n'), the LF is <b>not</b>
	 * copied. It is up to the caller to write the LF, if desired, between
	 * output lines.
	 *
	 * @param out
	 *            stream to copy the line data onto.
	 * @param i
	 *            index of the line to extract. Note this is 0-based, so line
	 *            number 1 is actually index 0.
	 * @throws IOException
	 *             the stream write operation failed.
	 */
	public void writeLine(final OutputStream out, final int i)
			throws IOException {
		int start = getStart(i);
		int end = getEnd(i);
		if (content[end - 1] == '\n')
			end--;
		out.write(content, start, end - start);
	}

	/**
	 * Determine if the file ends with a LF ('\n').
	 *
	 * @return true if the last line has an LF; false otherwise.
	 */
	public boolean isMissingNewlineAtEnd() {
		final int end = lines.get(lines.size() - 1);
		if (end == 0)
			return true;
		return content[end - 1] != '\n';
	}

	/**
	 * Get the text for a single line.
	 *
	 * @param i
	 *            index of the line to extract. Note this is 0-based, so line
	 *            number 1 is actually index 0.
	 * @return the text for the line, without a trailing LF.
	 */
	public String getString(int i) {
		return getString(i, i + 1, true);
	}

	/**
	 * Get the text for a region of lines.
	 *
	 * @param begin
	 *            index of the first line to extract. Note this is 0-based, so
	 *            line number 1 is actually index 0.
	 * @param end
	 *            index of one past the last line to extract.
	 * @param dropLF
	 *            if true the trailing LF ('\n') of the last returned line is
	 *            dropped, if present.
	 * @return the text for lines {@code [begin, end)}.
	 */
	public String getString(int begin, int end, boolean dropLF) {
		if (begin == end)
			return ""; //$NON-NLS-1$

		int s = getStart(begin);
		int e = getEnd(end - 1);
		if (dropLF && content[e - 1] == '\n')
			e--;
		return decode(s, e);
	}

	/**
	 * Decode a region of the text into a String.
	 *
	 * The default implementation of this method tries to guess the character
	 * set by considering UTF-8, the platform default, and falling back on
	 * ISO-8859-1 if neither of those can correctly decode the region given.
	 *
	 * @param start
	 *            first byte of the content to decode.
	 * @param end
	 *            one past the last byte of the content to decode.
	 * @return the region {@code [start, end)} decoded as a String.
	 */
	protected String decode(int start, int end) {
		return RawParseUtils.decode(content, start, end);
	}

	private int getStart(final int i) {
		return lines.get(i + 1);
	}

	private int getEnd(final int i) {
		return lines.get(i + 2);
	}

	/**
	 * Determine heuristically whether a byte array represents binary (as
	 * opposed to text) content.
	 *
	 * @param raw
	 *            the raw file content.
	 * @return true if raw is likely to be a binary file, false otherwise
	 */
	public static boolean isBinary(byte[] raw) {
		return isBinary(raw, raw.length);
	}

	/**
	 * Determine heuristically whether the bytes contained in a stream
	 * represents binary (as opposed to text) content.
	 *
	 * Note: Do not further use this stream after having called this method! The
	 * stream may not be fully read and will be left at an unknown position
	 * after consuming an unknown number of bytes. The caller is responsible for
	 * closing the stream.
	 *
	 * @param raw
	 *            input stream containing the raw file content.
	 * @return true if raw is likely to be a binary file, false otherwise
	 * @throws IOException
	 *             if input stream could not be read
	 */
	public static boolean isBinary(InputStream raw) throws IOException {
		final byte[] buffer = new byte[FIRST_FEW_BYTES];
		int cnt = 0;
		while (cnt < buffer.length) {
			final int n = raw.read(buffer, cnt, buffer.length - cnt);
			if (n == -1)
				break;
			cnt += n;
		}
		return isBinary(buffer, cnt);
	}

	/**
	 * Determine heuristically whether a byte array represents binary (as
	 * opposed to text) content.
	 *
	 * @param raw
	 *            the raw file content.
	 * @param length
	 *            number of bytes in {@code raw} to evaluate. This should be
	 *            {@code raw.length} unless {@code raw} was over-allocated by
	 *            the caller.
	 * @return true if raw is likely to be a binary file, false otherwise
	 */
	public static boolean isBinary(byte[] raw, int length) {
		// Same heuristic as C Git
		if (length > FIRST_FEW_BYTES)
			length = FIRST_FEW_BYTES;
		for (int ptr = 0; ptr < length; ptr++)
			if (raw[ptr] == '\0')
				return true;

		return false;
	}

	/**
	 * Get the line delimiter for the first line.
	 *
	 * @since 2.0
	 * @return the line delimiter or <code>null</code>
	 */
	public String getLineDelimiter() {
		if (size() == 0)
			return null;
		int e = getEnd(0);
		if (content[e - 1] != '\n')
			return null;
		if (content.length > 1 && content[e - 2] == '\r')
			return "\r\n"; //$NON-NLS-1$
		else
			return "\n"; //$NON-NLS-1$
	}
}
