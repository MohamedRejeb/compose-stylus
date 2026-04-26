/*
 * Copyright 2016 Alex Andres
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

#ifndef JNI_PEN_H_
#define JNI_PEN_H_

// Java/Kotlin package containing the public Pen* API. Used to build FQNs for
// FindClass / GetMethodID lookups across the JNI bridge.
#define PKG "com/mohamedrejeb/stylus/"

#define STRING_SIG "Ljava/lang/String;"

namespace stylus
{
	/**
	 * Mirrors the Java enum `com.mohamedrejeb.stylus.PenEventType` (Hover, Move,
	 * Press, Release). The native bridge maps each dispatch site (cursor change /
	 * cursor move / button down / button up) to a value of this enum and converts
	 * it to a Java enum via [JavaEnums::toJava] before constructing a `PenEvent`.
	 *
	 * Order **must** match the Java enum's declaration order; see PenEnumsTest.
	 */
	enum class PenEventTypeOrdinal : unsigned
	{
		HOVER,
		MOVE,
		PRESS,
		RELEASE,
	};
}

#endif
