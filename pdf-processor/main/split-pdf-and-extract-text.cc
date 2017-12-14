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
#include "fpdfview.h"
#include "fpdf_text.h"
#include "lodepng.h"

static const int MaxNUtf16CharsPerPage = 100000;
static const int MaxThumbnailDimension = 700;
static const std::vector<uint8_t> EmptyPng;

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
renderPageThumbnailPng(FPDF_PAGE& page)
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

void
outputError(const std::string& message)
{
  outputChar(0x3);
  outputString(message);
}

bool
pageHasOcr(FPDF_PAGE& fPage)
{
  return false; // TODO find a way to implement this
}

bool
streamPageTextAndMaybeThumbnail(FPDF_DOCUMENT& fDocument, int pageIndex, int nPages, std::ostream& out, bool thumbnail)
{
  FPDF_PAGE fPage = FPDF_LoadPage(fDocument, pageIndex);
  if (!fPage) {
    outputError(std::string("Failed to read PDF page: ") + formatLastPdfiumError());
    return false;
  }

  bool hasOcr = pageHasOcr(fPage);

  std::vector<uint8_t> thumbnailPng;
  if (thumbnail) {
    thumbnailPng = renderPageThumbnailPng(fPage);
    if (thumbnailPng.empty()) {
      // [adam, 2017-12-11] I _think_ the only possible error is out-of-memory
      outputError(std::string("Failed to render PDF thumbnail: out of memory"));
      FPDF_ClosePage(fPage);
      return false;
    }
  }

  FPDF_TEXTPAGE textPage = FPDFText_LoadPage(fPage);
  if (!textPage) {
    outputError(std::string("Failed to read text from PDF page: ") + formatLastPdfiumError());
    FPDF_ClosePage(fPage);
    return false;
  }

  char16_t utf16Buf[MaxNUtf16CharsPerPage];
  int nChars = FPDFText_GetText(textPage, 0, MaxNUtf16CharsPerPage, reinterpret_cast<unsigned short*>(&utf16Buf[0]));
  normalizeUtf16(&utf16Buf[0], nChars);
  std::u16string u16Text(&utf16Buf[0], nChars);

  std::wstring_convert<std::codecvt_utf8_utf16<char16_t>,char16_t> convert;
  std::string u8Text(convert.to_bytes(u16Text));

  FPDFText_ClosePage(textPage);
  FPDF_ClosePage(fPage);

  outputChar(0x2);           // "PAGE"
  outputBool(hasOcr);
  outputBytes(thumbnailPng); // PNG
  outputString("");          // PDF
  outputString(u8Text);

  return true;
}

void
streamDocumentTextAndFirstPageThumbnail(FPDF_DOCUMENT& fDocument, std::ostream& out)
{
  const int nPages = FPDF_GetPageCount(fDocument);

  outputChar(0x1); // HEADER
  outputSize(nPages);

  for (int pageIndex = 0; pageIndex < nPages; pageIndex++) {
    if (!streamPageTextAndMaybeThumbnail(fDocument, pageIndex, nPages, out, pageIndex == 0)) {
      return; // we've already output a footer
    }
  }

  outputChar(0x3); // FOOTER
  outputString(""); // no error
}

void
streamDocumentPages(FPDF_DOCUMENT& fDocument, std::ostream& out)
{
}

int
main(int argc, char** argv)
{
  if (argc != 3) {
    std::cerr << "Usage: " << argv[0] << " --only-extract=true|false INFILE" << std::endl;
    return 1;
  }

  FPDF_InitLibrary();

  FPDF_STRING fFilename(argv[2]);
  FPDF_DOCUMENT fDocument(FPDF_LoadDocument(fFilename, NULL));
  if (fDocument) {
    if (std::string("--only-extract=true") == std::string(argv[1])) {
      streamDocumentTextAndFirstPageThumbnail(fDocument, std::cout);
    } else {
      streamDocumentPages(fDocument, std::cout);
    }
    FPDF_CloseDocument(fDocument);
  } else {
    outputError(std::string("Failed to open PDF: ") + formatLastPdfiumError());
  }

  FPDF_DestroyLibrary();

  // No matter what, if we got this far we "succeeded". (Our definition of
  // "success" is: "this file does not contain a bug.") Return 0, for success.
  return 0;
}
