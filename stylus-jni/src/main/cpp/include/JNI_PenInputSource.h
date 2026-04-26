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

#include <jni.h>
/* Header for class com_mohamedrejeb_stylus_PenInputSource */

#ifndef _Included_com_mohamedrejeb_stylus_PenInputSource
#define _Included_com_mohamedrejeb_stylus_PenInputSource
#ifdef __cplusplus
extern "C" {
#endif

	/*
	 * Class:     com_mohamedrejeb_stylus_PenInputSource
	 * Method:    destroy
	 * Signature: ()V
	 */
	JNIEXPORT void JNICALL Java_com_mohamedrejeb_stylus_PenInputSource_destroy
		(JNIEnv *, jobject);

	/*
	 * Class:     com_mohamedrejeb_stylus_PenInputSource
	 * Method:    attachCallback
	 * Signature: (Lcom/mohamedrejeb/stylus/PenEventCallback;Ljava/lang/Object;)V
	 */
	JNIEXPORT void JNICALL Java_com_mohamedrejeb_stylus_PenInputSource_attachCallback
		(JNIEnv *, jobject, jobject, jobject);

	/*
	 * Class:     com_mohamedrejeb_stylus_PenInputSource
	 * Method:    detachCallback
	 * Signature: (Lcom/mohamedrejeb/stylus/PenEventCallback;Ljava/lang/Object;)V
	 */
	JNIEXPORT void JNICALL Java_com_mohamedrejeb_stylus_PenInputSource_detachCallback
		(JNIEnv *, jobject, jobject, jobject);

	/*
	 * Class:     com_mohamedrejeb_stylus_PenInputSource
	 * Method:    setCallbackEnabled
	 * Signature: (Lcom/mohamedrejeb/stylus/PenEventCallback;Z)V
	 */
	JNIEXPORT void JNICALL Java_com_mohamedrejeb_stylus_PenInputSource_setCallbackEnabled
		(JNIEnv *, jobject, jobject, jboolean);

#ifdef __cplusplus
}
#endif
#endif
