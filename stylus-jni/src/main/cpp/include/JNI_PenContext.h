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

#ifndef JNI_PEN_CONTEXT_H
#define JNI_PEN_CONTEXT_H

#include "JavaContext.h"
#include "JNI_PenEventCallback.h"
#include "StylusManager.h"

#include <jni.h>
#include <map>
#include <memory>

namespace stylus
{
	class JNI_PenContext : public jni::JavaContext
	{
		public:
			JNI_PenContext(JavaVM * vm);
			~JNI_PenContext() = default;

			StylusManager * getStylusManager();

			void initialize(JNIEnv * env) override;
			void destroy(JNIEnv * env) override;

		public:
			std::map<jlong, PJNI_PenEventCallback> callbacks;

		private:
			std::unique_ptr<StylusManager> manager;
	};
}

#endif
