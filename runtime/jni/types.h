#ifndef TYPES_H
#define TYPES_H

#include <stdint.h>

extern "C" {

// List of interfaces implemented by a Java class.
class it {
public:
    it* next;
    char* interface_name;
};

// Dispatch vector for a Java class.
class dv {
public:
    it* it;
    void* type_info;
};

// Header of a Java object instance.
class jobject {
public:
    dv* dv;
};

class jarray : public jobject {
public:
    int32_t len;
    intptr_t data;
};

class jstring : public jobject {
public:
    jarray* chars;
};

using jbool = bool;
using jint = int32_t;
using jlong = int64_t;
using jfloat = float;
using jdouble = double;

} // extern "C"

#endif // TYPES_H
