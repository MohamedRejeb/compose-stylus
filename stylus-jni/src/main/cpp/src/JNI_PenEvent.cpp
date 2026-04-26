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

#include "JNI_PenEvent.h"
#include "JNI_Pen.h"
#include "JavaClasses.h"
#include "JavaEnums.h"

namespace stylus
{
	namespace JNI_PenEvent
	{
		jni::JavaLocalRef<jobject> toJava(JNIEnv * env, PenEventTypeOrdinal type, const PStylusEvent & event)
		{
			const auto javaClass = jni::JavaClasses::get<JavaPenEventClass>(env);

			const StylusAxesData & axesData = event->axesData;

			jsize dataSize = static_cast<jsize>(axesData.size());

			jobject jType = jni::JavaEnums::toJava(env, type).release();
			jobject jTool = jni::JavaEnums::toJava(env, event->cursor).release();
			jobject jButton = jni::JavaEnums::toJava(env, event->button).release();

			jdoubleArray jAxesData = env->NewDoubleArray(dataSize);

			if (jAxesData != nullptr) {
				env->SetDoubleArrayRegion(jAxesData, 0, dataSize, axesData.getData());
			}

			jobject jEvent = env->NewObject(javaClass->cls, javaClass->ctor, jType, jTool, jButton, jAxesData);

			return jni::JavaLocalRef<jobject>(env, jEvent);
		}

		JavaPenEventClass::JavaPenEventClass(JNIEnv * env)
		{
			cls = FindClass(env, PKG "PenEvent");

			ctor = GetMethod(env, cls, "<init>",
				"(L" PKG "PenEventType;L" PKG "PenTool;L" PKG "PenButton;[D)V");
		}
	}
}
