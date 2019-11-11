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
package example.formattinglib;

import jsinterop.annotations.JsType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A simple hello world example.
 *
 * <p>Note that it is marked as @JsType as we would like to call have whole class available to use
 * from JavaScript.
 */
@JsType
public class HelloWorld {

    public static String getHelloWorld() {
        final List<String> list = Arrays.asList("A1", "C3", "B2");
        Collections.sort(list);
        return "ClosureFormattingOption: Hello from Java!->" + list;
    }
}
