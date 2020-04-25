/*
 * Copyright © 2020 Miroslav Pokorny
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
 */
package test;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(JunitTest.class)
public class JunitTest {

    @Test
    public void testDependencyCustomSrc() {
        Assert.assertEquals("Dependency1", dependency1.Dependency1.getMessage());
    }

//    @Test
//    public void testDependencyCustomResources() {
//        Assert.assertEquals("Dependency2", dependency2.Dependency2.getMessage());
//    }
//
//    @Test
//    public void testDependencyCustomTestSrc() {
//        Assert.assertEquals("Dependency3", dependency3.Dependency3.getMessage());
//    }
//
//    @Test
//    public void testDependencyCustomTestResources() {
//        Assert.assertEquals("Dependency4", dependency4.Dependency4.getMessage());
//    }

    @Test
    public void testProjectCustomSrc() {
        Assert.assertEquals("Dependency5", dependency5.Dependency5.getMessage());
    }

    @Test
    public void testProjectCustomResources() {
        Assert.assertEquals("Dependency6", dependency6.Dependency6.getMessage());
    }

    @Test
    public void testProjectCustomTestSrc() {
        Assert.assertEquals("Dependency7", dependency7.Dependency7.getMessage());
    }

    @Test
    public void testProjectCustomTestResources() {
        Assert.assertEquals("Dependency8", dependency8.Dependency8.getMessage());
    }
}
