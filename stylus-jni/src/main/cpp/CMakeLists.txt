cmake_minimum_required(VERSION 3.13)
project(stylus)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(UNIX AND NOT APPLE)
	set(LINUX TRUE)
endif()

if(APPLE)
    set(CMAKE_OSX_DEPLOYMENT_TARGET "12.0" CACHE STRING "Minimum OS X deployment version" FORCE)
	# Build for the host architecture (arm64 on Apple Silicon, x86_64 on Intel).
	# Override with -DCMAKE_OSX_ARCHITECTURES=arm64;x86_64 for a universal binary.
	if(NOT CMAKE_OSX_ARCHITECTURES)
		set(CMAKE_OSX_ARCHITECTURES "${CMAKE_HOST_SYSTEM_PROCESSOR}")
	endif()
	set(CMAKE_CXX_VISIBILITY_PRESET hidden)
	set(STYLUS_OS macos)
	add_subdirectory(dependencies/${STYLUS_OS}/cocoa)
elseif(LINUX)
	set(CMAKE_POSITION_INDEPENDENT_CODE ON)
	set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -s")
	set(STYLUS_OS linux)
	add_subdirectory(dependencies/${STYLUS_OS}/x11)
elseif(WIN32)
	set(CMAKE_CXX_FLAGS_RELEASE "/MT")
	set(CMAKE_CXX_FLAGS_DEBUG "/MTd")
	set(STYLUS_OS windows)
	add_subdirectory(dependencies/${STYLUS_OS}/rts)
endif()

add_subdirectory(dependencies/jni-voithos)
add_subdirectory(dependencies/core)

# Find the JNI package
find_package(JNI)

get_target_property(JNI_INCLUDES jni-voithos INCLUDE_DIRECTORIES)
get_target_property(CORE_INCLUDES stylus-core INCLUDE_DIRECTORIES)

set_property(
	TARGET stylus-core
	APPEND PROPERTY INCLUDE_DIRECTORIES ${JNI_INCLUDES}
)

add_library(${PROJECT_NAME} SHARED)

target_sources(${PROJECT_NAME}
	INTERFACE
		include/JNI_Pen.h
		include/JNI_PenContext.h
		include/JNI_PenEvent.h
		include/JNI_PenEventCallback.h
		include/JNI_PenInputSource.h
	PRIVATE
		src/JNI_Pen.cpp
		src/JNI_PenContext.cpp
		src/JNI_PenEvent.cpp
		src/JNI_PenEventCallback.cpp
		src/JNI_PenInputSource.cpp
)

target_include_directories(${PROJECT_NAME} PRIVATE
	include
)

if(APPLE)
	target_compile_options(${PROJECT_NAME} PRIVATE -x objective-c++)
	# JavaNativeFoundation was removed from macOS in Big Sur+; we don't use any
	# JNF_* APIs in this codebase, so link against the standard system frameworks only.
	set(STYLUS_LINK_LIBS -ObjC "-framework AppKit" "-framework Cocoa" "-framework IOKit" "-framework QuartzCore")
	set(PLATFORM_LIB stylus-cocoa)
elseif(LINUX)
	target_link_directories(${PROJECT_NAME} PRIVATE dependencies/${STYLUS_OS}/lib/x86-64)
	set(STYLUS_LINK_LIBS X11 Xi Xext -static-libgcc -static-libstdc++)
	set(PLATFORM_LIB stylus-x11)
elseif(WIN32)
	target_link_directories(${PROJECT_NAME} PRIVATE dependencies/${STYLUS_OS}/lib/x86-64)
	set(STYLUS_LINK_LIBS setupapi)
	set(PLATFORM_LIB stylus-rts)
endif()

set_property(
	TARGET ${PLATFORM_LIB}
	APPEND PROPERTY INCLUDE_DIRECTORIES ${JNI_INCLUDES} ${CORE_INCLUDES}
)

target_link_libraries(${PROJECT_NAME} ${JNI_LIBRARIES} ${PLATFORM_LIB} ${STYLUS_LINK_LIBS} jni-voithos stylus-core)

if (JNI_FOUND)
	target_link_libraries(${PROJECT_NAME} ${JNI_LIBRARIES})
	message("${JNI_LIBRARIES}")
endif()

install(TARGETS ${PROJECT_NAME}
	RUNTIME DESTINATION ${CMAKE_INSTALL_PREFIX} COMPONENT Runtime
	LIBRARY DESTINATION ${CMAKE_INSTALL_PREFIX} COMPONENT Runtime
)
