// Counts the bytes each thread allocates, for the memory allocated per
// document: Swift on Linux has no counter of its own. The functions below
// replace glibc's malloc in this program, as glibc documents ("Replacing
// malloc"), add the size asked for to the thread's count, and call glibc's
// own allocator, so the memory itself is handled as before.
#include <errno.h>
#include <stddef.h>
#include <stdint.h>
#include "alloc_count.h"

extern void *__libc_malloc(size_t size);
extern void *__libc_calloc(size_t n, size_t size);
extern void *__libc_realloc(void *p, size_t size);
extern void *__libc_memalign(size_t alignment, size_t size);
extern void __libc_free(void *p);

static __thread uint64_t allocated;

uint64_t alloc_count_thread_bytes(void) {
    return allocated;
}

void *malloc(size_t size) {
    allocated += size;
    return __libc_malloc(size);
}

void *calloc(size_t n, size_t size) {
    allocated += n * size;
    return __libc_calloc(n, size);
}

// A realloc counts its new size, as a block moved or grown is new memory
void *realloc(void *p, size_t size) {
    allocated += size;
    return __libc_realloc(p, size);
}

void free(void *p) {
    __libc_free(p);
}

void *memalign(size_t alignment, size_t size) {
    allocated += size;
    return __libc_memalign(alignment, size);
}

void *aligned_alloc(size_t alignment, size_t size) {
    allocated += size;
    return __libc_memalign(alignment, size);
}

int posix_memalign(void **out, size_t alignment, size_t size) {
    if (alignment < sizeof(void *) || (alignment & (alignment - 1)) != 0) {
        return EINVAL;
    }
    void *p = __libc_memalign(alignment, size);
    if (p == NULL && size != 0) {
        return ENOMEM;
    }
    allocated += size;
    *out = p;
    return 0;
}
