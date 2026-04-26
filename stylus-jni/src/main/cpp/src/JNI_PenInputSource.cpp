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

#include "JNI_Pen.h"
#include "JNI_PenContext.h"
#include "JNI_PenInputSource.h"
#include "JNI_PenEventCallback.h"
#include "JavaContext.h"
#include "JavaUtils.h"
#include "JavaRef.h"
#include "JavaRuntimeException.h"
#include "StylusException.h"

#include <fstream>
#include <iostream>
#include <stdio.h>
#include <memory>


using namespace stylus;


JNIEXPORT void JNICALL Java_com_mohamedrejeb_stylus_PenInputSource_destroy
(JNIEnv * env, jobject caller)
{
	JNI_PenContext * context = static_cast<JNI_PenContext *>(javaContext);
	StylusManager * manager = context->getStylusManager();

	try {
		context->callbacks.clear();

		delete manager;
	}
	catch (StylusException & ex) {
		env->Throw(jni::JavaRuntimeException(env, ex.what()));
	}
	catch (...) {
		ThrowCxxJavaException(env);
	}
}

JNIEXPORT void JNICALL Java_com_mohamedrejeb_stylus_PenInputSource_attachCallback
(JNIEnv * env, jobject caller, jobject callback, jobject window)
{
	JNI_PenContext * context = static_cast<JNI_PenContext *>(javaContext);
	StylusManager * manager = context->getStylusManager();

	try {
		jni::JavaGlobalRef<jobject> callbackRef = jni::JavaGlobalRef<jobject>(env, callback);

		// Connect the Java callback with native listener implementation.
		PJNI_PenEventCallback penCallback = std::make_shared<JNI_PenEventCallback>(env, callbackRef);

		// Keep a reference to the callback.
		SetHandle(env, callback, penCallback.get());

		manager->attachStylusListener(jni::JavaLocalRef<jobject>(env, window), penCallback);

		jlong ptr = GetHandleLong<StylusManager>(env, callback);
		context->callbacks[ptr] = penCallback;
	}
	catch (StylusException & ex) {
		env->Throw(jni::JavaRuntimeException(env, ex.what()));
	}
	catch (...) {
		ThrowCxxJavaException(env);
	}
}

JNIEXPORT void JNICALL Java_com_mohamedrejeb_stylus_PenInputSource_detachCallback
(JNIEnv * env, jobject caller, jobject callback, jobject window)
{
	JNI_PenContext * context = static_cast<JNI_PenContext *>(javaContext);
	StylusManager * manager = context->getStylusManager();

	try {
		jlong ptr = GetHandleLong<StylusManager>(env, callback);
		auto found = context->callbacks.find(ptr);

		if (found != context->callbacks.end()) {
			PJNI_PenEventCallback penCallback = found->second;
			manager->detachStylusListener(jni::JavaLocalRef<jobject>(env, window), penCallback);
			context->callbacks.erase(found);
		}
	}
	catch (StylusException & ex) {
		env->Throw(jni::JavaRuntimeException(env, ex.what()));
	}
	catch (...) {
		ThrowCxxJavaException(env);
	}
}

JNIEXPORT void JNICALL Java_com_mohamedrejeb_stylus_PenInputSource_setCallbackEnabled
(JNIEnv * env, jobject caller, jobject callback, jboolean enable)
{
	JNI_PenContext * context = static_cast<JNI_PenContext *>(javaContext);

	try {
		jlong ptr = GetHandleLong<StylusManager>(env, callback);
		auto found = context->callbacks.find(ptr);

		if (found != context->callbacks.end()) {
			PJNI_PenEventCallback penCallback = found->second;
			penCallback->setEnabled(enable);
		}
	}
	catch (StylusException & ex) {
		env->Throw(jni::JavaRuntimeException(env, ex.what()));
	}
	catch (...) {
		ThrowCxxJavaException(env);
	}
}
