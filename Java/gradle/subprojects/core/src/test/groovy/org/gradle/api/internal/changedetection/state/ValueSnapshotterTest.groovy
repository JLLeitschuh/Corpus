/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state

import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import spock.lang.Specification

class ValueSnapshotterTest extends Specification {
    def snapshotter = new ValueSnapshotter(Stub(ClassLoaderHierarchyHasher))

    def "creates snapshot for string"() {
        expect:
        def snapshot = snapshotter.snapshot("abc")
        snapshot instanceof StringValueSnapshot
        snapshot == snapshotter.snapshot("abc")
        snapshot != snapshotter.snapshot("other")
    }

    def "creates snapshot for integer"() {
        expect:
        def snapshot = snapshotter.snapshot(123)
        snapshot instanceof IntegerValueSnapshot
        snapshot == snapshotter.snapshot(123)
        snapshot != snapshotter.snapshot(-1)
    }

    def "creates snapshot for boolean"() {
        expect:
        snapshotter.snapshot(true).is BooleanValueSnapshot.TRUE
        snapshotter.snapshot(false).is BooleanValueSnapshot.FALSE
    }

    def "creates snapshot for null value"() {
        expect:
        snapshotter.snapshot(null).is NullValueSnapshot.INSTANCE
    }

    def "creates snapshot for list"() {
        expect:
        def snapshot1 = snapshotter.snapshot([])
        snapshot1 instanceof ListValueSnapshot
        snapshot1 == snapshotter.snapshot([])
        snapshot1 != snapshotter.snapshot("abc")

        def snapshot2 = snapshotter.snapshot(["123"])
        snapshot2 instanceof ListValueSnapshot
        snapshot2 == snapshotter.snapshot(["123"])
        snapshot2 != snapshot1
    }

    def "creates snapshot for set"() {
        expect:
        def snapshot1 = snapshotter.snapshot([] as Set)
        snapshot1 instanceof SetValueSnapshot
        snapshot1 == snapshotter.snapshot([] as Set)
        snapshot1 != snapshotter.snapshot("abc")
        snapshot1 != snapshotter.snapshot([])

        def snapshot2 = snapshotter.snapshot(["123"] as Set)
        snapshot2 instanceof SetValueSnapshot
        snapshot2 == snapshotter.snapshot(["123"] as Set)
        snapshot2 != snapshotter.snapshot(["123"])
        snapshot2 != snapshot1
    }

    def "creates snapshot for map"() {
        expect:
        def snapshot1 = snapshotter.snapshot([:])
        snapshot1 instanceof MapValueSnapshot
        snapshot1 == snapshotter.snapshot([:])
        snapshot1 != snapshotter.snapshot("abc")
        snapshot1 != snapshotter.snapshot([a: "123"])

        def snapshot2 = snapshotter.snapshot([a: "123"])
        snapshot2 instanceof MapValueSnapshot
        snapshot2 == snapshotter.snapshot([a: "123"])
        snapshot2 != snapshotter.snapshot(["123"])
        snapshot2 != snapshotter.snapshot([:])
        snapshot2 != snapshotter.snapshot([a: "123", b: "abc"])
        snapshot2 != snapshot1
    }

    enum Type1 {
        ONE, TWO
    }

    enum Type2 {
        TWO, THREE
    }

    def "creates snapshot for enum type"() {
        expect:
        def snapshot = snapshotter.snapshot(Type1.TWO)
        snapshot instanceof EnumValueSnapshot
        snapshot == snapshotter.snapshot(Type1.TWO)
        snapshot != snapshotter.snapshot(Type1.ONE)
        snapshot != snapshotter.snapshot(Type2.TWO)
        snapshot != snapshotter.snapshot(new Bean(prop: "value2"))
    }

    def "creates snapshot for file type"() {
        expect:
        def snapshot = snapshotter.snapshot(new File("abc"))
        snapshot instanceof FileValueSnapshot
        snapshot == snapshotter.snapshot(new File("abc"))
        snapshot != snapshotter.snapshot(new File("abc").getAbsoluteFile())
        snapshot != snapshotter.snapshot(new File("123"))
        snapshot != snapshotter.snapshot(new Bean(prop: "value2"))
    }

    def "creates snapshot for serializable type"() {
        def value = new Bean()

        expect:
        def snapshot = snapshotter.snapshot(value)
        snapshot instanceof SerializedValueSnapshot
        snapshot == snapshotter.snapshot(value)
        snapshot == snapshotter.snapshot(new Bean())
        snapshot != snapshotter.snapshot(new Bean(prop: "value2"))
    }

    def "creates snapshot for string from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot("abc")
        snapshotter.snapshot("abc", snapshot).is(snapshot)

        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot("other", snapshot) == snapshotter.snapshot("other")

        snapshotter.snapshot(null, snapshot) != snapshot
        snapshotter.snapshot(null, snapshot) == snapshotter.snapshot(null)

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())
    }

    def "creates snapshot for integer from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(123)
        snapshotter.snapshot(123, snapshot).is(snapshot)

        snapshotter.snapshot(-12, snapshot) != snapshot
        snapshotter.snapshot(-12, snapshot) == snapshotter.snapshot(-12)

        snapshotter.snapshot(null, snapshot) != snapshot
        snapshotter.snapshot(null, snapshot) == snapshotter.snapshot(null)

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())
    }

    def "creates snapshot for file from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(new File("abc"))
        snapshotter.snapshot(new File("abc"), snapshot).is(snapshot)

        snapshotter.snapshot(new File("other"), snapshot) != snapshot
        snapshotter.snapshot(new File("other"), snapshot) == snapshotter.snapshot(new File("other"))

        snapshotter.snapshot("abc", snapshot) != snapshot
        snapshotter.snapshot("abc", snapshot) == snapshotter.snapshot("abc")
    }

    def "creates snapshot for enum from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(Type1.TWO)
        snapshotter.snapshot(Type1.TWO, snapshot).is(snapshot)

        snapshotter.snapshot(Type1.ONE, snapshot) != snapshot
        snapshotter.snapshot(Type1.ONE, snapshot) == snapshotter.snapshot(Type1.ONE)

        snapshotter.snapshot(Type2.TWO, snapshot) != snapshot
        snapshotter.snapshot(Type2.TWO, snapshot) == snapshotter.snapshot(Type2.TWO)

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())
    }

    def "creates snapshot for null from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(null)
        snapshotter.snapshot(null, snapshot).is(snapshot)

        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot("other", snapshot) == snapshotter.snapshot("other")

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())
    }

    def "creates snapshot for boolean from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(true)
        snapshotter.snapshot(true, snapshot).is(snapshot)

        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot("other", snapshot) == snapshotter.snapshot("other")

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())
    }

    def "creates snapshot for list from candidate"() {
        expect:
        def snapshot1 = snapshotter.snapshot([])
        snapshotter.snapshot([], snapshot1).is(snapshot1)

        snapshotter.snapshot(["123"], snapshot1) != snapshot1
        snapshotter.snapshot("other", snapshot1) != snapshot1
        snapshotter.snapshot(new Bean(), snapshot1) != snapshot1

        def snapshot2 = snapshotter.snapshot(["123"])
        snapshotter.snapshot(["123"], snapshot2).is(snapshot2)

        snapshotter.snapshot(["456"], snapshot2) != snapshot2
        snapshotter.snapshot([], snapshot2) != snapshot2
        snapshotter.snapshot(["123", "456"], snapshot2) != snapshot2

        def snapshot3 = snapshotter.snapshot([new Bean(prop: "value")])
        snapshotter.snapshot([new Bean(prop: "value")], snapshot3).is(snapshot3)

        snapshotter.snapshot([new Bean(prop: "value 2")], snapshot3) != snapshot3
        snapshotter.snapshot([], snapshot3) != snapshot3
        snapshotter.snapshot([new Bean(prop: "value"), new Bean(prop: "value")], snapshot3) != snapshot3

        def snapshot4 = snapshotter.snapshot([new Bean(prop: "value1"), new Bean(prop: "value2")])
        snapshotter.snapshot([new Bean(prop: "value1"), new Bean(prop: "value2")], snapshot4).is(snapshot4)

        snapshotter.snapshot([new Bean(prop: "value1"), new Bean(prop: "value3")], snapshot4) != snapshot4
    }

    def "creates snapshot for set from candidates"() {
        expect:
        def snapshot1 = snapshotter.snapshot([] as Set)
        snapshotter.snapshot([] as Set, snapshot1).is(snapshot1)

        snapshotter.snapshot(["123"] as Set, snapshot1) != snapshot1
        snapshotter.snapshot("other", snapshot1) != snapshot1
        snapshotter.snapshot(new Bean(), snapshot1) != snapshot1

        def snapshot2 = snapshotter.snapshot(["123"] as Set)
        snapshotter.snapshot(["123"] as Set, snapshot2).is(snapshot2)

        snapshotter.snapshot(["123"], snapshot2) != snapshot2
        snapshotter.snapshot(["456"] as Set, snapshot2) != snapshot2
        snapshotter.snapshot([] as Set, snapshot2) != snapshot2
        snapshotter.snapshot(["123", "456"] as Set, snapshot2) != snapshot2

        def snapshot3 = snapshotter.snapshot([new Bean(prop: "value")] as Set)
        snapshotter.snapshot([new Bean(prop: "value")] as Set, snapshot3).is(snapshot3)

        snapshotter.snapshot([new Bean(prop: "value 2")] as Set, snapshot3) != snapshot3
        snapshotter.snapshot([] as Set, snapshot3) != snapshot3
        snapshotter.snapshot([new Bean(prop: "value 2"), new Bean(prop: "value")] as Set, snapshot3) != snapshot3
    }

    def "creates snapshot for map from candidate"() {
        def map1 = [:]
        map1.put(new Bean(prop: "value"), new Bean(prop: "value"))
        def map2 = [:]
        map2.put(new Bean(prop: "value"), new Bean(prop: "value2"))
        def map3 = [:]
        map3.putAll(map1)
        map3.put(new Bean(prop: "value2"), new Bean(prop: "value2"))

        expect:
        def snapshot1 = snapshotter.snapshot([:])
        snapshotter.snapshot([:], snapshot1).is(snapshot1)

        snapshotter.snapshot([12: "123"], snapshot1) != snapshot1

        snapshotter.snapshot("other", snapshot1) != snapshot1
        snapshotter.snapshot("other", snapshot1) == snapshotter.snapshot("other")
        snapshotter.snapshot(new Bean(), snapshot1) != snapshot1
        snapshotter.snapshot(new Bean(), snapshot1) == snapshotter.snapshot(new Bean())

        def snapshot2 = snapshotter.snapshot([12: "123"])
        snapshotter.snapshot([12: "123"], snapshot2).is(snapshot2)

        snapshotter.snapshot([12: "456"], snapshot2) != snapshot2
        snapshotter.snapshot([:], snapshot2) != snapshot2
        snapshotter.snapshot([123: "123"], snapshot2) != snapshot2
        snapshotter.snapshot([12: "123", 10: "123"], snapshot2) != snapshot2

        def snapshot3 = snapshotter.snapshot([a: new Bean(prop: "value")])
        snapshotter.snapshot([a: new Bean(prop: "value")], snapshot3).is(snapshot3)

        snapshotter.snapshot([a: new Bean(prop: "value 2")], snapshot3) != snapshot3
        snapshotter.snapshot([:], snapshot3) != snapshot3
        snapshotter.snapshot(map1, snapshot3) != snapshot3

        def snapshot4 = snapshotter.snapshot(map1)
        snapshotter.snapshot(map1, snapshot4).is(snapshot4)

        snapshotter.snapshot(map2, snapshot4) != snapshot4
        snapshotter.snapshot(map2, snapshot4) == snapshotter.snapshot(map2)
        snapshotter.snapshot(map3, snapshot4) != snapshot4
        snapshotter.snapshot(map3, snapshot4) == snapshotter.snapshot(map3)
    }

    def "creates snapshot for serializable type from candidate"() {
        expect:
        def snapshot = snapshotter.snapshot(new Bean(prop: "value"))
        snapshotter.snapshot(new Bean(prop: "value"), snapshot).is(snapshot)

        snapshotter.snapshot(new Bean(), snapshot) != snapshot
        snapshotter.snapshot(new Bean(), snapshot) == snapshotter.snapshot(new Bean())

        snapshotter.snapshot("other", snapshot) != snapshot
        snapshotter.snapshot("other", snapshot) == snapshotter.snapshot("other")
    }

    static class Bean implements Serializable {
        String prop
    }
}
