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

package org.eclipse.jgit.util;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;

/**
 * Specialized Map to present a {@code RefDatabase} namespace.
 * <p>
 * Although not declared as a {@link java.util.SortedMap}, iterators from this
 * map's projections always return references in {@link RefComparator} ordering.
 * The map's internal representation is a sorted array of {@link Ref} objects,
 * which means lookup and replacement is O(log N), while insertion and removal
 * can be as expensive as O(N + log N) while the list expands or contracts.
 * Since this is not a general map implementation, all entries must be keyed by
 * the reference name.
 * <p>
 * This class is really intended as a helper for {@code RefDatabase}, which
 * needs to perform a merge-join of three sorted {@link RefList}s in order to
 * present the unified namespace of the packed-refs file, the loose refs/
 * directory tree, and the resolved form of any symbolic references.
 */
public class RefMap extends AbstractMap<String, Ref> {
	/**
	 * Prefix denoting the reference subspace this map contains.
	 * <p>
	 * All reference names in this map must start with this prefix. If the
	 * prefix is not the empty string, it must end with a '/'.
	 */
	private final String prefix;

	/** Immutable collection of the packed references at construction time. */
	private RefList<Ref> packed;

	/**
	 * Immutable collection of the loose references at construction time.
	 * <p>
	 * If an entry appears here and in {@link #packed}, this entry must take
	 * precedence, as its more current. Symbolic references in this collection
	 * are typically unresolved, so they only tell us who their target is, but
	 * not the current value of the target.
	 */
	private RefList<Ref> loose;

	/**
	 * Immutable collection of resolved symbolic references.
	 * <p>
	 * This collection contains only the symbolic references we were able to
	 * resolve at map construction time. Other loose references must be read
	 * from {@link #loose}. Every entry in this list must be matched by an entry
	 * in {@code loose}, otherwise it might be omitted by the map.
	 */
	private RefList<Ref> resolved;

	private int size;

	private boolean sizeIsValid;

	private Set<Entry<String, Ref>> entrySet;

	/** Construct an empty map with a small initial capacity. */
	public RefMap() {
		prefix = ""; //$NON-NLS-1$
		packed = RefList.emptyList();
		loose = RefList.emptyList();
		resolved = RefList.emptyList();
	}

	/**
	 * Construct a map to merge 3 collections together.
	 *
	 * @param prefix
	 *            prefix used to slice the lists down. Only references whose
	 *            names start with this prefix will appear to reside in the map.
	 *            Must not be null, use {@code ""} (the empty string) to select
	 *            all list items.
	 * @param packed
	 *            items from the packed reference list, this is the last list
	 *            searched.
	 * @param loose
	 *            items from the loose reference list, this list overrides
	 *            {@code packed} if a name appears in both.
	 * @param resolved
	 *            resolved symbolic references. This list overrides the prior
	 *            list {@code loose}, if an item appears in both. Items in this
	 *            list <b>must</b> also appear in {@code loose}.
	 */
	@SuppressWarnings("unchecked")
	public RefMap(String prefix, RefList<? extends Ref> packed,
			RefList<? extends Ref> loose, RefList<? extends Ref> resolved) {
		this.prefix = prefix;
		this.packed = (RefList<Ref>) packed;
		this.loose = (RefList<Ref>) loose;
		this.resolved = (RefList<Ref>) resolved;
	}

	@Override
	public boolean containsKey(Object name) {
		return get(name) != null;
	}

	@Override
	public Ref get(Object key) {
		String name = toRefName((String) key);
		Ref ref = resolved.get(name);
		if (ref == null)
			ref = loose.get(name);
		if (ref == null)
			ref = packed.get(name);
		return ref;
	}

	@Override
	public Ref put(final String keyName, Ref value) {
		String name = toRefName(keyName);

		if (!name.equals(value.getName()))
			throw new IllegalArgumentException();

		if (!resolved.isEmpty()) {
			// Collapse the resolved list into the loose list so we
			// can discard it and stop joining the two together.
			for (Ref ref : resolved)
				loose = loose.put(ref);
			resolved = RefList.emptyList();
		}

		int idx = loose.find(name);
		if (0 <= idx) {
			Ref prior = loose.get(name);
			loose = loose.set(idx, value);
			return prior;
		} else {
			Ref prior = get(keyName);
			loose = loose.add(idx, value);
			sizeIsValid = false;
			return prior;
		}
	}

	@Override
	public Ref remove(Object key) {
		String name = toRefName((String) key);
		Ref res = null;
		int idx;
		if (0 <= (idx = packed.find(name))) {
			res = packed.get(name);
			packed = packed.remove(idx);
			sizeIsValid = false;
		}
		if (0 <= (idx = loose.find(name))) {
			res = loose.get(name);
			loose = loose.remove(idx);
			sizeIsValid = false;
		}
		if (0 <= (idx = resolved.find(name))) {
			res = resolved.get(name);
			resolved = resolved.remove(idx);
			sizeIsValid = false;
		}
		return res;
	}

	@Override
	public boolean isEmpty() {
		return entrySet().isEmpty();
	}

	@Override
	public Set<Entry<String, Ref>> entrySet() {
		if (entrySet == null) {
			entrySet = new AbstractSet<Entry<String, Ref>>() {
				@Override
				public Iterator<Entry<String, Ref>> iterator() {
					return new SetIterator();
				}

				@Override
				public int size() {
					if (!sizeIsValid) {
						size = 0;
						Iterator<?> i = entrySet().iterator();
						for (; i.hasNext(); i.next())
							size++;
						sizeIsValid = true;
					}
					return size;
				}

				@Override
				public boolean isEmpty() {
					if (sizeIsValid)
						return 0 == size;
					return !iterator().hasNext();
				}

				@Override
				public void clear() {
					packed = RefList.emptyList();
					loose = RefList.emptyList();
					resolved = RefList.emptyList();
					size = 0;
					sizeIsValid = true;
				}
			};
		}
		return entrySet;
	}

	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		boolean first = true;
		r.append('[');
		for (Ref ref : values()) {
			if (first)
				first = false;
			else
				r.append(", "); //$NON-NLS-1$
			r.append(ref);
		}
		r.append(']');
		return r.toString();
	}

	private String toRefName(String name) {
		if (0 < prefix.length())
			name = prefix + name;
		return name;
	}

	private String toMapKey(Ref ref) {
		String name = ref.getName();
		if (0 < prefix.length())
			name = name.substring(prefix.length());
		return name;
	}

	private class SetIterator implements Iterator<Entry<String, Ref>> {
		private int packedIdx;

		private int looseIdx;

		private int resolvedIdx;

		private Entry<String, Ref> next;

		SetIterator() {
			if (0 < prefix.length()) {
				packedIdx = -(packed.find(prefix) + 1);
				looseIdx = -(loose.find(prefix) + 1);
				resolvedIdx = -(resolved.find(prefix) + 1);
			}
		}

		public boolean hasNext() {
			if (next == null)
				next = peek();
			return next != null;
		}

		public Entry<String, Ref> next() {
			if (hasNext()) {
				Entry<String, Ref> r = next;
				next = peek();
				return r;
			}
			throw new NoSuchElementException();
		}

		public Entry<String, Ref> peek() {
			if (packedIdx < packed.size() && looseIdx < loose.size()) {
				Ref p = packed.get(packedIdx);
				Ref l = loose.get(looseIdx);
				int cmp = RefComparator.compareTo(p, l);
				if (cmp < 0) {
					packedIdx++;
					return toEntry(p);
				}

				if (cmp == 0)
					packedIdx++;
				looseIdx++;
				return toEntry(resolveLoose(l));
			}

			if (looseIdx < loose.size())
				return toEntry(resolveLoose(loose.get(looseIdx++)));
			if (packedIdx < packed.size())
				return toEntry(packed.get(packedIdx++));
			return null;
		}

		private Ref resolveLoose(final Ref l) {
			if (resolvedIdx < resolved.size()) {
				Ref r = resolved.get(resolvedIdx);
				int cmp = RefComparator.compareTo(l, r);
				if (cmp == 0) {
					resolvedIdx++;
					return r;
				} else if (cmp > 0) {
					// WTF, we have a symbolic entry but no match
					// in the loose collection. That's an error.
					throw new IllegalStateException();
				}
			}
			return l;
		}

		private Ent toEntry(Ref p) {
			if (p.getName().startsWith(prefix))
				return new Ent(p);
			packedIdx = packed.size();
			looseIdx = loose.size();
			resolvedIdx = resolved.size();
			return null;
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	private class Ent implements Entry<String, Ref> {
		private Ref ref;

		Ent(Ref ref) {
			this.ref = ref;
		}

		public String getKey() {
			return toMapKey(ref);
		}

		public Ref getValue() {
			return ref;
		}

		public Ref setValue(Ref value) {
			Ref prior = put(getKey(), value);
			ref = value;
			return prior;
		}

		@Override
		public int hashCode() {
			return getKey().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Map.Entry) {
				final Object key = ((Map.Entry) obj).getKey();
				final Object val = ((Map.Entry) obj).getValue();
				if (key instanceof String && val instanceof Ref) {
					final Ref r = (Ref) val;
					if (r.getName().equals(ref.getName())) {
						final ObjectId a = r.getObjectId();
						final ObjectId b = ref.getObjectId();
						if (a != null && b != null && AnyObjectId.equals(a, b))
							return true;
					}
				}
			}
			return false;
		}

		@Override
		public String toString() {
			return ref.toString();
		}
	}
}
