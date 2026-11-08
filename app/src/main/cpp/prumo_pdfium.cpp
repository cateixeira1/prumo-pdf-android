// Ligação entre o Kotlin (br.com.prumopdf.pdf.PdfiumNative) e o PDFium, o mesmo motor de PDF do Prumo
// para Windows. O PDFium não pode ser usado por duas linhas de execução ao mesmo tempo: o lado Kotlin
// chama tudo daqui numa linha de execução só (Pdfium.dispatcher).

#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "fpdfview.h"
#include "fpdf_edit.h"
#include "fpdf_ppo.h"
#include "fpdf_save.h"
#include "fpdf_text.h"

#define LOG_TAG "PrumoPdfium"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

bool g_ready = false;

FPDF_DOCUMENT doc_of(jlong h) { return reinterpret_cast<FPDF_DOCUMENT>(h); }

std::string to_utf8(JNIEnv* env, jstring s) {
    if (!s) return std::string();
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

// Página carregada só durante uma chamada.
struct Page {
    FPDF_PAGE p;
    Page(FPDF_DOCUMENT d, int i) : p(FPDF_LoadPage(d, i)) {}
    ~Page() { if (p) FPDF_ClosePage(p); }
};

struct TextPage {
    FPDF_TEXTPAGE t;
    explicit TextPage(FPDF_PAGE p) : t(p ? FPDFText_LoadPage(p) : nullptr) {}
    ~TextPage() { if (t) FPDFText_ClosePage(t); }
};

// Gravação do PDF num arquivo comum.
struct FileWriter : FPDF_FILEWRITE {
    FILE* f;
    bool ok = true;
    explicit FileWriter(FILE* file) : f(file) {
        version = 1;
        WriteBlock = &FileWriter::write;
    }
    static int write(FPDF_FILEWRITE* self, const void* data, unsigned long size) {
        auto* w = static_cast<FileWriter*>(self);
        if (size == 0) return 1;
        if (fwrite(data, 1, size, w->f) != size) {
            w->ok = false;
            return 0;
        }
        return 1;
    }
};

long long largest_image(FPDF_PAGEOBJECT obj, FPDF_PAGE page, int depth) {
    if (!obj || depth > 4) return 0;
    int type = FPDFPageObj_GetType(obj);
    if (type == FPDF_PAGEOBJ_IMAGE) {
        unsigned int w = 0, h = 0;
        if (FPDFImageObj_GetImagePixelSize(obj, &w, &h)) return static_cast<long long>(w) * h;
        return 0;
    }
    if (type == FPDF_PAGEOBJ_FORM) {
        long long best = 0;
        int n = FPDFFormObj_CountObjects(obj);
        for (int i = 0; i < n; i++) best = std::max(best, largest_image(FPDFFormObj_GetObject(obj, i), page, depth + 1));
        return best;
    }
    return 0;
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_init(JNIEnv*, jobject) {
    if (g_ready) return;
    FPDF_LIBRARY_CONFIG config;
    memset(&config, 0, sizeof(config));
    config.version = 2;
    FPDF_InitLibraryWithConfig(&config);
    g_ready = true;
}

// Devolve o documento (ou 0; o motivo fica em lastError).
JNIEXPORT jlong JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_open(JNIEnv* env, jobject, jstring path, jstring password) {
    std::string p = to_utf8(env, path);
    std::string pw = to_utf8(env, password);
    FPDF_DOCUMENT d = FPDF_LoadDocument(p.c_str(), password ? pw.c_str() : nullptr);
    return reinterpret_cast<jlong>(d);
}

// Revisão do método de criptografia (/Encrypt), ou -1 se o PDF não tem criptografia.
JNIEXPORT jint JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_securityRevision(JNIEnv*, jobject, jlong doc) {
    return FPDF_GetSecurityHandlerRevision(doc_of(doc));
}

JNIEXPORT jint JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_lastError(JNIEnv*, jobject) {
    return static_cast<jint>(FPDF_GetLastError());
}

JNIEXPORT jlong JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_newDocument(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(FPDF_CreateNewDocument());
}

JNIEXPORT void JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_close(JNIEnv*, jobject, jlong doc) {
    if (doc) FPDF_CloseDocument(doc_of(doc));
}

JNIEXPORT jint JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_pageCount(JNIEnv*, jobject, jlong doc) {
    return FPDF_GetPageCount(doc_of(doc));
}

// Largura e altura da página como ela aparece (com o giro da página), em pontos.
JNIEXPORT jfloatArray JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_pageSize(JNIEnv* env, jobject, jlong doc, jint index) {
    FS_SIZEF size{0, 0};
    FPDF_GetPageSizeByIndexF(doc_of(doc), index, &size);
    jfloatArray out = env->NewFloatArray(2);
    float v[2] = {size.width, size.height};
    env->SetFloatArrayRegion(out, 0, 2, v);
    return out;
}

JNIEXPORT jint JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_rotation(JNIEnv*, jobject, jlong doc, jint index) {
    Page page(doc_of(doc), index);
    return page.p ? FPDFPage_GetRotation(page.p) : 0;
}

// Desenha a página dentro do bitmap (ARGB_8888). A página inteira ocupa sizeX x sizeY pixels e o bitmap
// mostra o trecho que começa em (startX, startY): assim dá para desenhar só a parte visível com zoom.
JNIEXPORT jboolean JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_render(JNIEnv* env, jobject, jlong doc, jint index,
                                                                      jobject bitmap, jint startX, jint startY,
                                                                      jint sizeX, jint sizeY, jboolean annotations) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    Page page(doc_of(doc), index);
    if (!page.p) return JNI_FALSE;
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    FPDF_BITMAP bmp = FPDFBitmap_CreateEx(info.width, info.height, FPDFBitmap_BGRA, pixels, info.stride);
    bool ok = bmp != nullptr;
    if (ok) {
        FPDFBitmap_FillRect(bmp, 0, 0, info.width, info.height, 0xFFFFFFFF);
        int flags = FPDF_REVERSE_BYTE_ORDER | (annotations ? FPDF_ANNOT : 0);
        FPDF_RenderPageBitmap(bmp, page.p, -startX, -startY, sizeX, sizeY, 0, flags);
        FPDFBitmap_Destroy(bmp);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// Texto da página. Cada caractere do texto devolvido corresponde a um índice do PDFium (para os retângulos).
JNIEXPORT jstring JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_pageText(JNIEnv* env, jobject, jlong doc, jint index) {
    Page page(doc_of(doc), index);
    TextPage text(page.p);
    if (!text.t) return env->NewStringUTF("");
    int n = FPDFText_CountChars(text.t);
    if (n <= 0) return env->NewStringUTF("");
    std::vector<unsigned short> buf(n + 1, 0);
    int written = FPDFText_GetText(text.t, 0, n, buf.data());
    int len = std::max(0, written - 1);   // sem o 0 final
    return env->NewString(reinterpret_cast<const jchar*>(buf.data()), len);
}

// Retângulos de um trecho do texto, em frações da página como ela aparece (0 a 1; x0, y0, x1, y1 com y para baixo).
JNIEXPORT jfloatArray JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_textRects(JNIEnv* env, jobject, jlong doc, jint index,
                                                                            jint start, jint count) {
    Page page(doc_of(doc), index);
    TextPage text(page.p);
    std::vector<float> out;
    if (text.t) {
        const int W = 10000, H = 10000;
        int n = FPDFText_CountRects(text.t, start, count);
        for (int i = 0; i < n; i++) {
            double l, t, r, b;
            if (!FPDFText_GetRect(text.t, i, &l, &t, &r, &b)) continue;
            int x0, y0, x1, y1;
            FPDF_PageToDevice(page.p, 0, 0, W, H, 0, l, t, &x0, &y0);
            FPDF_PageToDevice(page.p, 0, 0, W, H, 0, r, b, &x1, &y1);
            out.push_back(std::min(x0, x1) / float(W));
            out.push_back(std::min(y0, y1) / float(H));
            out.push_back(std::max(x0, x1) / float(W));
            out.push_back(std::max(y0, y1) / float(H));
        }
    }
    jfloatArray arr = env->NewFloatArray(static_cast<jsize>(out.size()));
    if (!out.empty()) env->SetFloatArrayRegion(arr, 0, static_cast<jsize>(out.size()), out.data());
    return arr;
}

// Para a análise: [caracteres que não são espaço, pixels da maior imagem].
JNIEXPORT jlongArray JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_pageStats(JNIEnv* env, jobject, jlong doc, jint index) {
    Page page(doc_of(doc), index);
    long long chars = 0, image = 0;
    if (page.p) {
        TextPage text(page.p);
        if (text.t) {
            int n = FPDFText_CountChars(text.t);
            for (int i = 0; i < n; i++) {
                unsigned int c = FPDFText_GetUnicode(text.t, i);
                if (c > 32 && c != 0xA0 && c != 0xFFFE && c != 0xFFFF) chars++;
            }
        }
        int n = FPDFPage_CountObjects(page.p);
        for (int i = 0; i < n; i++) image = std::max(image, largest_image(FPDFPage_GetObject(page.p, i), page.p, 0));
    }
    jlongArray arr = env->NewLongArray(2);
    jlong v[2] = {chars, image};
    env->SetLongArrayRegion(arr, 0, 2, v);
    return arr;
}

JNIEXPORT jboolean JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_deletePage(JNIEnv*, jobject, jlong doc, jint index) {
    if (index < 0 || index >= FPDF_GetPageCount(doc_of(doc))) return JNI_FALSE;
    FPDFPage_Delete(doc_of(doc), index);
    return JNI_TRUE;
}

// Leva as páginas indicadas (na ordem dada) para começar na posição dest.
JNIEXPORT jboolean JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_movePages(JNIEnv* env, jobject, jlong doc,
                                                                         jintArray indices, jint dest) {
    jsize n = env->GetArrayLength(indices);
    std::vector<int> v(n);
    env->GetIntArrayRegion(indices, 0, n, v.data());
    return FPDF_MovePages(doc_of(doc), v.data(), static_cast<unsigned long>(n), dest) ? JNI_TRUE : JNI_FALSE;
}

// Giro da página: 0, 1, 2 ou 3 (vezes 90° no sentido horário).
JNIEXPORT jboolean JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_setRotation(JNIEnv*, jobject, jlong doc, jint index, jint rotation) {
    Page page(doc_of(doc), index);
    if (!page.p) return JNI_FALSE;
    // Só muda o /Rotate da página; o conteúdo não é regravado.
    FPDFPage_SetRotation(page.p, ((rotation % 4) + 4) % 4);
    return JNI_TRUE;
}

// Copia páginas de src (índices começando em 0) para dest, a partir da posição at.
JNIEXPORT jboolean JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_importPages(JNIEnv* env, jobject, jlong dest, jlong src,
                                                                           jintArray indices, jint at) {
    jsize n = env->GetArrayLength(indices);
    std::vector<int> v(n);
    env->GetIntArrayRegion(indices, 0, n, v.data());
    return FPDF_ImportPagesByIndex(doc_of(dest), doc_of(src), v.data(), static_cast<unsigned long>(n), at) ? JNI_TRUE : JNI_FALSE;
}

// Grava o documento inteiro (não incremental) no arquivo indicado. Com removeSecurity, grava sem a senha
// (o documento precisa ter sido aberto com a senha certa).
JNIEXPORT jboolean JNICALL Java_br_com_prumopdf_pdf_PdfiumNative_save(JNIEnv* env, jobject, jlong doc, jstring path,
                                                                    jboolean removeSecurity) {
    std::string p = to_utf8(env, path);
    FILE* f = fopen(p.c_str(), "wb");
    if (!f) return JNI_FALSE;
    FileWriter w(f);
    bool ok = FPDF_SaveAsCopy(doc_of(doc), &w, FPDF_NO_INCREMENTAL | (removeSecurity ? FPDF_REMOVE_SECURITY : 0)) && w.ok;
    if (fflush(f) != 0) ok = false;
    if (fclose(f) != 0) ok = false;
    if (!ok) LOGW("falha ao gravar %s", p.c_str());
    return ok ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
