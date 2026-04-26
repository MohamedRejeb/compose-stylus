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

#include "JNI_PenEventCallback.h"
#include "JNI_PenContext.h"
#include "JNI_PenEvent.h"
#include "JNI_Pen.h"
#include "JavaUtils.h"

namespace stylus
{
	JNI_PenEventCallback::JNI_PenEventCallback(JNIEnv * env, const jni::JavaGlobalRef<jobject> & callback) :
		jCallback(callback),
		enabled(false),
		javaClass(jni::JavaClasses::get<JavaPenEventCallbackClass>(env))
	{
	}

	const jboolean & JNI_PenEventCallback::isEnabled()
	{
		return enabled;
	}

	void JNI_PenEventCallback::setEnabled(jboolean enabled)
	{
		this->enabled = enabled;
	}

	void JNI_PenEventCallback::onCursorChange(PStylusEvent event)
	{
		dispatch(PenEventTypeOrdinal::HOVER, event);
	}

	void JNI_PenEventCallback::onCursorMove(PStylusEvent event)
	{
		dispatch(PenEventTypeOrdinal::MOVE, event);
	}

	void JNI_PenEventCallback::onButtonDown(PStylusEvent event)
	{
		dispatch(PenEventTypeOrdinal::PRESS, event);
	}

	void JNI_PenEventCallback::onButtonUp(PStylusEvent event)
	{
		dispatch(PenEventTypeOrdinal::RELEASE, event);
	}

	void JNI_PenEventCallback::dispatch(PenEventTypeOrdinal type, PStylusEvent event)
	{
		if (!enabled) {
			return;
		}

		JNIEnv * env = AttachCurrentThread();

		jobject jEvent = JNI_PenEvent::toJava(env, type, event).release();

		env->CallVoidMethod(jCallback, javaClass->onEvent, jEvent);
	}

	JNI_PenEventCallback::JavaPenEventCallbackClass::JavaPenEventCallbackClass(JNIEnv * env)
	{
		jclass cls = FindClass(env, PKG "PenEventCallback");

		onEvent = GetMethod(env, cls, "onEvent", "(L" PKG "PenEvent;)V");
	}
}
