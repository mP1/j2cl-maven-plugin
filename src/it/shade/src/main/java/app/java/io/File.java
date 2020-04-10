/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package app.java.io;

import jsinterop.annotations.JsType;
/**
 * This file eventually gets translated into java.io.File
 *
 * <p>Note that it is marked as @JsType as we would like to call have whole class available to use
 * from JavaScript.
 */
@JsType
public class File {

    public File(final String path) {
        this.path = path;
    }
    
    @Override
    public String toString() {
        return "!!!" + this.getClass().getName() + " path=" + path;
    }

    private final String path;
}
