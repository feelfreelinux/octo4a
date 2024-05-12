#ifndef _UTIL_H_
#define _UTIL_H_

#ifdef DEBUG_TRACE
void _trace(const char* format, ...);
#endif

#ifdef DEBUG_TRACE
#define TRACE(X) _trace X;
#else /*DEBUG_TRACE*/
#define TRACE(X)
#endif /*DEBUG_TRACE*/

#endif //_UTIL_H_