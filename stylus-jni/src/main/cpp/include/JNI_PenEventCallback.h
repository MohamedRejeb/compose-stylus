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

#ifndef JNI_PEN_EVENT_CALLBACK_H
#define JNI_PEN_EVENT_CALLBACK_H

#include "JNI_Pen.h"
#include "StylusListener.h"
#include "JavaClass.h"
#include "JavaRef.h"

#include <jni.h>

namespace stylus
{
	/**
	 * Bridges the C++ `StylusListener` (4 virtual methods, called from each
	 * platform's input pump) to the Java `com.mohamedrejeb.stylus.PenEventCallback`
	 * single-method interface. Each C++ dispatch site is mapped to a
	 * [PenEventTypeOrdinal] value carried on the constructed `PenEvent`.
	 */
	class JNI_PenEventCallback : public StylusListener
	{
		public:
			JNI_PenEventCallback(JNIEnv * env, const jni::JavaGlobalRef<jobject> & callback);
			~JNI_PenEventCallback() = default;

			const jboolean & isEnabled();
			void setEnabled(jboolean enabled);

			void onCursorChange(PStylusEvent event) override;
			void onCursorMove(PStylusEvent event) override;
			void onButtonDown(PStylusEvent event) override;
			void onButtonUp(PStylusEvent event) override;

		private:
			void dispatch(PenEventTypeOrdinal type, PStylusEvent event);

			class JavaPenEventCallbackClass : public jni::JavaClass
			{
				public:
					explicit JavaPenEventCallbackClass(JNIEnv * env);

					jmethodID onEvent;
			};

			jni::JavaGlobalRef<jobject> jCallback;

			/* Indicates whether the callback should receive pen events. */
			jboolean enabled;

			const std::shared_ptr<JavaPenEventCallbackClass> javaClass;
	};


	using PJNI_PenEventCallback = std::shared_ptr<JNI_PenEventCallback>;
}

#endif
