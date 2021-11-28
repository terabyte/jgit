/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.revwalk.filter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.RawCharSequence;

/** Abstract filter that searches text using extended regular expressions. */
public abstract class PatternMatchRevFilter extends RevFilter {
	/**
	 * Encode a string pattern for faster matching on byte arrays.
	 * <p>
	 * Force the characters to our funny UTF-8 only convention that we use on
	 * raw buffers. This avoids needing to perform character set decodes on the
	 * individual commit buffers.
	 *
	 * @param patternText
	 *            original pattern string supplied by the user or the
	 *            application.
	 * @return same pattern, but re-encoded to match our funny raw UTF-8
	 *         character sequence {@link RawCharSequence}.
	 */
	protected static final String forceToRaw(final String patternText) {
		final byte[] b = Constants.encode(patternText);
		final StringBuilder needle = new StringBuilder(b.length);
		for (int i = 0; i < b.length; i++)
			needle.append((char) (b[i] & 0xff));
		return needle.toString();
	}

	private final String patternText;

	private final Matcher compiledPattern;

	/**
	 * Construct a new pattern matching filter.
	 *
	 * @param pattern
	 *            text of the pattern. Callers may want to surround their
	 *            pattern with ".*" on either end to allow matching in the
	 *            middle of the string.
	 * @param innerString
	 *            should .* be wrapped around the pattern of ^ and $ are
	 *            missing? Most users will want this set.
	 * @param rawEncoding
	 *            should {@link #forceToRaw(String)} be applied to the pattern
	 *            before compiling it?
	 * @param flags
	 *            flags from {@link Pattern} to control how matching performs.
	 */
	protected PatternMatchRevFilter(String pattern, final boolean innerString,
			final boolean rawEncoding, final int flags) {
		if (pattern.length() == 0)
			throw new IllegalArgumentException(JGitText.get().cannotMatchOnEmptyString);
		patternText = pattern;

		if (innerString) {
			if (!pattern.startsWith("^") && !pattern.startsWith(".*")) //$NON-NLS-1$ //$NON-NLS-2$
				pattern = ".*" + pattern; //$NON-NLS-1$
			if (!pattern.endsWith("$") && !pattern.endsWith(".*")) //$NON-NLS-1$ //$NON-NLS-2$
				pattern = pattern + ".*"; //$NON-NLS-1$
		}
		final String p = rawEncoding ? forceToRaw(pattern) : pattern;
		compiledPattern = Pattern.compile(p, flags).matcher(""); //$NON-NLS-1$
	}

	/**
	 * Get the pattern this filter uses.
	 *
	 * @return the pattern this filter is applying to candidate strings.
	 */
	public String pattern() {
		return patternText;
	}

	@Override
	public boolean include(final RevWalk walker, final RevCommit cmit)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		return compiledPattern.reset(text(cmit)).matches();
	}

	@Override
	public boolean requiresCommitBody() {
		return true;
	}

	/**
	 * Obtain the raw text to match against.
	 *
	 * @param cmit
	 *            current commit being evaluated.
	 * @return sequence for the commit's content that we need to match on.
	 */
	protected abstract CharSequence text(RevCommit cmit);

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return super.toString() + "(\"" + patternText + "\")";
	}
}
