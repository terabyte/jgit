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

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A command being processed by {@link BaseReceivePack}.
 * <p>
 * This command instance roughly translates to the server side representation of
 * the {@link RemoteRefUpdate} created by the client.
 */
public class ReceiveCommand {
	/** Type of operation requested. */
	public static enum Type {
		/** Create a new ref; the ref must not already exist. */
		CREATE,

		/**
		 * Update an existing ref with a fast-forward update.
		 * <p>
		 * During a fast-forward update no changes will be lost; only new
		 * commits are inserted into the ref.
		 */
		UPDATE,

		/**
		 * Update an existing ref by potentially discarding objects.
		 * <p>
		 * The current value of the ref is not fully reachable from the new
		 * value of the ref, so a successful command may result in one or more
		 * objects becoming unreachable.
		 */
		UPDATE_NONFASTFORWARD,

		/** Delete an existing ref; the ref should already exist. */
		DELETE;
	}

	/** Result of the update command. */
	public static enum Result {
		/** The command has not yet been attempted by the server. */
		NOT_ATTEMPTED,

		/** The server is configured to deny creation of this ref. */
		REJECTED_NOCREATE,

		/** The server is configured to deny deletion of this ref. */
		REJECTED_NODELETE,

		/** The update is a non-fast-forward update and isn't permitted. */
		REJECTED_NONFASTFORWARD,

		/** The update affects <code>HEAD</code> and cannot be permitted. */
		REJECTED_CURRENT_BRANCH,

		/**
		 * One or more objects aren't in the repository.
		 * <p>
		 * This is severe indication of either repository corruption on the
		 * server side, or a bug in the client wherein the client did not supply
		 * all required objects during the pack transfer.
		 */
		REJECTED_MISSING_OBJECT,

		/** Other failure; see {@link ReceiveCommand#getMessage()}. */
		REJECTED_OTHER_REASON,

		/** The ref could not be locked and updated atomically; try again. */
		LOCK_FAILURE,

		/** The change was completed successfully. */
		OK;
	}

	/**
	 * Filter a list of commands according to result.
	 *
	 * @param commands
	 *            commands to filter.
	 * @param want
	 *            desired status to filter by.
	 * @return a copy of the command list containing only those commands with
	 *         the desired status.
	 * @since 2.0
	 */
	public static List<ReceiveCommand> filter(List<ReceiveCommand> commands,
			final Result want) {
		List<ReceiveCommand> r = new ArrayList<ReceiveCommand>(commands.size());
		for (final ReceiveCommand cmd : commands) {
			if (cmd.getResult() == want)
				r.add(cmd);
		}
		return r;
	}

	private final ObjectId oldId;

	private final ObjectId newId;

	private final String name;

	private Type type;

	private Ref ref;

	private Result status;

	private String message;

	private boolean typeIsCorrect;

	/**
	 * Create a new command for {@link BaseReceivePack}.
	 *
	 * @param oldId
	 *            the old object id; must not be null. Use
	 *            {@link ObjectId#zeroId()} to indicate a ref creation.
	 * @param newId
	 *            the new object id; must not be null. Use
	 *            {@link ObjectId#zeroId()} to indicate a ref deletion.
	 * @param name
	 *            name of the ref being affected.
	 */
	public ReceiveCommand(final ObjectId oldId, final ObjectId newId,
			final String name) {
		this.oldId = oldId;
		this.newId = newId;
		this.name = name;

		type = Type.UPDATE;
		if (ObjectId.zeroId().equals(oldId))
			type = Type.CREATE;
		if (ObjectId.zeroId().equals(newId))
			type = Type.DELETE;
		status = Result.NOT_ATTEMPTED;
	}

	/**
	 * Create a new command for {@link BaseReceivePack}.
	 *
	 * @param oldId
	 *            the old object id; must not be null. Use
	 *            {@link ObjectId#zeroId()} to indicate a ref creation.
	 * @param newId
	 *            the new object id; must not be null. Use
	 *            {@link ObjectId#zeroId()} to indicate a ref deletion.
	 * @param name
	 *            name of the ref being affected.
	 * @param type
	 *            type of the command.
	 * @since 2.0
	 */
	public ReceiveCommand(final ObjectId oldId, final ObjectId newId,
			final String name, final Type type) {
		this.oldId = oldId;
		this.newId = newId;
		this.name = name;
		this.type = type;
	}

	/** @return the old value the client thinks the ref has. */
	public ObjectId getOldId() {
		return oldId;
	}

	/** @return the requested new value for this ref. */
	public ObjectId getNewId() {
		return newId;
	}

	/** @return the name of the ref being updated. */
	public String getRefName() {
		return name;
	}

	/** @return the type of this command; see {@link Type}. */
	public Type getType() {
		return type;
	}

	/** @return the ref, if this was advertised by the connection. */
	public Ref getRef() {
		return ref;
	}

	/** @return the current status code of this command. */
	public Result getResult() {
		return status;
	}

	/** @return the message associated with a failure status. */
	public String getMessage() {
		return message;
	}

	/**
	 * Set the status of this command.
	 *
	 * @param s
	 *            the new status code for this command.
	 */
	public void setResult(final Result s) {
		setResult(s, null);
	}

	/**
	 * Set the status of this command.
	 *
	 * @param s
	 *            new status code for this command.
	 * @param m
	 *            optional message explaining the new status.
	 */
	public void setResult(final Result s, final String m) {
		status = s;
		message = m;
	}

	/**
	 * Update the type of this command by checking for fast-forward.
	 * <p>
	 * If the command's current type is UPDATE, a merge test will be performed
	 * using the supplied RevWalk to determine if {@link #getOldId()} is fully
	 * merged into {@link #getNewId()}. If some commits are not merged the
	 * update type is changed to {@link Type#UPDATE_NONFASTFORWARD}.
	 *
	 * @param walk
	 *            an instance to perform the merge test with. The caller must
	 *            allocate and release this object.
	 * @throws IOException
	 *             either oldId or newId is not accessible in the repository
	 *             used by the RevWalk. This usually indicates data corruption,
	 *             and the command cannot be processed.
	 */
	public void updateType(RevWalk walk) throws IOException {
		if (typeIsCorrect)
			return;
		if (type == Type.UPDATE && !AnyObjectId.equals(oldId, newId)) {
			RevObject o = walk.parseAny(oldId);
			RevObject n = walk.parseAny(newId);
			if (!(o instanceof RevCommit)
					|| !(n instanceof RevCommit)
					|| !walk.isMergedInto((RevCommit) o, (RevCommit) n))
				setType(Type.UPDATE_NONFASTFORWARD);
		}
		typeIsCorrect = true;
	}

	/**
	 * Execute this command during a receive-pack session.
	 * <p>
	 * Sets the status of the command as a side effect.
	 *
	 * @param rp
	 *            receive-pack session.
	 * @since 2.0
	 */
	public void execute(final BaseReceivePack rp) {
		try {
			final RefUpdate ru = rp.getRepository().updateRef(getRefName());
			ru.setRefLogIdent(rp.getRefLogIdent());
			switch (getType()) {
			case DELETE:
				if (!ObjectId.zeroId().equals(getOldId())) {
					// We can only do a CAS style delete if the client
					// didn't bork its delete request by sending the
					// wrong zero id rather than the advertised one.
					//
					ru.setExpectedOldObjectId(getOldId());
				}
				ru.setForceUpdate(true);
				setResult(ru.delete(rp.getRevWalk()));
				break;

			case CREATE:
			case UPDATE:
			case UPDATE_NONFASTFORWARD:
				ru.setForceUpdate(rp.isAllowNonFastForwards());
				ru.setExpectedOldObjectId(getOldId());
				ru.setNewObjectId(getNewId());
				ru.setRefLogMessage("push", true); //$NON-NLS-1$
				setResult(ru.update(rp.getRevWalk()));
				break;
			}
		} catch (IOException err) {
			reject(err);
		}
	}

	void setRef(final Ref r) {
		ref = r;
	}

	void setType(final Type t) {
		type = t;
	}

	void setTypeFastForwardUpdate() {
		type = Type.UPDATE;
		typeIsCorrect = true;
	}

	/**
	 * Set the result of this command.
	 *
	 * @param r
	 *            the new result code for this command.
	 */
	public void setResult(RefUpdate.Result r) {
		switch (r) {
		case NOT_ATTEMPTED:
			setResult(Result.NOT_ATTEMPTED);
			break;

		case LOCK_FAILURE:
		case IO_FAILURE:
			setResult(Result.LOCK_FAILURE);
			break;

		case NO_CHANGE:
		case NEW:
		case FORCED:
		case FAST_FORWARD:
			setResult(Result.OK);
			break;

		case REJECTED:
			setResult(Result.REJECTED_NONFASTFORWARD);
			break;

		case REJECTED_CURRENT_BRANCH:
			setResult(Result.REJECTED_CURRENT_BRANCH);
			break;

		default:
			setResult(Result.REJECTED_OTHER_REASON, r.name());
			break;
		}
	}

	void reject(IOException err) {
		setResult(Result.REJECTED_OTHER_REASON, MessageFormat.format(
				JGitText.get().lockError, err.getMessage()));
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return getType().name() + ": " + getOldId().name() + " "
				+ getNewId().name() + " " + getRefName();
	}
}
