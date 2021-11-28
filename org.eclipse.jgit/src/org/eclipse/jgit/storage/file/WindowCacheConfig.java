/*
 * Copyright (C) 2009, Google Inc.
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

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.pack.PackConfig;

/** Configuration parameters for {@link WindowCache}. */
public class WindowCacheConfig {
	/** 1024 (number of bytes in one kibibyte/kilobyte) */
	public static final int KB = 1024;

	/** 1024 {@link #KB} (number of bytes in one mebibyte/megabyte) */
	public static final int MB = 1024 * KB;

	private int packedGitOpenFiles;

	private long packedGitLimit;

	private int packedGitWindowSize;

	private boolean packedGitMMAP;

	private int deltaBaseCacheLimit;

	private int streamFileThreshold;

	/** Create a default configuration. */
	public WindowCacheConfig() {
		packedGitOpenFiles = 128;
		packedGitLimit = 10 * MB;
		packedGitWindowSize = 8 * KB;
		packedGitMMAP = false;
		deltaBaseCacheLimit = 10 * MB;
		streamFileThreshold = PackConfig.DEFAULT_BIG_FILE_THRESHOLD;
	}

	/**
	 * @return maximum number of streams to open at a time. Open packs count
	 *         against the process limits. <b>Default is 128.</b>
	 */
	public int getPackedGitOpenFiles() {
		return packedGitOpenFiles;
	}

	/**
	 * @param fdLimit
	 *            maximum number of streams to open at a time. Open packs count
	 *            against the process limits
	 */
	public void setPackedGitOpenFiles(final int fdLimit) {
		packedGitOpenFiles = fdLimit;
	}

	/**
	 * @return maximum number bytes of heap memory to dedicate to caching pack
	 *         file data. <b>Default is 10 MB.</b>
	 */
	public long getPackedGitLimit() {
		return packedGitLimit;
	}

	/**
	 * @param newLimit
	 *            maximum number bytes of heap memory to dedicate to caching
	 *            pack file data.
	 */
	public void setPackedGitLimit(final long newLimit) {
		packedGitLimit = newLimit;
	}

	/**
	 * @return size in bytes of a single window mapped or read in from the pack
	 *         file. <b>Default is 8 KB.</b>
	 */
	public int getPackedGitWindowSize() {
		return packedGitWindowSize;
	}

	/**
	 * @param newSize
	 *            size in bytes of a single window read in from the pack file.
	 */
	public void setPackedGitWindowSize(final int newSize) {
		packedGitWindowSize = newSize;
	}

	/**
	 * @return true enables use of Java NIO virtual memory mapping for windows;
	 *         false reads entire window into a byte[] with standard read calls.
	 *         <b>Default false.</b>
	 */
	public boolean isPackedGitMMAP() {
		return packedGitMMAP;
	}

	/**
	 * @param usemmap
	 *            true enables use of Java NIO virtual memory mapping for
	 *            windows; false reads entire window into a byte[] with standard
	 *            read calls.
	 */
	public void setPackedGitMMAP(final boolean usemmap) {
		packedGitMMAP = usemmap;
	}

	/**
	 * @return maximum number of bytes to cache in {@link DeltaBaseCache}
	 *         for inflated, recently accessed objects, without delta chains.
	 *         <b>Default 10 MB.</b>
	 */
	public int getDeltaBaseCacheLimit() {
		return deltaBaseCacheLimit;
	}

	/**
	 * @param newLimit
	 *            maximum number of bytes to cache in
	 *            {@link DeltaBaseCache} for inflated, recently accessed
	 *            objects, without delta chains.
	 */
	public void setDeltaBaseCacheLimit(final int newLimit) {
		deltaBaseCacheLimit = newLimit;
	}

	/** @return the size threshold beyond which objects must be streamed. */
	public int getStreamFileThreshold() {
		return streamFileThreshold;
	}

	/**
	 * @param newLimit
	 *            new byte limit for objects that must be streamed. Objects
	 *            smaller than this size can be obtained as a contiguous byte
	 *            array, while objects bigger than this size require using an
	 *            {@link org.eclipse.jgit.lib.ObjectStream}.
	 */
	public void setStreamFileThreshold(final int newLimit) {
		streamFileThreshold = newLimit;
	}

	/**
	 * Update properties by setting fields from the configuration.
	 * <p>
	 * If a property is not defined in the configuration, then it is left
	 * unmodified.
	 *
	 * @param rc configuration to read properties from.
	 */
	public void fromConfig(final Config rc) {
		setPackedGitOpenFiles(rc.getInt(
				"core", null, "packedgitopenfiles", getPackedGitOpenFiles())); //$NON-NLS-1$ //$NON-NLS-2$
		setPackedGitLimit(rc.getLong(
				"core", null, "packedgitlimit", getPackedGitLimit())); //$NON-NLS-1$ //$NON-NLS-2$
		setPackedGitWindowSize(rc.getInt(
				"core", null, "packedgitwindowsize", getPackedGitWindowSize())); //$NON-NLS-1$ //$NON-NLS-2$
		setPackedGitMMAP(rc.getBoolean(
				"core", null, "packedgitmmap", isPackedGitMMAP())); //$NON-NLS-1$ //$NON-NLS-2$
		setDeltaBaseCacheLimit(rc.getInt(
				"core", null, "deltabasecachelimit", getDeltaBaseCacheLimit())); //$NON-NLS-1$ //$NON-NLS-2$

		long maxMem = Runtime.getRuntime().maxMemory();
		long sft = rc.getLong(
				"core", null, "streamfilethreshold", getStreamFileThreshold()); //$NON-NLS-1$ //$NON-NLS-2$
		sft = Math.min(sft, maxMem / 4); // don't use more than 1/4 of the heap
		sft = Math.min(sft, Integer.MAX_VALUE); // cannot exceed array length
		setStreamFileThreshold((int) sft);
	}
}
