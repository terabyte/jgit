/*
 * Copyright (C) 2011, GitHub Inc.
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
package org.eclipse.jgit.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

/**
 * A class used to execute a submodule sync command.
 *
 * This will set the remote URL in a submodule's repository to the current value
 * in the .gitmodules file.
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-submodule.html"
 *      >Git documentation about submodules</a>
 */
public class SubmoduleSyncCommand extends GitCommand<Map<String, String>> {

	private final Collection<String> paths;

	/**
	 * @param repo
	 */
	public SubmoduleSyncCommand(final Repository repo) {
		super(repo);
		paths = new ArrayList<String>();
	}

	/**
	 * Add repository-relative submodule path to synchronize
	 *
	 * @param path
	 * @return this command
	 */
	public SubmoduleSyncCommand addPath(final String path) {
		paths.add(path);
		return this;
	}

	/**
	 * Get branch that HEAD currently points to
	 *
	 * @param subRepo
	 * @return shortened branch name, null on failures
	 * @throws IOException
	 */
	protected String getHeadBranch(final Repository subRepo) throws IOException {
		Ref head = subRepo.getRef(Constants.HEAD);
		if (head != null && head.isSymbolic())
			return Repository.shortenRefName(head.getLeaf().getName());
		else
			return null;
	}

	public Map<String, String> call() throws GitAPIException {
		checkCallable();

		try {
			SubmoduleWalk generator = SubmoduleWalk.forIndex(repo);
			if (!paths.isEmpty())
				generator.setFilter(PathFilterGroup.createFromStrings(paths));
			Map<String, String> synced = new HashMap<String, String>();
			StoredConfig config = repo.getConfig();
			while (generator.next()) {
				String remoteUrl = generator.getRemoteUrl();
				if (remoteUrl == null)
					continue;

				String path = generator.getPath();
				config.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
						path, ConfigConstants.CONFIG_KEY_URL, remoteUrl);
				synced.put(path, remoteUrl);

				Repository subRepo = generator.getRepository();
				if (subRepo == null)
					continue;

				StoredConfig subConfig;
				String branch;
				try {
					subConfig = subRepo.getConfig();
					// Get name of remote associated with current branch and
					// fall back to default remote name as last resort
					branch = getHeadBranch(subRepo);
					String remote = null;
					if (branch != null)
						remote = subConfig.getString(
								ConfigConstants.CONFIG_BRANCH_SECTION, branch,
								ConfigConstants.CONFIG_KEY_REMOTE);
					if (remote == null)
						remote = Constants.DEFAULT_REMOTE_NAME;

					subConfig.setString(ConfigConstants.CONFIG_REMOTE_SECTION,
							remote, ConfigConstants.CONFIG_KEY_URL, remoteUrl);
					subConfig.save();
				} finally {
					subRepo.close();
				}
			}
			if (!synced.isEmpty())
				config.save();
			return synced;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (ConfigInvalidException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}
}