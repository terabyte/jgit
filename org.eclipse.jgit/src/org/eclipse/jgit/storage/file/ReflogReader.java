/*
 * Copyright (C) 2009, Robin Rosenberg
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Utility for reading reflog entries
 */
public class ReflogReader {
	private File logName;

	/**
	 * @param db
	 * @param refname
	 */
	public ReflogReader(Repository db, String refname) {
		logName = new File(db.getDirectory(), Constants.LOGS + '/' + refname);
	}

	/**
	 * Get the last entry in the reflog
	 *
	 * @return the latest reflog entry, or null if no log
	 * @throws IOException
	 */
	public ReflogEntry getLastEntry() throws IOException {
		return getReverseEntry(0);
	}

	/**
	 * @return all reflog entries in reverse order
	 * @throws IOException
	 */
	public List<ReflogEntry> getReverseEntries() throws IOException {
		return getReverseEntries(Integer.MAX_VALUE);
	}

	/**
	 * Get specific entry in the reflog relative to the last entry which is
	 * considered entry zero.
	 *
	 * @param number
	 * @return reflog entry or null if not found
	 * @throws IOException
	 */
	public ReflogEntry getReverseEntry(int number) throws IOException {
		if (number < 0)
			throw new IllegalArgumentException();

		final byte[] log;
		try {
			log = IO.readFully(logName);
		} catch (FileNotFoundException e) {
			return null;
		}

		int rs = RawParseUtils.prevLF(log, log.length);
		int current = 0;
		while (rs >= 0) {
			rs = RawParseUtils.prevLF(log, rs);
			if (number == current)
				return new ReflogEntry(log, rs < 0 ? 0 : rs + 2);
			current++;
		}
		return null;
	}

	/**
	 * @param max
	 *            max number of entries to read
	 * @return all reflog entries in reverse order
	 * @throws IOException
	 */
	public List<ReflogEntry> getReverseEntries(int max) throws IOException {
		final byte[] log;
		try {
			log = IO.readFully(logName);
		} catch (FileNotFoundException e) {
			return Collections.emptyList();
		}

		int rs = RawParseUtils.prevLF(log, log.length);
		List<ReflogEntry> ret = new ArrayList<ReflogEntry>();
		while (rs >= 0 && max-- > 0) {
			rs = RawParseUtils.prevLF(log, rs);
			ReflogEntry entry = new ReflogEntry(log, rs < 0 ? 0 : rs + 2);
			ret.add(entry);
		}
		return ret;
	}
}
