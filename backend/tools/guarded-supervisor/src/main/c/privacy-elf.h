#ifndef ES_PRIVACY_ELF_H
#define ES_PRIVACY_ELF_H
#include "privacy-file.h"
#include "privacy-hash.h"
typedef enum { ES_ELF_OK=0, ES_ELF_INVALID=1, ES_ELF_PLATFORM=2,
 ES_ELF_CANCELLED=3, ES_ELF_DEADLINE=4, ES_ELF_IDENTITY=5,
 ES_ELF_RESOURCE=6, ES_ELF_IO=7, ES_ELF_CLEANUP=8, ES_ELF_FORMAT=9 } es_elf_result;
typedef struct {uint32_t type,flags;uint64_t offset,vaddr,paddr,filesz,memsz,align;} es_elf_program;
typedef struct {
 es_hash_identity file;unsigned char ident[16];uint16_t type,machine;
 uint32_t version,flags;uint64_t entry,phoff,shoff;
 uint16_t ehsize,phentsize,phnum,shentsize,shnum,shstrndx;
 uint32_t load_count;es_elf_program programs[128];
} es_elf_layout;
/* Structural file metadata only; never mapped ELF, loader or runtime admission.
   Borrowed stable caller-serialized owners share original controls. No descriptor
   or owner transfers. Distinct safe output is zero on refusal; aliases preserve
   owners/inputs. Two shared full hashes consume original budgets. No atomicity
   or ABA protection. Caller latches failure, including sticky hash uncertainty. */
es_elf_result es_elf_check(const es_file *,es_hash *,const unsigned char expected[32],es_elf_layout *);
#endif
