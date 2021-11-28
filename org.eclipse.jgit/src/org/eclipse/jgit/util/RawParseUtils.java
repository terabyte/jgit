/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.util;

import static org.eclipse.jgit.lib.ObjectChecker.author;
import static org.eclipse.jgit.lib.ObjectChecker.committer;
import static org.eclipse.jgit.lib.ObjectChecker.encoding;
import static org.eclipse.jgit.lib.ObjectChecker.tagger;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;

/** Handy utility functions to parse raw object contents. */
public final class RawParseUtils {
	/**
	 * UTF-8 charset constant.
	 *
	 * @since 2.2
	 */
	public static final Charset UTF8_CHARSET = Charset.forName("UTF-8"); //$NON-NLS-1$

	private static final byte[] digits10;

	private static final byte[] digits16;

	private static final byte[] footerLineKeyChars;

	private static final Map<String, Charset> encodingAliases;

	static {
		encodingAliases = new HashMap<String, Charset>();
		encodingAliases.put("latin-1", Charset.forName("ISO-8859-1")); //$NON-NLS-1$ //$NON-NLS-2$

		digits10 = new byte['9' + 1];
		Arrays.fill(digits10, (byte) -1);
		for (char i = '0'; i <= '9'; i++)
			digits10[i] = (byte) (i - '0');

		digits16 = new byte['f' + 1];
		Arrays.fill(digits16, (byte) -1);
		for (char i = '0'; i <= '9'; i++)
			digits16[i] = (byte) (i - '0');
		for (char i = 'a'; i <= 'f'; i++)
			digits16[i] = (byte) ((i - 'a') + 10);
		for (char i = 'A'; i <= 'F'; i++)
			digits16[i] = (byte) ((i - 'A') + 10);

		footerLineKeyChars = new byte['z' + 1];
		footerLineKeyChars['-'] = 1;
		for (char i = '0'; i <= '9'; i++)
			footerLineKeyChars[i] = 1;
		for (char i = 'A'; i <= 'Z'; i++)
			footerLineKeyChars[i] = 1;
		for (char i = 'a'; i <= 'z'; i++)
			footerLineKeyChars[i] = 1;
	}

	/**
	 * Determine if b[ptr] matches src.
	 *
	 * @param b
	 *            the buffer to scan.
	 * @param ptr
	 *            first position within b, this should match src[0].
	 * @param src
	 *            the buffer to test for equality with b.
	 * @return ptr + src.length if b[ptr..src.length] == src; else -1.
	 */
	public static final int match(final byte[] b, int ptr, final byte[] src) {
		if (ptr + src.length > b.length)
			return -1;
		for (int i = 0; i < src.length; i++, ptr++)
			if (b[ptr] != src[i])
				return -1;
		return ptr;
	}

	private static final byte[] base10byte = { '0', '1', '2', '3', '4', '5',
			'6', '7', '8', '9' };

	/**
	 * Format a base 10 numeric into a temporary buffer.
	 * <p>
	 * Formatting is performed backwards. The method starts at offset
	 * <code>o-1</code> and ends at <code>o-1-digits</code>, where
	 * <code>digits</code> is the number of positions necessary to store the
	 * base 10 value.
	 * <p>
	 * The argument and return values from this method make it easy to chain
	 * writing, for example:
	 * </p>
	 *
	 * <pre>
	 * final byte[] tmp = new byte[64];
	 * int ptr = tmp.length;
	 * tmp[--ptr] = '\n';
	 * ptr = RawParseUtils.formatBase10(tmp, ptr, 32);
	 * tmp[--ptr] = ' ';
	 * ptr = RawParseUtils.formatBase10(tmp, ptr, 18);
	 * tmp[--ptr] = 0;
	 * final String str = new String(tmp, ptr, tmp.length - ptr);
	 * </pre>
	 *
	 * @param b
	 *            buffer to write into.
	 * @param o
	 *            one offset past the location where writing will begin; writing
	 *            proceeds towards lower index values.
	 * @param value
	 *            the value to store.
	 * @return the new offset value <code>o</code>. This is the position of
	 *         the last byte written. Additional writing should start at one
	 *         position earlier.
	 */
	public static int formatBase10(final byte[] b, int o, int value) {
		if (value == 0) {
			b[--o] = '0';
			return o;
		}
		final boolean isneg = value < 0;
		if (isneg)
			value = -value;
		while (value != 0) {
			b[--o] = base10byte[value % 10];
			value /= 10;
		}
		if (isneg)
			b[--o] = '-';
		return o;
	}

	/**
	 * Parse a base 10 numeric from a sequence of ASCII digits into an int.
	 * <p>
	 * Digit sequences can begin with an optional run of spaces before the
	 * sequence, and may start with a '+' or a '-' to indicate sign position.
	 * Any other characters will cause the method to stop and return the current
	 * result to the caller.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position within buffer to start parsing digits at.
	 * @param ptrResult
	 *            optional location to return the new ptr value through. If null
	 *            the ptr value will be discarded.
	 * @return the value at this location; 0 if the location is not a valid
	 *         numeric.
	 */
	public static final int parseBase10(final byte[] b, int ptr,
			final MutableInteger ptrResult) {
		int r = 0;
		int sign = 0;
		try {
			final int sz = b.length;
			while (ptr < sz && b[ptr] == ' ')
				ptr++;
			if (ptr >= sz)
				return 0;

			switch (b[ptr]) {
			case '-':
				sign = -1;
				ptr++;
				break;
			case '+':
				ptr++;
				break;
			}

			while (ptr < sz) {
				final byte v = digits10[b[ptr]];
				if (v < 0)
					break;
				r = (r * 10) + v;
				ptr++;
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			// Not a valid digit.
		}
		if (ptrResult != null)
			ptrResult.value = ptr;
		return sign < 0 ? -r : r;
	}

	/**
	 * Parse a base 10 numeric from a sequence of ASCII digits into a long.
	 * <p>
	 * Digit sequences can begin with an optional run of spaces before the
	 * sequence, and may start with a '+' or a '-' to indicate sign position.
	 * Any other characters will cause the method to stop and return the current
	 * result to the caller.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position within buffer to start parsing digits at.
	 * @param ptrResult
	 *            optional location to return the new ptr value through. If null
	 *            the ptr value will be discarded.
	 * @return the value at this location; 0 if the location is not a valid
	 *         numeric.
	 */
	public static final long parseLongBase10(final byte[] b, int ptr,
			final MutableInteger ptrResult) {
		long r = 0;
		int sign = 0;
		try {
			final int sz = b.length;
			while (ptr < sz && b[ptr] == ' ')
				ptr++;
			if (ptr >= sz)
				return 0;

			switch (b[ptr]) {
			case '-':
				sign = -1;
				ptr++;
				break;
			case '+':
				ptr++;
				break;
			}

			while (ptr < sz) {
				final byte v = digits10[b[ptr]];
				if (v < 0)
					break;
				r = (r * 10) + v;
				ptr++;
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			// Not a valid digit.
		}
		if (ptrResult != null)
			ptrResult.value = ptr;
		return sign < 0 ? -r : r;
	}

	/**
	 * Parse 4 character base 16 (hex) formatted string to unsigned integer.
	 * <p>
	 * The number is read in network byte order, that is, most significant
	 * nybble first.
	 *
	 * @param bs
	 *            buffer to parse digits from; positions {@code [p, p+4)} will
	 *            be parsed.
	 * @param p
	 *            first position within the buffer to parse.
	 * @return the integer value.
	 * @throws ArrayIndexOutOfBoundsException
	 *             if the string is not hex formatted.
	 */
	public static final int parseHexInt16(final byte[] bs, final int p) {
		int r = digits16[bs[p]] << 4;

		r |= digits16[bs[p + 1]];
		r <<= 4;

		r |= digits16[bs[p + 2]];
		r <<= 4;

		r |= digits16[bs[p + 3]];
		if (r < 0)
			throw new ArrayIndexOutOfBoundsException();
		return r;
	}

	/**
	 * Parse 8 character base 16 (hex) formatted string to unsigned integer.
	 * <p>
	 * The number is read in network byte order, that is, most significant
	 * nybble first.
	 *
	 * @param bs
	 *            buffer to parse digits from; positions {@code [p, p+8)} will
	 *            be parsed.
	 * @param p
	 *            first position within the buffer to parse.
	 * @return the integer value.
	 * @throws ArrayIndexOutOfBoundsException
	 *             if the string is not hex formatted.
	 */
	public static final int parseHexInt32(final byte[] bs, final int p) {
		int r = digits16[bs[p]] << 4;

		r |= digits16[bs[p + 1]];
		r <<= 4;

		r |= digits16[bs[p + 2]];
		r <<= 4;

		r |= digits16[bs[p + 3]];
		r <<= 4;

		r |= digits16[bs[p + 4]];
		r <<= 4;

		r |= digits16[bs[p + 5]];
		r <<= 4;

		r |= digits16[bs[p + 6]];

		final int last = digits16[bs[p + 7]];
		if (r < 0 || last < 0)
			throw new ArrayIndexOutOfBoundsException();
		return (r << 4) | last;
	}

	/**
	 * Parse a single hex digit to its numeric value (0-15).
	 *
	 * @param digit
	 *            hex character to parse.
	 * @return numeric value, in the range 0-15.
	 * @throws ArrayIndexOutOfBoundsException
	 *             if the input digit is not a valid hex digit.
	 */
	public static final int parseHexInt4(final byte digit) {
		final byte r = digits16[digit];
		if (r < 0)
			throw new ArrayIndexOutOfBoundsException();
		return r;
	}

	/**
	 * Parse a Git style timezone string.
	 * <p>
	 * The sequence "-0315" will be parsed as the numeric value -195, as the
	 * lower two positions count minutes, not 100ths of an hour.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position within buffer to start parsing digits at.
	 * @return the timezone at this location, expressed in minutes.
	 */
	public static final int parseTimeZoneOffset(final byte[] b, int ptr) {
		final int v = parseBase10(b, ptr, null);
		final int tzMins = v % 100;
		final int tzHours = v / 100;
		return tzHours * 60 + tzMins;
	}

	/**
	 * Locate the first position after a given character.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position within buffer to start looking for chrA at.
	 * @param chrA
	 *            character to find.
	 * @return new position just after chrA.
	 */
	public static final int next(final byte[] b, int ptr, final char chrA) {
		final int sz = b.length;
		while (ptr < sz) {
			if (b[ptr++] == chrA)
				return ptr;
		}
		return ptr;
	}

	/**
	 * Locate the first position after the next LF.
	 * <p>
	 * This method stops on the first '\n' it finds.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position within buffer to start looking for LF at.
	 * @return new position just after the first LF found.
	 */
	public static final int nextLF(final byte[] b, int ptr) {
		return next(b, ptr, '\n');
	}

	/**
	 * Locate the first position after either the given character or LF.
	 * <p>
	 * This method stops on the first match it finds from either chrA or '\n'.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position within buffer to start looking for chrA or LF at.
	 * @param chrA
	 *            character to find.
	 * @return new position just after the first chrA or LF to be found.
	 */
	public static final int nextLF(final byte[] b, int ptr, final char chrA) {
		final int sz = b.length;
		while (ptr < sz) {
			final byte c = b[ptr++];
			if (c == chrA || c == '\n')
				return ptr;
		}
		return ptr;
	}

	/**
	 * Locate the first position before a given character.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position within buffer to start looking for chrA at.
	 * @param chrA
	 *            character to find.
	 * @return new position just before chrA, -1 for not found
	 */
	public static final int prev(final byte[] b, int ptr, final char chrA) {
		if (ptr == b.length)
			--ptr;
		while (ptr >= 0) {
			if (b[ptr--] == chrA)
				return ptr;
		}
		return ptr;
	}

	/**
	 * Locate the first position before the previous LF.
	 * <p>
	 * This method stops on the first '\n' it finds.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position within buffer to start looking for LF at.
	 * @return new position just before the first LF found, -1 for not found
	 */
	public static final int prevLF(final byte[] b, int ptr) {
		return prev(b, ptr, '\n');
	}

	/**
	 * Locate the previous position before either the given character or LF.
	 * <p>
	 * This method stops on the first match it finds from either chrA or '\n'.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position within buffer to start looking for chrA or LF at.
	 * @param chrA
	 *            character to find.
	 * @return new position just before the first chrA or LF to be found, -1 for
	 *         not found
	 */
	public static final int prevLF(final byte[] b, int ptr, final char chrA) {
		if (ptr == b.length)
			--ptr;
		while (ptr >= 0) {
			final byte c = b[ptr--];
			if (c == chrA || c == '\n')
				return ptr;
		}
		return ptr;
	}

	/**
	 * Index the region between <code>[ptr, end)</code> to find line starts.
	 * <p>
	 * The returned list is 1 indexed. Index 0 contains
	 * {@link Integer#MIN_VALUE} to pad the list out.
	 * <p>
	 * Using a 1 indexed list means that line numbers can be directly accessed
	 * from the list, so <code>list.get(1)</code> (aka get line 1) returns
	 * <code>ptr</code>.
	 * <p>
	 * The last element (index <code>map.size()-1</code>) always contains
	 * <code>end</code>.
	 *
	 * @param buf
	 *            buffer to scan.
	 * @param ptr
	 *            position within the buffer corresponding to the first byte of
	 *            line 1.
	 * @param end
	 *            1 past the end of the content within <code>buf</code>.
	 * @return a line map indexing the start position of each line.
	 */
	public static final IntList lineMap(final byte[] buf, int ptr, int end) {
		// Experimentally derived from multiple source repositories
		// the average number of bytes/line is 36. Its a rough guess
		// to initially size our map close to the target.
		//
		final IntList map = new IntList((end - ptr) / 36);
		map.fillTo(1, Integer.MIN_VALUE);
		for (; ptr < end; ptr = nextLF(buf, ptr))
			map.add(ptr);
		map.add(end);
		return map;
	}

	/**
	 * Locate the "author " header line data.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position in buffer to start the scan at. Most callers should
	 *            pass 0 to ensure the scan starts from the beginning of the
	 *            commit buffer and does not accidentally look at message body.
	 * @return position just after the space in "author ", so the first
	 *         character of the author's name. If no author header can be
	 *         located -1 is returned.
	 */
	public static final int author(final byte[] b, int ptr) {
		final int sz = b.length;
		if (ptr == 0)
			ptr += 46; // skip the "tree ..." line.
		while (ptr < sz && b[ptr] == 'p')
			ptr += 48; // skip this parent.
		return match(b, ptr, author);
	}

	/**
	 * Locate the "committer " header line data.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position in buffer to start the scan at. Most callers should
	 *            pass 0 to ensure the scan starts from the beginning of the
	 *            commit buffer and does not accidentally look at message body.
	 * @return position just after the space in "committer ", so the first
	 *         character of the committer's name. If no committer header can be
	 *         located -1 is returned.
	 */
	public static final int committer(final byte[] b, int ptr) {
		final int sz = b.length;
		if (ptr == 0)
			ptr += 46; // skip the "tree ..." line.
		while (ptr < sz && b[ptr] == 'p')
			ptr += 48; // skip this parent.
		if (ptr < sz && b[ptr] == 'a')
			ptr = nextLF(b, ptr);
		return match(b, ptr, committer);
	}

	/**
	 * Locate the "tagger " header line data.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position in buffer to start the scan at. Most callers should
	 *            pass 0 to ensure the scan starts from the beginning of the tag
	 *            buffer and does not accidentally look at message body.
	 * @return position just after the space in "tagger ", so the first
	 *         character of the tagger's name. If no tagger header can be
	 *         located -1 is returned.
	 */
	public static final int tagger(final byte[] b, int ptr) {
		final int sz = b.length;
		if (ptr == 0)
			ptr += 48; // skip the "object ..." line.
		while (ptr < sz) {
			if (b[ptr] == '\n')
				return -1;
			final int m = match(b, ptr, tagger);
			if (m >= 0)
				return m;
			ptr = nextLF(b, ptr);
		}
		return -1;
	}

	/**
	 * Locate the "encoding " header line.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position in buffer to start the scan at. Most callers should
	 *            pass 0 to ensure the scan starts from the beginning of the
	 *            buffer and does not accidentally look at the message body.
	 * @return position just after the space in "encoding ", so the first
	 *         character of the encoding's name. If no encoding header can be
	 *         located -1 is returned (and UTF-8 should be assumed).
	 */
	public static final int encoding(final byte[] b, int ptr) {
		final int sz = b.length;
		while (ptr < sz) {
			if (b[ptr] == '\n')
				return -1;
			if (b[ptr] == 'e')
				break;
			ptr = nextLF(b, ptr);
		}
		return match(b, ptr, encoding);
	}

	/**
	 * Parse the "encoding " header into a character set reference.
	 * <p>
	 * Locates the "encoding " header (if present) by first calling
	 * {@link #encoding(byte[], int)} and then returns the proper character set
	 * to apply to this buffer to evaluate its contents as character data.
	 * <p>
	 * If no encoding header is present, {@link Constants#CHARSET} is assumed.
	 *
	 * @param b
	 *            buffer to scan.
	 * @return the Java character set representation. Never null.
	 */
	public static Charset parseEncoding(final byte[] b) {
		final int enc = encoding(b, 0);
		if (enc < 0)
			return Constants.CHARSET;
		final int lf = nextLF(b, enc);
		String decoded = decode(Constants.CHARSET, b, enc, lf - 1);
		try {
			return Charset.forName(decoded);
		} catch (IllegalCharsetNameException badName) {
			Charset aliased = charsetForAlias(decoded);
			if (aliased != null)
				return aliased;
			throw badName;
		} catch (UnsupportedCharsetException badName) {
			Charset aliased = charsetForAlias(decoded);
			if (aliased != null)
				return aliased;
			throw badName;
		}
	}

	/**
	 * Parse a name string (e.g. author, committer, tagger) into a PersonIdent.
	 * <p>
	 * Leading spaces won't be trimmed from the string, i.e. will show up in the
	 * parsed name afterwards.
	 *
	 * @param in
	 *            the string to parse a name from.
	 * @return the parsed identity or null in case the identity could not be
	 *         parsed.
	 */
	public static PersonIdent parsePersonIdent(final String in) {
		return parsePersonIdent(Constants.encode(in), 0);
	}

	/**
	 * Parse a name line (e.g. author, committer, tagger) into a PersonIdent.
	 * <p>
	 * When passing in a value for <code>nameB</code> callers should use the
	 * return value of {@link #author(byte[], int)} or
	 * {@link #committer(byte[], int)}, as these methods provide the proper
	 * position within the buffer.
	 *
	 * @param raw
	 *            the buffer to parse character data from.
	 * @param nameB
	 *            first position of the identity information. This should be the
	 *            first position after the space which delimits the header field
	 *            name (e.g. "author" or "committer") from the rest of the
	 *            identity line.
	 * @return the parsed identity or null in case the identity could not be
	 *         parsed.
	 */
	public static PersonIdent parsePersonIdent(final byte[] raw, final int nameB) {
		final Charset cs = parseEncoding(raw);
		final int emailB = nextLF(raw, nameB, '<');
		final int emailE = nextLF(raw, emailB, '>');
		if (emailB >= raw.length || raw[emailB] == '\n' ||
				(emailE >= raw.length - 1 && raw[emailE - 1] != '>'))
			return null;

		final int nameEnd = emailB - 2 >= nameB && raw[emailB - 2] == ' ' ?
				emailB - 2 : emailB - 1;
		final String name = decode(cs, raw, nameB, nameEnd);
		final String email = decode(cs, raw, emailB, emailE - 1);

		// Start searching from end of line, as after first name-email pair,
		// another name-email pair may occur. We will ignore all kinds of
		// "junk" following the first email.
		//
		// We've to use (emailE - 1) for the case that raw[email] is LF,
		// otherwise we would run too far. "-2" is necessary to position
		// before the LF in case of LF termination resp. the penultimate
		// character if there is no trailing LF.
		final int tzBegin = lastIndexOfTrim(raw, ' ',
				nextLF(raw, emailE - 1) - 2) + 1;
		if (tzBegin <= emailE) // No time/zone, still valid
			return new PersonIdent(name, email, 0, 0);

		final int whenBegin = Math.max(emailE,
				lastIndexOfTrim(raw, ' ', tzBegin - 1) + 1);
		if (whenBegin >= tzBegin - 1) // No time/zone, still valid
			return new PersonIdent(name, email, 0, 0);

		final long when = parseLongBase10(raw, whenBegin, null);
		final int tz = parseTimeZoneOffset(raw, tzBegin);
		return new PersonIdent(name, email, when * 1000L, tz);
	}

	/**
	 * Parse a name data (e.g. as within a reflog) into a PersonIdent.
	 * <p>
	 * When passing in a value for <code>nameB</code> callers should use the
	 * return value of {@link #author(byte[], int)} or
	 * {@link #committer(byte[], int)}, as these methods provide the proper
	 * position within the buffer.
	 *
	 * @param raw
	 *            the buffer to parse character data from.
	 * @param nameB
	 *            first position of the identity information. This should be the
	 *            first position after the space which delimits the header field
	 *            name (e.g. "author" or "committer") from the rest of the
	 *            identity line.
	 * @return the parsed identity. Never null.
	 */
	public static PersonIdent parsePersonIdentOnly(final byte[] raw,
			final int nameB) {
		int stop = nextLF(raw, nameB);
		int emailB = nextLF(raw, nameB, '<');
		int emailE = nextLF(raw, emailB, '>');
		final String name;
		final String email;
		if (emailE < stop) {
			email = decode(raw, emailB, emailE - 1);
		} else {
			email = "invalid"; //$NON-NLS-1$
		}
		if (emailB < stop)
			name = decode(raw, nameB, emailB - 2);
		else
			name = decode(raw, nameB, stop);

		final MutableInteger ptrout = new MutableInteger();
		long when;
		int tz;
		if (emailE < stop) {
			when = parseLongBase10(raw, emailE + 1, ptrout);
			tz = parseTimeZoneOffset(raw, ptrout.value);
		} else {
			when = 0;
			tz = 0;
		}
		return new PersonIdent(name, email, when * 1000L, tz);
	}

	/**
	 * Locate the end of a footer line key string.
	 * <p>
	 * If the region at {@code raw[ptr]} matches {@code ^[A-Za-z0-9-]+:} (e.g.
	 * "Signed-off-by: A. U. Thor\n") then this method returns the position of
	 * the first ':'.
	 * <p>
	 * If the region at {@code raw[ptr]} does not match {@code ^[A-Za-z0-9-]+:}
	 * then this method returns -1.
	 *
	 * @param raw
	 *            buffer to scan.
	 * @param ptr
	 *            first position within raw to consider as a footer line key.
	 * @return position of the ':' which terminates the footer line key if this
	 *         is otherwise a valid footer line key; otherwise -1.
	 */
	public static int endOfFooterLineKey(final byte[] raw, int ptr) {
		try {
			for (;;) {
				final byte c = raw[ptr];
				if (footerLineKeyChars[c] == 0) {
					if (c == ':')
						return ptr;
					return -1;
				}
				ptr++;
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			return -1;
		}
	}

	/**
	 * Decode a buffer under UTF-8, if possible.
	 *
	 * If the byte stream cannot be decoded that way, the platform default is tried
	 * and if that too fails, the fail-safe ISO-8859-1 encoding is tried.
	 *
	 * @param buffer
	 *            buffer to pull raw bytes from.
	 * @return a string representation of the range <code>[start,end)</code>,
	 *         after decoding the region through the specified character set.
	 */
	public static String decode(final byte[] buffer) {
		return decode(buffer, 0, buffer.length);
	}

	/**
	 * Decode a buffer under UTF-8, if possible.
	 *
	 * If the byte stream cannot be decoded that way, the platform default is
	 * tried and if that too fails, the fail-safe ISO-8859-1 encoding is tried.
	 *
	 * @param buffer
	 *            buffer to pull raw bytes from.
	 * @param start
	 *            start position in buffer
	 * @param end
	 *            one position past the last location within the buffer to take
	 *            data from.
	 * @return a string representation of the range <code>[start,end)</code>,
	 *         after decoding the region through the specified character set.
	 */
	public static String decode(final byte[] buffer, final int start,
			final int end) {
		return decode(Constants.CHARSET, buffer, start, end);
	}

	/**
	 * Decode a buffer under the specified character set if possible.
	 *
	 * If the byte stream cannot be decoded that way, the platform default is tried
	 * and if that too fails, the fail-safe ISO-8859-1 encoding is tried.
	 *
	 * @param cs
	 *            character set to use when decoding the buffer.
	 * @param buffer
	 *            buffer to pull raw bytes from.
	 * @return a string representation of the range <code>[start,end)</code>,
	 *         after decoding the region through the specified character set.
	 */
	public static String decode(final Charset cs, final byte[] buffer) {
		return decode(cs, buffer, 0, buffer.length);
	}

	/**
	 * Decode a region of the buffer under the specified character set if possible.
	 *
	 * If the byte stream cannot be decoded that way, the platform default is tried
	 * and if that too fails, the fail-safe ISO-8859-1 encoding is tried.
	 *
	 * @param cs
	 *            character set to use when decoding the buffer.
	 * @param buffer
	 *            buffer to pull raw bytes from.
	 * @param start
	 *            first position within the buffer to take data from.
	 * @param end
	 *            one position past the last location within the buffer to take
	 *            data from.
	 * @return a string representation of the range <code>[start,end)</code>,
	 *         after decoding the region through the specified character set.
	 */
	public static String decode(final Charset cs, final byte[] buffer,
			final int start, final int end) {
		try {
			return decodeNoFallback(cs, buffer, start, end);
		} catch (CharacterCodingException e) {
			// Fall back to an ISO-8859-1 style encoding. At least all of
			// the bytes will be present in the output.
			//
			return extractBinaryString(buffer, start, end);
		}
	}

	/**
	 * Decode a region of the buffer under the specified character set if
	 * possible.
	 *
	 * If the byte stream cannot be decoded that way, the platform default is
	 * tried and if that too fails, an exception is thrown.
	 *
	 * @param cs
	 *            character set to use when decoding the buffer.
	 * @param buffer
	 *            buffer to pull raw bytes from.
	 * @param start
	 *            first position within the buffer to take data from.
	 * @param end
	 *            one position past the last location within the buffer to take
	 *            data from.
	 * @return a string representation of the range <code>[start,end)</code>,
	 *         after decoding the region through the specified character set.
	 * @throws CharacterCodingException
	 *             the input is not in any of the tested character sets.
	 */
	public static String decodeNoFallback(final Charset cs,
			final byte[] buffer, final int start, final int end)
			throws CharacterCodingException {
		final ByteBuffer b = ByteBuffer.wrap(buffer, start, end - start);
		b.mark();

		// Try our built-in favorite. The assumption here is that
		// decoding will fail if the data is not actually encoded
		// using that encoder.
		//
		try {
			return decode(b, Constants.CHARSET);
		} catch (CharacterCodingException e) {
			b.reset();
		}

		if (!cs.equals(Constants.CHARSET)) {
			// Try the suggested encoding, it might be right since it was
			// provided by the caller.
			//
			try {
				return decode(b, cs);
			} catch (CharacterCodingException e) {
				b.reset();
			}
		}

		// Try the default character set. A small group of people
		// might actually use the same (or very similar) locale.
		//
		final Charset defcs = Charset.defaultCharset();
		if (!defcs.equals(cs) && !defcs.equals(Constants.CHARSET)) {
			try {
				return decode(b, defcs);
			} catch (CharacterCodingException e) {
				b.reset();
			}
		}

		throw new CharacterCodingException();
	}

	/**
	 * Decode a region of the buffer under the ISO-8859-1 encoding.
	 *
	 * Each byte is treated as a single character in the 8859-1 character
	 * encoding, performing a raw binary->char conversion.
	 *
	 * @param buffer
	 *            buffer to pull raw bytes from.
	 * @param start
	 *            first position within the buffer to take data from.
	 * @param end
	 *            one position past the last location within the buffer to take
	 *            data from.
	 * @return a string representation of the range <code>[start,end)</code>.
	 */
	public static String extractBinaryString(final byte[] buffer,
			final int start, final int end) {
		final StringBuilder r = new StringBuilder(end - start);
		for (int i = start; i < end; i++)
			r.append((char) (buffer[i] & 0xff));
		return r.toString();
	}

	private static String decode(final ByteBuffer b, final Charset charset)
			throws CharacterCodingException {
		final CharsetDecoder d = charset.newDecoder();
		d.onMalformedInput(CodingErrorAction.REPORT);
		d.onUnmappableCharacter(CodingErrorAction.REPORT);
		return d.decode(b).toString();
	}

	/**
	 * Locate the position of the commit message body.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position in buffer to start the scan at. Most callers should
	 *            pass 0 to ensure the scan starts from the beginning of the
	 *            commit buffer.
	 * @return position of the user's message buffer.
	 */
	public static final int commitMessage(final byte[] b, int ptr) {
		final int sz = b.length;
		if (ptr == 0)
			ptr += 46; // skip the "tree ..." line.
		while (ptr < sz && b[ptr] == 'p')
			ptr += 48; // skip this parent.

		// Skip any remaining header lines, ignoring what their actual
		// header line type is. This is identical to the logic for a tag.
		//
		return tagMessage(b, ptr);
	}

	/**
	 * Locate the position of the tag message body.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param ptr
	 *            position in buffer to start the scan at. Most callers should
	 *            pass 0 to ensure the scan starts from the beginning of the tag
	 *            buffer.
	 * @return position of the user's message buffer.
	 */
	public static final int tagMessage(final byte[] b, int ptr) {
		final int sz = b.length;
		if (ptr == 0)
			ptr += 48; // skip the "object ..." line.
		while (ptr < sz && b[ptr] != '\n')
			ptr = nextLF(b, ptr);
		if (ptr < sz && b[ptr] == '\n')
			return ptr + 1;
		return -1;
	}

	/**
	 * Locate the end of a paragraph.
	 * <p>
	 * A paragraph is ended by two consecutive LF bytes.
	 *
	 * @param b
	 *            buffer to scan.
	 * @param start
	 *            position in buffer to start the scan at. Most callers will
	 *            want to pass the first position of the commit message (as
	 *            found by {@link #commitMessage(byte[], int)}.
	 * @return position of the LF at the end of the paragraph;
	 *         <code>b.length</code> if no paragraph end could be located.
	 */
	public static final int endOfParagraph(final byte[] b, final int start) {
		int ptr = start;
		final int sz = b.length;
		while (ptr < sz && b[ptr] != '\n')
			ptr = nextLF(b, ptr);
		while (0 < ptr && start < ptr && b[ptr - 1] == '\n')
			ptr--;
		return ptr;
	}

	private static int lastIndexOfTrim(byte[] raw, char ch, int pos) {
		while (pos >= 0 && raw[pos] == ' ')
			pos--;

		while (pos >= 0 && raw[pos] != ch)
			pos--;

		return pos;
	}

	private static Charset charsetForAlias(String name) {
		return encodingAliases.get(StringUtils.toLowerCase(name));
	}

	private RawParseUtils() {
		// Don't create instances of a static only utility.
	}
}
