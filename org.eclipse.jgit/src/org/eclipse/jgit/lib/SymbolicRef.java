/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.lib;

/**
 * A reference that indirectly points at another {@link Ref}.
 * <p>
 * A symbolic reference always derives its current value from the target
 * reference.
 */
public class SymbolicRef implements Ref {
	private final String name;

	private final Ref target;

	/**
	 * Create a new ref pairing.
	 *
	 * @param refName
	 *            name of this ref.
	 * @param target
	 *            the ref we reference and derive our value from.
	 */
	public SymbolicRef(String refName, Ref target) {
		this.name = refName;
		this.target = target;
	}

	public String getName() {
		return name;
	}

	public boolean isSymbolic() {
		return true;
	}

	public Ref getLeaf() {
		Ref dst = getTarget();
		while (dst.isSymbolic())
			dst = dst.getTarget();
		return dst;
	}

	public Ref getTarget() {
		return target;
	}

	public ObjectId getObjectId() {
		return getLeaf().getObjectId();
	}

	public Storage getStorage() {
		return Storage.LOOSE;
	}

	public ObjectId getPeeledObjectId() {
		return getLeaf().getPeeledObjectId();
	}

	public boolean isPeeled() {
		return getLeaf().isPeeled();
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		r.append("SymbolicRef[");
		Ref cur = this;
		while (cur.isSymbolic()) {
			r.append(cur.getName());
			r.append(" -> ");
			cur = cur.getTarget();
		}
		r.append(cur.getName());
		r.append('=');
		r.append(ObjectId.toString(cur.getObjectId()));
		r.append("]");
		return r.toString();
	}
}
