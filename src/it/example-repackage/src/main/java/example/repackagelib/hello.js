/*
 * Copyright 2018 Google Inc.
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

// We don't need JavaScript for document.write but for the sake having a richer
// example, here is a JavaScript module that uses our Java code.

goog.module('example.repackagelib.hello');

// Here we use goog.require to import the Java HelloWorld class to this module.
const HelloWorld = goog.require('example.repackagelib.HelloWorld');

/**
 * Document write to browser page
 *
 * @return {void}
 */
function sayHello() {
    document.write("" + HelloWorld.getHelloWorld());
}

// Export our method so it could be used outside of the module.
exports = {sayHello};
