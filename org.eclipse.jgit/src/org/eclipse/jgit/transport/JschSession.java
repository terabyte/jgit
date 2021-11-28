/*
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2009, Google, Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.util.io.StreamCopyThread;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * Run remote commands using Jsch.
 * <p>
 * This class is the default session implementation using Jsch. Note that
 * {@link JschConfigSessionFactory} is used to create the actual session passed
 * to the constructor.
 */
public class JschSession implements RemoteSession {
	private final Session sock;
	private final URIish uri;

	/**
	 * Create a new session object by passing the real Jsch session and the URI
	 * information.
	 * 
	 * @param session
	 *            the real Jsch session created elsewhere.
	 * @param uri
	 *            the URI information for the remote connection
	 */
	public JschSession(final Session session, URIish uri) {
		sock = session;
		this.uri = uri;
	}

	public Process exec(String command, int timeout) throws IOException {
		return new JschProcess(command, timeout);
	}

	public void disconnect() {
		if (sock.isConnected())
			sock.disconnect();
	}

	/**
	 * A kludge to allow {@link TransportSftp} to get an Sftp channel from Jsch.
	 * Ideally, this method would be generic, which would require implementing
	 * generic Sftp channel operations in the RemoteSession class.
	 *
	 * @return a channel suitable for Sftp operations.
	 * @throws JSchException
	 *             on problems getting the channel.
	 */
	public Channel getSftpChannel() throws JSchException {
		return sock.openChannel("sftp"); //$NON-NLS-1$
	}

	/**
	 * Implementation of Process for running a single command using Jsch.
	 * <p>
	 * Uses the Jsch session to do actual command execution and manage the
	 * execution.
	 */
	private class JschProcess extends Process {
		private ChannelExec channel;

		private final int timeout;

		private InputStream inputStream;

		private OutputStream outputStream;

		private InputStream errStream;

		/**
		 * Opens a channel on the session ("sock") for executing the given
		 * command, opens streams, and starts command execution.
		 *
		 * @param commandName
		 *            the command to execute
		 * @param tms
		 *            the timeout value, in seconds, for the command.
		 * @throws TransportException
		 *             on problems opening a channel or connecting to the remote
		 *             host
		 * @throws IOException
		 *             on problems opening streams
		 */
		private JschProcess(final String commandName, int tms)
				throws TransportException, IOException {
			timeout = tms;
			try {
				channel = (ChannelExec) sock.openChannel("exec"); //$NON-NLS-1$
				channel.setCommand(commandName);
				setupStreams();
				channel.connect(timeout > 0 ? timeout * 1000 : 0);
				if (!channel.isConnected())
					throw new TransportException(uri, "connection failed");
			} catch (JSchException e) {
				throw new TransportException(uri, e.getMessage(), e);
			}
		}

		private void setupStreams() throws IOException {
			inputStream = channel.getInputStream();

			// JSch won't let us interrupt writes when we use our InterruptTimer
			// to break out of a long-running write operation. To work around
			// that we spawn a background thread to shuttle data through a pipe,
			// as we can issue an interrupted write out of that. Its slower, so
			// we only use this route if there is a timeout.
			final OutputStream out = channel.getOutputStream();
			if (timeout <= 0) {
				outputStream = out;
			} else {
				final PipedInputStream pipeIn = new PipedInputStream();
				final StreamCopyThread copier = new StreamCopyThread(pipeIn,
						out);
				final PipedOutputStream pipeOut = new PipedOutputStream(pipeIn) {
					@Override
					public void flush() throws IOException {
						super.flush();
						copier.flush();
					}

					@Override
					public void close() throws IOException {
						super.close();
						try {
							copier.join(timeout * 1000);
						} catch (InterruptedException e) {
							// Just wake early, the thread will terminate
							// anyway.
						}
					}
				};
				copier.start();
				outputStream = pipeOut;
			}

			errStream = channel.getErrStream();
		}

		@Override
		public InputStream getInputStream() {
			return inputStream;
		}

		@Override
		public OutputStream getOutputStream() {
			return outputStream;
		}

		@Override
		public InputStream getErrorStream() {
			return errStream;
		}

		@Override
		public int exitValue() {
			if (isRunning())
				throw new IllegalStateException();
			return channel.getExitStatus();
		}

		private boolean isRunning() {
			return channel.getExitStatus() < 0 && channel.isConnected();
		}

		@Override
		public void destroy() {
			if (channel.isConnected())
				channel.disconnect();
		}

		@Override
		public int waitFor() throws InterruptedException {
			while (isRunning())
				Thread.sleep(100);
			return exitValue();
		}
	}
}