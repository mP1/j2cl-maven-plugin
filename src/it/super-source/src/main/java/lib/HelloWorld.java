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
package lib;

import jsinterop.annotations.JsType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@JsType
public class HelloWorld {

    public static String getHelloWorld() {
        return "Modifier.isInterface!->" + java.lang.reflect.Modifier.isInterface(0) + " == true"; // Modifier is super sourced
    }
}
