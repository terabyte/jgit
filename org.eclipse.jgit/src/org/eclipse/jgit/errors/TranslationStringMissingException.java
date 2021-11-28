/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com>
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
package org.eclipse.jgit.errors;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * This exception will be thrown when a translation string for a translation
 * bundle and locale is missing.
 */
public class TranslationStringMissingException extends TranslationBundleException {
	private static final long serialVersionUID = 1L;

	private final String key;

	/**
	 * Construct a {@link TranslationStringMissingException} for the specified
	 * bundle class, locale and translation key
	 *
	 * @param bundleClass
	 *            the bundle class for which a translation string was missing
	 * @param locale
	 *            the locale for which a translation string was missing
	 * @param key
	 *            the key of the missing translation string
	 * @param cause
	 *            the original exception thrown from the
	 *            {@link ResourceBundle#getString(String)} method.
	 */
	public TranslationStringMissingException(Class bundleClass, Locale locale, String key, Exception cause) {
		super("Translation missing for [" + bundleClass.getName() + ", " //$NON-NLS-1$ //$NON-NLS-2$
				+ locale.toString() + ", " + key + "]", bundleClass, locale, //$NON-NLS-1$ //$NON-NLS-2$
				cause);
		this.key = key;
	}

	/**
	 * @return the key of the missing translation string
	 */
	public String getKey() {
		return key;
	}
}
