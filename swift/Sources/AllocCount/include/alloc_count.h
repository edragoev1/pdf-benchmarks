#ifndef ALLOC_COUNT_H
#define ALLOC_COUNT_H

#include <stdint.h>

// The bytes the calling thread has asked malloc for since it started, as
// Java's ThreadMXBean and .NET's GC.GetAllocatedBytesForCurrentThread count
// them for their own heaps.
uint64_t alloc_count_thread_bytes(void);

#endif
