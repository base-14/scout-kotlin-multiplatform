// Scout in-process native crash handler. On a fatal signal it writes a crash report (signal,
// registers, unwound PC frames) to disk, plus a separate file of loaded binary images (with ELF
// build-ids, captured at install time — dl_iterate_phdr is not async-signal-safe). Both are read &
// emitted as a `native_crash` on the next launch, giving the backend what it needs to symbolicate.

#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>
#include <unwind.h>
#include <jni.h>
#include <link.h>
#include <elf.h>
#include <ucontext.h>
#include <sys/syscall.h>
#include <dirent.h>

#define SCOUT_NUM_SIGNALS 6
#define SCOUT_MAX_FRAMES 64

static char g_crash_path[1024];
static char g_images_path[1024];
static struct sigaction g_old[SCOUT_NUM_SIGNALS];
static const int g_signals[SCOUT_NUM_SIGNALS] = {SIGSEGV, SIGABRT, SIGBUS, SIGFPE, SIGILL, SIGTRAP};
static volatile sig_atomic_t g_handling = 0;

static const char *scout_signal_name(int sig) {
    switch (sig) {
        case SIGSEGV: return "SIGSEGV";
        case SIGABRT: return "SIGABRT";
        case SIGBUS:  return "SIGBUS";
        case SIGFPE:  return "SIGFPE";
        case SIGILL:  return "SIGILL";
        case SIGTRAP: return "SIGTRAP";
        default:      return "UNKNOWN";
    }
}

// ---- backtrace ----
struct scout_bt_state { int fd; int count; };

static _Unwind_Reason_Code scout_unwind_cb(struct _Unwind_Context *ctx, void *arg) {
    struct scout_bt_state *st = (struct scout_bt_state *) arg;
    uintptr_t pc = _Unwind_GetIP(ctx);
    if (pc != 0) {
        char line[64];
        int n = snprintf(line, sizeof(line), "#%02d pc 0x%lx\n", st->count, (unsigned long) pc);
        if (n > 0) { ssize_t w = write(st->fd, line, (size_t) n); (void) w; }
        st->count++;
        if (st->count >= SCOUT_MAX_FRAMES) return _URC_END_OF_STACK;
    }
    return _URC_NO_REASON;
}

// ---- registers (arch-specific): full GP set, written as space-separated name=0xval pairs ----
static void scout_wreg(int fd, const char *name, unsigned long val) {
    char b[48];
    int n = snprintf(b, sizeof(b), "%s=0x%lx ", name, val);
    if (n > 0) { ssize_t w = write(fd, b, (size_t) n); (void) w; }
}

static void scout_write_registers(int fd, void *uc_void) {
    if (!uc_void) return;
    ucontext_t *uc = (ucontext_t *) uc_void;
    ssize_t w = write(fd, "registers ", 10); (void) w;
#if defined(__aarch64__)
    scout_wreg(fd, "pc", (unsigned long) uc->uc_mcontext.pc);
    scout_wreg(fd, "sp", (unsigned long) uc->uc_mcontext.sp);
    scout_wreg(fd, "lr", (unsigned long) uc->uc_mcontext.regs[30]);
    scout_wreg(fd, "fp", (unsigned long) uc->uc_mcontext.regs[29]);
    char xn[8];
    for (int i = 0; i <= 28; i++) {
        snprintf(xn, sizeof(xn), "x%d", i);
        scout_wreg(fd, xn, (unsigned long) uc->uc_mcontext.regs[i]);
    }
#elif defined(__x86_64__)
    greg_t *g = uc->uc_mcontext.gregs;
    scout_wreg(fd, "rip", (unsigned long) g[REG_RIP]); scout_wreg(fd, "rsp", (unsigned long) g[REG_RSP]);
    scout_wreg(fd, "rbp", (unsigned long) g[REG_RBP]); scout_wreg(fd, "rax", (unsigned long) g[REG_RAX]);
    scout_wreg(fd, "rbx", (unsigned long) g[REG_RBX]); scout_wreg(fd, "rcx", (unsigned long) g[REG_RCX]);
    scout_wreg(fd, "rdx", (unsigned long) g[REG_RDX]); scout_wreg(fd, "rsi", (unsigned long) g[REG_RSI]);
    scout_wreg(fd, "rdi", (unsigned long) g[REG_RDI]); scout_wreg(fd, "r8", (unsigned long) g[REG_R8]);
    scout_wreg(fd, "r9", (unsigned long) g[REG_R9]); scout_wreg(fd, "r10", (unsigned long) g[REG_R10]);
    scout_wreg(fd, "r11", (unsigned long) g[REG_R11]); scout_wreg(fd, "r12", (unsigned long) g[REG_R12]);
    scout_wreg(fd, "r13", (unsigned long) g[REG_R13]); scout_wreg(fd, "r14", (unsigned long) g[REG_R14]);
    scout_wreg(fd, "r15", (unsigned long) g[REG_R15]);
#elif defined(__arm__)
    scout_wreg(fd, "pc", (unsigned long) uc->uc_mcontext.arm_pc); scout_wreg(fd, "sp", (unsigned long) uc->uc_mcontext.arm_sp);
    scout_wreg(fd, "lr", (unsigned long) uc->uc_mcontext.arm_lr); scout_wreg(fd, "ip", (unsigned long) uc->uc_mcontext.arm_ip);
    scout_wreg(fd, "fp", (unsigned long) uc->uc_mcontext.arm_fp);
    scout_wreg(fd, "r0", (unsigned long) uc->uc_mcontext.arm_r0); scout_wreg(fd, "r1", (unsigned long) uc->uc_mcontext.arm_r1);
    scout_wreg(fd, "r2", (unsigned long) uc->uc_mcontext.arm_r2); scout_wreg(fd, "r3", (unsigned long) uc->uc_mcontext.arm_r3);
    scout_wreg(fd, "r4", (unsigned long) uc->uc_mcontext.arm_r4); scout_wreg(fd, "r5", (unsigned long) uc->uc_mcontext.arm_r5);
    scout_wreg(fd, "r6", (unsigned long) uc->uc_mcontext.arm_r6); scout_wreg(fd, "r7", (unsigned long) uc->uc_mcontext.arm_r7);
    scout_wreg(fd, "r8", (unsigned long) uc->uc_mcontext.arm_r8); scout_wreg(fd, "r9", (unsigned long) uc->uc_mcontext.arm_r9);
    scout_wreg(fd, "r10", (unsigned long) uc->uc_mcontext.arm_r10);
#elif defined(__i386__)
    greg_t *g = uc->uc_mcontext.gregs;
    scout_wreg(fd, "eip", (unsigned long) g[REG_EIP]); scout_wreg(fd, "esp", (unsigned long) g[REG_ESP]);
    scout_wreg(fd, "ebp", (unsigned long) g[REG_EBP]); scout_wreg(fd, "eax", (unsigned long) g[REG_EAX]);
    scout_wreg(fd, "ebx", (unsigned long) g[REG_EBX]); scout_wreg(fd, "ecx", (unsigned long) g[REG_ECX]);
    scout_wreg(fd, "edx", (unsigned long) g[REG_EDX]); scout_wreg(fd, "esi", (unsigned long) g[REG_ESI]);
    scout_wreg(fd, "edi", (unsigned long) g[REG_EDI]);
#endif
    w = write(fd, "\n", 1); (void) w;
}

// ---- thread count: count /proc/self/task entries (async-signal-safe via getdents64) ----
static int scout_thread_count(void) {
    int fd = open("/proc/self/task", O_RDONLY | O_DIRECTORY);
    if (fd < 0) return 0;
    int count = 0;
    char buf[4096];
    for (;;) {
        long nread = syscall(SYS_getdents64, fd, buf, sizeof(buf));
        if (nread <= 0) break;
        long pos = 0;
        while (pos < nread) {
            struct dirent64 *d = (struct dirent64 *) (buf + pos);
            if (d->d_name[0] != '.') count++; // task dirs are numeric; '.'/'..' skipped
            pos += d->d_reclen;
        }
    }
    close(fd);
    return count;
}

// ---- memory map: copy /proc/self/maps into a delimited block (capped) ----
static void scout_write_maps(int fd) {
    int mfd = open("/proc/self/maps", O_RDONLY);
    if (mfd < 0) return;
    ssize_t w = write(fd, "maps_begin\n", 11); (void) w;
    char buf[4096];
    long total = 0;
    for (;;) {
        ssize_t r = read(mfd, buf, sizeof(buf));
        if (r <= 0) break;
        if (total + r > 131072) { r = (ssize_t) (131072 - total); if (r <= 0) break; }
        w = write(fd, buf, (size_t) r);
        total += r;
        if (total >= 131072) break;
    }
    w = write(fd, "\nmaps_end\n", 10); (void) w;
    close(mfd);
}

// ---- binary images (captured at install: not async-signal-safe) ----
static void scout_hex(const unsigned char *bytes, size_t len, char *out, size_t out_sz) {
    static const char *hx = "0123456789abcdef";
    size_t j = 0;
    for (size_t i = 0; i < len && j + 2 < out_sz; i++) {
        out[j++] = hx[(bytes[i] >> 4) & 0xF];
        out[j++] = hx[bytes[i] & 0xF];
    }
    out[j] = '\0';
}

static int scout_image_cb(struct dl_phdr_info *info, size_t size, void *data) {
    (void) size;
    int fd = *(int *) data;
    char build_id[64] = "";
    for (int i = 0; i < info->dlpi_phnum; i++) {
        const ElfW(Phdr) *ph = &info->dlpi_phdr[i];
        if (ph->p_type != PT_NOTE) continue;
        const char *p = (const char *) (info->dlpi_addr + ph->p_vaddr);
        const char *end = p + ph->p_memsz;
        while (p + sizeof(ElfW(Nhdr)) <= end) {
            const ElfW(Nhdr) *nh = (const ElfW(Nhdr) *) p;
            const char *name = p + sizeof(ElfW(Nhdr));
            const unsigned char *desc = (const unsigned char *) (name + ((nh->n_namesz + 3) & ~3));
            if (nh->n_type == NT_GNU_BUILD_ID && nh->n_namesz == 4 && memcmp(name, "GNU", 3) == 0) {
                scout_hex(desc, nh->n_descsz, build_id, sizeof(build_id));
                break;
            }
            p = (const char *) desc + ((nh->n_descsz + 3) & ~3);
        }
        if (build_id[0]) break;
    }
    const char *name = (info->dlpi_name && info->dlpi_name[0]) ? info->dlpi_name : "app_process";
    char line[1100];
    int n = snprintf(line, sizeof(line), "%s 0x%lx %s\n", name,
                     (unsigned long) info->dlpi_addr, build_id);
    if (n > 0) { ssize_t w = write(fd, line, (size_t) n); (void) w; }
    return 0;
}

static void scout_capture_images(void) {
    int fd = open(g_images_path, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) return;
    dl_iterate_phdr(scout_image_cb, &fd);
    close(fd);
}

// ---- signal handler ----
static void scout_handler(int sig, siginfo_t *info, void *uc) {
    if (g_handling) _exit(1);
    g_handling = 1;

    int fd = open(g_crash_path, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd >= 0) {
        char header[256];
        int n = snprintf(header, sizeof(header), "signal %d %s code %d fault_addr 0x%lx\n",
                         sig, scout_signal_name(sig), info ? info->si_code : 0,
                         (unsigned long) (info ? (uintptr_t) info->si_addr : 0));
        if (n > 0) { ssize_t w = write(fd, header, (size_t) n); (void) w; }
        char tc[48];
        int tn = snprintf(tc, sizeof(tc), "thread_count %d\n", scout_thread_count());
        if (tn > 0) { ssize_t w = write(fd, tc, (size_t) tn); (void) w; }
        scout_write_registers(fd, uc);
        struct scout_bt_state st = {fd, 0};
        _Unwind_Backtrace(scout_unwind_cb, &st);
        scout_write_maps(fd);
        close(fd);
    }

    for (int i = 0; i < SCOUT_NUM_SIGNALS; i++) sigaction(g_signals[i], &g_old[i], NULL);
    raise(sig);
}

JNIEXPORT void JNICALL
Java_io_base14_scout_android_instrumentation_NativeCrashHandler_nativeInstall(JNIEnv *env, jobject thiz,
                                                                              jstring crashPath,
                                                                              jstring imagesPath) {
    (void) thiz;
    const char *cp = (*env)->GetStringUTFChars(env, crashPath, NULL);
    if (cp) { strncpy(g_crash_path, cp, sizeof(g_crash_path) - 1); (*env)->ReleaseStringUTFChars(env, crashPath, cp); }
    const char *ip = (*env)->GetStringUTFChars(env, imagesPath, NULL);
    if (ip) { strncpy(g_images_path, ip, sizeof(g_images_path) - 1); (*env)->ReleaseStringUTFChars(env, imagesPath, ip); }

    scout_capture_images();

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = scout_handler;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);
    for (int i = 0; i < SCOUT_NUM_SIGNALS; i++) sigaction(g_signals[i], &sa, &g_old[i]);
}
