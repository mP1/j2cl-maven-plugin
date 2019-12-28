/*
 * Copyright 2019 Miroslav Pokorny (github.com/mP1)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package walkingkooka.j2cl.maven;

import org.apache.commons.codec.binary.Hex;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Builds a SHA1 composed of several inputs, such as dependencies & maven parameters.
 */
final class HashBuilder {

    private final MessageDigest digest;
    private String hash;

    static HashBuilder empty() {
        return new HashBuilder();
    }

    private HashBuilder() {
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    HashBuilder append(final Enum<?> e) {
        return this.append(e.name());
    }

    HashBuilder append(final String text) {
        return this.append(text.getBytes(Charset.defaultCharset()));
    }

    HashBuilder append(final byte[] content) {
        final String hash = this.hash;
        if (null != hash) {
            throw new IllegalStateException("Hash already computed: " + hash);
        }

        this.digest.update(content);
        return this;
    }

    /**
     * The builder that returns the SHA as hex digits.
     */
    @Override
    public String toString() {
        if (null == this.hash) {
            this.hash = Hex.encodeHexString(digest.digest());
        }
        return this.hash;
    }
}
