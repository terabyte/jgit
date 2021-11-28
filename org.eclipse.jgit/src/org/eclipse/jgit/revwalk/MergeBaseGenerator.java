/*
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

package org.eclipse.jgit.revwalk;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;

/**
 * Computes the merge base(s) of the starting commits.
 * <p>
 * This generator is selected if the RevFilter is only
 * {@link org.eclipse.jgit.revwalk.filter.RevFilter#MERGE_BASE}.
 * <p>
 * To compute the merge base we assign a temporary flag to each of the starting
 * commits. The maximum number of starting commits is bounded by the number of
 * free flags available in the RevWalk when the generator is initialized. These
 * flags will be automatically released on the next reset of the RevWalk, but
 * not until then, as they are assigned to commits throughout the history.
 * <p>
 * Several internal flags are reused here for a different purpose, but this
 * should not have any impact as this generator should be run alone, and without
 * any other generators wrapped around it.
 */
class MergeBaseGenerator extends Generator {
	private static final int PARSED = RevWalk.PARSED;

	private static final int IN_PENDING = RevWalk.SEEN;

	private static final int POPPED = RevWalk.TEMP_MARK;

	private static final int MERGE_BASE = RevWalk.REWRITE;

	private final RevWalk walker;

	private final DateRevQueue pending;

	private int branchMask;

	private int recarryTest;

	private int recarryMask;

	MergeBaseGenerator(final RevWalk w) {
		walker = w;
		pending = new DateRevQueue();
	}

	void init(final AbstractRevQueue p) {
		try {
			for (;;) {
				final RevCommit c = p.next();
				if (c == null)
					break;
				add(c);
			}
		} finally {
			// Always free the flags immediately. This ensures the flags
			// will be available for reuse when the walk resets.
			//
			walker.freeFlag(branchMask);

			// Setup the condition used by carryOntoOne to detect a late
			// merge base and produce it on the next round.
			//
			recarryTest = branchMask | POPPED;
			recarryMask = branchMask | POPPED | MERGE_BASE;
		}
	}

	private void add(final RevCommit c) {
		final int flag = walker.allocFlag();
		branchMask |= flag;
		if ((c.flags & branchMask) != 0) {
			// This should never happen. RevWalk ensures we get a
			// commit admitted to the initial queue only once. If
			// we see this marks aren't correctly erased.
			//
			throw new IllegalStateException(MessageFormat.format(JGitText.get().staleRevFlagsOn, c.name()));
		}
		c.flags |= flag;
		pending.add(c);
	}

	@Override
	int outputType() {
		return 0;
	}

	@Override
	RevCommit next() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		for (;;) {
			final RevCommit c = pending.next();
			if (c == null) {
				walker.reader.walkAdviceEnd();
				return null;
			}

			for (final RevCommit p : c.parents) {
				if ((p.flags & IN_PENDING) != 0)
					continue;
				if ((p.flags & PARSED) == 0)
					p.parseHeaders(walker);
				p.flags |= IN_PENDING;
				pending.add(p);
			}

			int carry = c.flags & branchMask;
			boolean mb = carry == branchMask;
			if (mb) {
				// If we are a merge base make sure our ancestors are
				// also flagged as being popped, so that they do not
				// generate to the caller.
				//
				carry |= MERGE_BASE;
			}
			carryOntoHistory(c, carry);

			if ((c.flags & MERGE_BASE) != 0) {
				// This commit is an ancestor of a merge base we already
				// popped back to the caller. If everyone in pending is
				// that way we are done traversing; if not we just need
				// to move to the next available commit and try again.
				//
				if (pending.everbodyHasFlag(MERGE_BASE))
					return null;
				continue;
			}
			c.flags |= POPPED;

			if (mb) {
				c.flags |= MERGE_BASE;
				return c;
			}
		}
	}

	private void carryOntoHistory(RevCommit c, final int carry) {
		for (;;) {
			final RevCommit[] pList = c.parents;
			if (pList == null)
				return;
			final int n = pList.length;
			if (n == 0)
				return;

			for (int i = 1; i < n; i++) {
				final RevCommit p = pList[i];
				if (!carryOntoOne(p, carry))
					carryOntoHistory(p, carry);
			}

			c = pList[0];
			if (carryOntoOne(c, carry))
				break;
		}
	}

	private boolean carryOntoOne(final RevCommit p, final int carry) {
		final boolean haveAll = (p.flags & carry) == carry;
		p.flags |= carry;

		if ((p.flags & recarryMask) == recarryTest) {
			// We were popped without being a merge base, but we just got
			// voted to be one. Inject ourselves back at the front of the
			// pending queue and tell all of our ancestors they are within
			// the merge base now.
			//
			p.flags &= ~POPPED;
			pending.add(p);
			carryOntoHistory(p, branchMask | MERGE_BASE);
			return true;
		}

		// If we already had all carried flags, our parents do too.
		// Return true to stop the caller from running down this leg
		// of the revision graph any further.
		//
		return haveAll;
	}
}
