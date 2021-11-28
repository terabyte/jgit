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

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Abstraction of name to {@link ObjectId} mapping.
 * <p>
 * A reference database stores a mapping of reference names to {@link ObjectId}.
 * Every {@link Repository} has a single reference database, mapping names to
 * the tips of the object graph contained by the {@link ObjectDatabase}.
 */
public abstract class RefDatabase {
	/**
	 * Order of prefixes to search when using non-absolute references.
	 * <p>
	 * The implementation's {@link #getRef(String)} method must take this search
	 * space into consideration when locating a reference by name. The first
	 * entry in the path is always {@code ""}, ensuring that absolute references
	 * are resolved without further mangling.
	 */
	protected static final String[] SEARCH_PATH = { "", //$NON-NLS-1$
			Constants.R_REFS, //
			Constants.R_TAGS, //
			Constants.R_HEADS, //
			Constants.R_REMOTES //
	};

	/**
	 * Maximum number of times a {@link SymbolicRef} can be traversed.
	 * <p>
	 * If the reference is nested deeper than this depth, the implementation
	 * should either fail, or at least claim the reference does not exist.
	 */
	protected static final int MAX_SYMBOLIC_REF_DEPTH = 5;

	/** Magic value for {@link #getRefs(String)} to return all references. */
	public static final String ALL = "";//$NON-NLS-1$

	/**
	 * Initialize a new reference database at this location.
	 *
	 * @throws IOException
	 *             the database could not be created.
	 */
	public abstract void create() throws IOException;

	/** Close any resources held by this database. */
	public abstract void close();

	/**
	 * Determine if a proposed reference name overlaps with an existing one.
	 * <p>
	 * Reference names use '/' as a component separator, and may be stored in a
	 * hierarchical storage such as a directory on the local filesystem.
	 * <p>
	 * If the reference "refs/heads/foo" exists then "refs/heads/foo/bar" must
	 * not exist, as a reference cannot have a value and also be a container for
	 * other references at the same time.
	 * <p>
	 * If the reference "refs/heads/foo/bar" exists than the reference
	 * "refs/heads/foo" cannot exist, for the same reason.
	 *
	 * @param name
	 *            proposed name.
	 * @return true if the name overlaps with an existing reference; false if
	 *         using this name right now would be safe.
	 * @throws IOException
	 *             the database could not be read to check for conflicts.
	 */
	public abstract boolean isNameConflicting(String name) throws IOException;

	/**
	 * Create a new update command to create, modify or delete a reference.
	 *
	 * @param name
	 *            the name of the reference.
	 * @param detach
	 *            if {@code true} and {@code name} is currently a
	 *            {@link SymbolicRef}, the update will replace it with an
	 *            {@link ObjectIdRef}. Otherwise, the update will recursively
	 *            traverse {@link SymbolicRef}s and operate on the leaf
	 *            {@link ObjectIdRef}.
	 * @return a new update for the requested name; never null.
	 * @throws IOException
	 *             the reference space cannot be accessed.
	 */
	public abstract RefUpdate newUpdate(String name, boolean detach)
			throws IOException;

	/**
	 * Create a new update command to rename a reference.
	 *
	 * @param fromName
	 *            name of reference to rename from
	 * @param toName
	 *            name of reference to rename to
	 * @return an update command that knows how to rename a branch to another.
	 * @throws IOException
	 *             the reference space cannot be accessed.
	 */
	public abstract RefRename newRename(String fromName, String toName)
			throws IOException;

	/**
	 * Create a new batch update to attempt on this database.
	 * <p>
	 * The default implementation performs a sequential update of each command.
	 *
	 * @return a new batch update object.
	 */
	public BatchRefUpdate newBatchUpdate() {
		return new BatchRefUpdate(this);
	}

	/**
	 * Read a single reference.
	 * <p>
	 * Aside from taking advantage of {@link #SEARCH_PATH}, this method may be
	 * able to more quickly resolve a single reference name than obtaining the
	 * complete namespace by {@code getRefs(ALL).get(name)}.
	 *
	 * @param name
	 *            the name of the reference. May be a short name which must be
	 *            searched for using the standard {@link #SEARCH_PATH}.
	 * @return the reference (if it exists); else {@code null}.
	 * @throws IOException
	 *             the reference space cannot be accessed.
	 */
	public abstract Ref getRef(String name) throws IOException;

	/**
	 * Get a section of the reference namespace.
	 *
	 * @param prefix
	 *            prefix to search the namespace with; must end with {@code /}.
	 *            If the empty string ({@link #ALL}), obtain a complete snapshot
	 *            of all references.
	 * @return modifiable map that is a complete snapshot of the current
	 *         reference namespace, with {@code prefix} removed from the start
	 *         of each key. The map can be an unsorted map.
	 * @throws IOException
	 *             the reference space cannot be accessed.
	 */
	public abstract Map<String, Ref> getRefs(String prefix) throws IOException;

	/**
	 * Get the additional reference-like entities from the repository.
	 * <p>
	 * The result list includes non-ref items such as MERGE_HEAD and
	 * FETCH_RESULT cast to be refs. The names of these refs are not returned by
	 * <code>getRefs(ALL)</code> but are accepted by {@link #getRef(String)}
	 *
	 * @return a list of additional refs
	 * @throws IOException
	 *             the reference space cannot be accessed.
	 */
	public abstract List<Ref> getAdditionalRefs() throws IOException;

	/**
	 * Peel a possibly unpeeled reference by traversing the annotated tags.
	 * <p>
	 * If the reference cannot be peeled (as it does not refer to an annotated
	 * tag) the peeled id stays null, but {@link Ref#isPeeled()} will be true.
	 * <p>
	 * Implementors should check {@link Ref#isPeeled()} before performing any
	 * additional work effort.
	 *
	 * @param ref
	 *            The reference to peel
	 * @return {@code ref} if {@code ref.isPeeled()} is true; otherwise a new
	 *         Ref object representing the same data as Ref, but isPeeled() will
	 *         be true and getPeeledObjectId() will contain the peeled object
	 *         (or null).
	 * @throws IOException
	 *             the reference space or object space cannot be accessed.
	 */
	public abstract Ref peel(Ref ref) throws IOException;

	/**
	 * Triggers a refresh of all internal data structures.
	 * <p>
	 * In case the RefDatabase implementation has internal caches this method
	 * will trigger that all these caches are cleared.
	 * <p>
	 * Implementors should overwrite this method if they use any kind of caches.
	 */
	public void refresh() {
		// nothing
	}
}
