/*
 * Copyright (C) 2009-2010, Google Inc.
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

package org.eclipse.jgit.transport.resolver;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;

/**
 * Locate a Git {@link Repository} by name from the URL.
 *
 * @param <C>
 *            type of connection.
 */
public interface RepositoryResolver<C> {
	/** Resolver configured to open nothing. */
	public static final RepositoryResolver<?> NONE = new RepositoryResolver<Object>() {
		public Repository open(Object req, String name)
				throws RepositoryNotFoundException {
			throw new RepositoryNotFoundException(name);
		}
	};

	/**
	 * Locate and open a reference to a {@link Repository}.
	 * <p>
	 * The caller is responsible for closing the returned Repository.
	 *
	 * @param req
	 *            the current request, may be used to inspect session state
	 *            including cookies or user authentication.
	 * @param name
	 *            name of the repository, as parsed out of the URL.
	 * @return the opened repository instance, never null.
	 * @throws RepositoryNotFoundException
	 *             the repository does not exist or the name is incorrectly
	 *             formatted as a repository name.
	 * @throws ServiceNotAuthorizedException
	 *             the repository may exist, but HTTP access is not allowed
	 *             without authentication, i.e. this corresponds to an HTTP 401
	 *             Unauthorized.
	 * @throws ServiceNotEnabledException
	 *             the repository may exist, but HTTP access is not allowed on the
	 *             target repository, for the current user.
	 * @throws ServiceMayNotContinueException
	 *             the repository may exist, but HTTP access is not allowed for
	 *             the current request. The exception message contains a detailed
	 *             message that should be shown to the user.
	 */
	Repository open(C req, String name) throws RepositoryNotFoundException,
			ServiceNotAuthorizedException, ServiceNotEnabledException,
			ServiceMayNotContinueException;
}
