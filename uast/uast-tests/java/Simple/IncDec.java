/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class IncDec {
    public static int foo() {
        int i1 = 1;    // 1
        int i2 = ++i1; // 2, 2
        int i3 = i2++; // 2, 3
        int i4 = --i3; // 1, 1
        int i5 = i4--; // 1, 0
        return i4 + i5;// 1
    }
}