#include <codecvt>
#include <locale>
#include <iostream>
#include <cstdlib>
#include <cmath>
#include <memory>
#include <string>
#include <vector>

#include <boost/archive/iterators/base64_from_binary.hpp>
#include <boost/archive/iterators/transform_width.hpp>
#include "public/cpp/fpdf_deleters.h"
#include "public/fpdfview.h"
#include "public/fpdf_annot.h"
#include "public/fpdf_ppo.h"
#include "public/fpdf_save.h"
#include "public/fpdf_text.h"
#include "lodepng.h"

static const int MaxNUtf16CharsPerPage = 100000;
static const int MaxThumbnailDimension = 700;
static const int StreamPdfBufferSize = 1024 * 1024; // 1MB
static const std::vector<uint8_t> EmptyPng;
static const std::string GetPageTextError; // we'll check the return value by address

class FileWrite : public FPDF_FILEWRITE {
public:
  FileWrite(FILE* aFile) : mFile(aFile) {
    FPDF_FILEWRITE::version = 1;
    FPDF_FILEWRITE::WriteBlock = WriteBlockCallback;
  }

  static int WriteBlockCallback(FPDF_FILEWRITE* pFileWrite, const void* data, unsigned long size) {
    FileWrite* pThis = static_cast<FileWrite*>(pFileWrite);
    size_t nWritten = std::fwrite(data, 1, size, pThis->mFile);
    return nWritten < size ? 0 : nWritten;
  }

  FILE* mFile;
};

std::string
formatLastPdfiumError()
{
  unsigned long err = FPDF_GetLastError();
  switch (err) {
    case FPDF_ERR_UNKNOWN: return "unknown error";
    case FPDF_ERR_FILE: return "file not found or could not be opened";
    case FPDF_ERR_FORMAT: return "file is not a valid PDF";
    case FPDF_ERR_PASSWORD: return "file is password-protected";
    case FPDF_ERR_SECURITY: return "unsupported security scheme";
    case FPDF_ERR_PAGE: return "page not found or content error";
    default: return std::string("unknown error: ") + std::to_string(err);
  }
}

/**
 * Encodes a PNG from correctly-sized buffer of pixel data.
 *
 * This overwrites data in argbBuffer.
 */
std::vector<uint8_t>
argbToPng(uint32_t* argbBuffer, size_t width, size_t height)
{
  // Write BGR to the same buffer, destroying it. This saves memory.
  uint8_t* bgrBuffer = reinterpret_cast<uint8_t*>(argbBuffer);

  size_t n = width * height;
  for (size_t i = 0, o = 0; i < n; i++, o += 3) {
    const uint32_t argb = argbBuffer[i];
    bgrBuffer[o + 0] = (argb >>  0) & 0xff;
    bgrBuffer[o + 1] = (argb >>  8) & 0xff;
    bgrBuffer[o + 2] = (argb >> 16) & 0xff;
  }

  std::vector<uint8_t> out;
  const unsigned int err = lodepng::encode(out, &bgrBuffer[0], width, height, LCT_RGB);
  if (err) {
    return EmptyPng;
  }
  return out;
}

/**
 * Renders the given PDF page as a PNG.
 *
 * Returns "" if rendering failed. That means out-of-memory.
 */
std::vector<uint8_t>
renderPageThumbnailPngOrReturnEmptyPng(FPDF_PAGE page)
{
  double pageWidth = FPDF_GetPageWidth(page);
  double pageHeight = FPDF_GetPageHeight(page);

  int width = MaxThumbnailDimension;
  int height = MaxThumbnailDimension;
  if (pageWidth > pageHeight) {
    height = static_cast<int>(std::round(1.0 * MaxThumbnailDimension * pageHeight / pageWidth));
  } else {
    width = static_cast<int>(std::round(1.0 * MaxThumbnailDimension * pageWidth / pageHeight));
  }

  std::unique_ptr<uint32_t[]> buffer(new (std::nothrow) uint32_t[width * height]);
  if (!buffer) {
    return EmptyPng; // out of memory
  }

  FPDF_BITMAP bitmap = FPDFBitmap_CreateEx(width, height, FPDFBitmap_BGRA, &buffer[0], sizeof(uint32_t) * width);
  if (!bitmap) {
    return EmptyPng; // can this even happen?
  }

  // TODO investigate speedup from
  // FPDF_RENDER_NO_SMOOTHTEXT, FPDF_RENDER_NO_SMOOTHIMAGE, FPDF_RENDER_NO_SMOOTHPATH
  int flags = 0;
  FPDFBitmap_FillRect(bitmap, 0, 0, width, height, 0xffffffff);
  FPDF_RenderPageBitmap(bitmap, page, 0, 0, width, height, 0, flags);
  FPDFBitmap_Destroy(bitmap);

  std::vector<uint8_t> png(argbToPng(&buffer[0], width, height));
  return png; // TODO investigate possible lodepng errors and how to handle them.
}

// Remove "\f" characters. This helps us conform with the spec, which places
// a "\f" before every subsequent page's info.
void
normalizeUtf16(char16_t* buf, int n) {
  for (int i = 0; i < n; i++) {
    if (buf[i] == '\f') {
      buf[i] = ' ';
    }
  }
}

void
outputChar(char c)
{
  std::cout.put(c);
}

void
outputBool(bool b)
{
  outputChar(static_cast<char>(b ? 0x1 : 0x0));
}

void
outputSize(size_t size)
{
  outputChar(static_cast<char>((size >> 24) & 0xff));
  outputChar(static_cast<char>((size >> 16) & 0xff));
  outputChar(static_cast<char>((size >> 8) & 0xff));
  outputChar(static_cast<char>((size >> 0) & 0xff));
}

void
outputBytes(const std::vector<uint8_t>& bytes)
{
  outputSize(bytes.size());
  std::cout.write(reinterpret_cast<const char*>(&bytes.cbegin()[0]), bytes.size());
}

void
outputString(const std::string& s)
{
  outputSize(s.size());
  std::cout.write(s.c_str(), s.size());
}

/**
 * Outputs the given file contents to stdout, prefaced with a length integer.
 *
 * This assumes the file pointer is at the _end_: we calculate size with ftell()
 * and then rewind() and fread() file contents.
 *
 * Any error is catastrophic and leads to undefined behavior. (If ftell and
 * fread on a tempfile fail, we have problems.)
 */
void
outputFileIfNotNull(FILE* f)
{
  if (f == nullptr) {
    outputString("");
  } else {
    long nBytesRemaining = std::ftell(f);
    if (nBytesRemaining == -1) {
      std::perror("Could not ftell() tempfile");
      return;
    }

    std::rewind(f);

    outputSize(static_cast<int>(nBytesRemaining));

    char buf[StreamPdfBufferSize] = {};
    while (true) {
      size_t n = std::fread(&buf[0], 1, StreamPdfBufferSize, f);
      if (n == 0) {
        if (std::ferror(f)) {
          std::perror("Could not fread() tempfile");
          return;
        } else {
          break; // we're done reading the file
        }
      }

      std::cout.write(&buf[0], n);
    }
  }
}

void
outputError(const std::string& message)
{
  outputChar(0x3);
  outputString(message);
}

/**
 * Returns `true` iff `fPage` had OCR run on it.
 *
 * We flag OCR on the page by adding an annotation. If the _last_ annotation
 * on the page is invisible+hidden RGBA color 0xd0cd0c00, the page had OCR.
 * (Better suggestions are welcome.)
 */
bool
pageHasOcr(FPDF_PAGE fPage)
{
  // Look for the text annotation, in rect 0,0,0,0,
  // with text: "OCR by www.overviewdocs.com"
  int nAnnotations = FPDFPage_GetAnnotCount(fPage);

  if (nAnnotations == 0) return false;

  FPDF_ANNOTATION annot = FPDFPage_GetAnnot(fPage, nAnnotations - 1);
  if (!annot) return false;

  unsigned int r = 0, g = 0, b = 0, a = 0;

  bool ret = (
      FPDF_ANNOT_LINE == FPDFAnnot_GetSubtype(annot)
      && FPDFAnnot_GetColor(annot, FPDFANNOT_COLORTYPE_Color, &r, &g, &b, &a)
      && (r / 255) == 0xd0
      && (g / 255) == 0xcd
      && (b / 255) == 0x0c
  );

  FPDFPage_CloseAnnot(annot);

  return ret;
}

std::string
getPageTextUtf8OrOutputError(FPDF_PAGE fPage)
{
  std::unique_ptr<void, FPDFTextPageDeleter> textPage(FPDFText_LoadPage(fPage));
  if (!textPage) {
    outputError(std::string("Failed to read text from PDF page: ") + formatLastPdfiumError());
    return GetPageTextError;
  }

  // TODO handle out-of-memory on big buffers

  char16_t utf16Buf[MaxNUtf16CharsPerPage];
  int nChars = FPDFText_GetText(textPage.get(), 0, MaxNUtf16CharsPerPage, reinterpret_cast<unsigned short*>(&utf16Buf[0]));
  normalizeUtf16(&utf16Buf[0], nChars);
  std::u16string u16Text(&utf16Buf[0], nChars);

  std::wstring_convert<std::codecvt_utf8_utf16<char16_t>,char16_t> convert;
  std::string u8Text(convert.to_bytes(u16Text));
  // [adam, 2017-12-14] pdfium tends to end its string with a nullptr byte. That
  // makes tests ugly, and it gives no value. Nix the nullptr byte.
  if (u8Text.size() > 0 && u8Text[u8Text.size() - 1] == '\0') u8Text.resize(u8Text.size() - 1);

  return u8Text;
}

std::unique_ptr<FILE, decltype(&fclose)>
renderPagePdfToTempfileOrOutputErrorAndReturnNull(FPDF_DOCUMENT inDocument, int pageIndex)
{
  // always return spool: either a null pointer, or a FILE*
  auto spool(std::unique_ptr<FILE, decltype(&fclose)>(nullptr, &fclose));

  std::unique_ptr<void, FPDFDocumentDeleter> outDocument(FPDF_CreateNewDocument());
  std::string pageIndexString = std::to_string(pageIndex + 1);
  if (!FPDF_ImportPages(outDocument.get(), inDocument, pageIndexString.c_str(), 0)) {
    outputError(std::string("Error outputting page with index ") + std::to_string(pageIndex) + ": " + formatLastPdfiumError());
    return spool;
  }

  spool.reset(std::tmpfile());
  FileWrite writer(spool.get());
  FPDF_SaveAsCopy(outDocument.get(), &writer, FPDF_REMOVE_SECURITY);

  return spool;
}

bool
streamPageParts(
        FPDF_DOCUMENT fDocument,
        int pageIndex,
        int nPages,
        bool makeThumbnail,
        bool makePdf
)
{
  std::unique_ptr<void, FPDFPageDeleter> fPage(FPDF_LoadPage(fDocument, pageIndex));
  if (!fPage) {
    outputError(std::string("Failed to read PDF page: ") + formatLastPdfiumError());
    return false;
  }

  bool hasOcr = pageHasOcr(fPage.get());

  std::vector<uint8_t> thumbnailPng;
  if (makeThumbnail) {
    thumbnailPng = renderPageThumbnailPngOrReturnEmptyPng(fPage.get());
    if (thumbnailPng.empty()) {
      // [adam, 2017-12-11] I _think_ the only possible error is out-of-memory
      outputError(std::string("Failed to render PDF thumbnail: out of memory"));
      return false;
    }
  }

  std::string u8Text = getPageTextUtf8OrOutputError(fPage.get());
  if (&u8Text == &GetPageTextError) return false;

  std::unique_ptr<FILE, decltype(&fclose)> pdfFile(nullptr, &fclose);
  if (makePdf) {
      pdfFile = renderPagePdfToTempfileOrOutputErrorAndReturnNull(fDocument, pageIndex);
      if (!pdfFile) return false;
  }

  outputChar(0x2);           // "PAGE"
  outputBool(hasOcr);
  outputBytes(thumbnailPng); // PNG
  outputFileIfNotNull(pdfFile.get());
  outputString(u8Text);

  return true;
}

bool
streamDocumentTextAndFirstPageThumbnail(FPDF_DOCUMENT fDocument, int nPages)
{
  for (int pageIndex = 0; pageIndex < nPages; pageIndex++) {
    if (!streamPageParts(fDocument, pageIndex, nPages, pageIndex == 0, false)) {
      return false; // we've already output a footer
    }
  }

  return true;
}

bool
streamDocumentPages(FPDF_DOCUMENT fDocument, int nPages)
{
  for (int pageIndex = 0; pageIndex < nPages; pageIndex++) {
    if (!streamPageParts(fDocument, pageIndex, nPages, true, true)) {
      return false; // we've already output a footer
    }
  }

  return true;
}

void
processPdfFile(const char* filename, bool onlyExtract)
{
  FPDF_STRING fFilename(filename);
  std::unique_ptr<void, FPDFDocumentDeleter> fDocument(FPDF_LoadDocument(fFilename, nullptr));
  if (!fDocument) {
    outputError(std::string("Failed to open PDF: ") + formatLastPdfiumError());
    return;
  }

  const int nPages = FPDF_GetPageCount(fDocument.get());
  outputChar(0x1); // HEADER
  outputSize(nPages);

  bool success;

  if (onlyExtract) {
    success = streamDocumentTextAndFirstPageThumbnail(fDocument.get(), nPages);
  } else {
    success = streamDocumentPages(fDocument.get(), nPages);
  }

  if (success) {
    outputChar(0x3); // FOOTER
    outputString(""); // no error
  }
}

int
main(int argc, char** argv)
{
  if (argc != 3) {
    std::cerr << "Usage: " << argv[0] << " --only-extract=true|false INFILE" << std::endl;
    return 1;
  }

  FPDF_InitLibrary();

  processPdfFile(argv[2], std::string("--only-extract=true") == argv[1]);

  FPDF_DestroyLibrary();

  // No matter what, if we got this far we "succeeded". (Our definition of
  // "success" is: "this file does not contain a bug.") Return 0, for success.
  return 0;
}
