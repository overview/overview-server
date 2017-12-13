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
 * Destructively converts the given BGRA buffer to a PNG.
 */
std::vector<uint8_t>
bgraToPng(uint32_t* bgraBuffer, size_t width, size_t height)
{
  // We'll edit bgraBuffer in-place. But let's name it rgbaBuffer when we write
  // rgba to it.
  uint32_t* rgbaBuffer = bgraBuffer;

  const size_t nPixels = width * height;
  for (size_t i = 0; i < nPixels; i++) {
    const uint32_t bgra = bgraBuffer[i];
    const uint32_t rgba =
        ((bgra << 16) & 0xff000000)  // r
      | ( bgra        & 0x00ff00ff)  // g+a
      | ((bgra >> 16) & 0x0000ff00); // b
    rgbaBuffer[i] = rgba;
  }

  std::vector<uint8_t> out;
  const unsigned int err = lodepng::encode(out, reinterpret_cast<uint8_t*>(rgbaBuffer), width, height, LCT_RGB);
  if (err) {
    return EmptyPng;
  }
  return out;
}

/**
 * Renders the given PDF page as a PNG, base64-encoded.
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
    height = static_cast<int>(std::round(MaxThumbnailDimension * pageHeight / pageWidth));
  } else {
    width = static_cast<int>(std::round(MaxThumbnailDimension * pageWidth / pageHeight));
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

  std::vector<uint8_t> png(bgraToPng(&buffer[0], width, height));
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

bool
streamPageTextAndMaybeThumbnail(FPDF_DOCUMENT& fDocument, int pageIndex, int nPages, std::ostream& out, bool thumbnail)
{
  FPDF_PAGE page = FPDF_LoadPage(fDocument, pageIndex);
  if (!page) {
    std::cout << "Failed to read PDF page: " << formatLastPdfiumError();
    return false;
  }

  std::vector<uint8_t> thumbnailPng;
  if (thumbnail) {
    thumbnailPng = renderPageThumbnailPng(page);
    if (thumbnailPng.empty()) {
      // [adam, 2017-12-11] I _think_ the only possible error is out-of-memory
      std::cout << "Failed to render PDF thumbnail: out of memory";
      FPDF_ClosePage(page);
      return false;
    }
  }

  FPDF_TEXTPAGE textPage = FPDFText_LoadPage(page);
  if (!textPage) {
    std::cout << "Failed to read text from PDF page: " << formatLastPdfiumError();
    FPDF_ClosePage(page);
    return false;
  }

  char16_t utf16Buf[MaxNUtf16CharsPerPage];
  int nChars = FPDFText_GetText(textPage, 0, MaxNUtf16CharsPerPage, reinterpret_cast<unsigned short*>(&utf16Buf[0]));
  normalizeUtf16(&utf16Buf[0], nChars);
  std::u16string u16Text(&utf16Buf[0], nChars);

  std::wstring_convert<std::codecvt_utf8_utf16<char16_t>,char16_t> convert;
  std::string u8Text(convert.to_bytes(u16Text));

  FPDFText_ClosePage(textPage);
  FPDF_ClosePage(page);

  out << pageIndex << "/" << nPages << " ";

  // output base64 PNG (may be zero-length)
  typedef boost::archive::iterators::base64_from_binary<
    boost::archive::iterators::transform_width<std::vector<uint8_t>::const_iterator, 6, 8>
  > base64_text;
  std::copy(
      base64_text(thumbnailPng.cbegin()),
      base64_text(thumbnailPng.cend()),
      std::ostream_iterator<char>(out)
  );

  out << " " << u8Text << "\f";

  return true;
}

void
streamDocumentTextAndThumbnails(FPDF_DOCUMENT& fDocument, std::ostream& out, bool thumbnailAll)
{
  const int nPages = FPDF_GetPageCount(fDocument);

  for (int pageIndex = 0; pageIndex < nPages; pageIndex++) {
    if (!streamPageTextAndMaybeThumbnail(fDocument, pageIndex, nPages, out, thumbnailAll || pageIndex == 0)) {
      break;
    }
  }
}

void
streamFileTextAndThumbnails(const char* filename, std::ostream& out, bool thumbnailAll)
{
  FPDF_InitLibrary();

  FPDF_STRING fFilename(filename);
  FPDF_DOCUMENT fDocument(FPDF_LoadDocument(fFilename, NULL));
  if (fDocument) {
    streamDocumentTextAndThumbnails(fDocument, out, thumbnailAll);
    FPDF_CloseDocument(fDocument);
  } else {
    out << "Failed to open PDF: " << formatLastPdfiumError();
  }

  FPDF_DestroyLibrary();
}

int
main(int argc, char** argv)
{
  if (argc != 3) {
    std::cerr << "Usage: " << argv[0] << " --thumbnails-for-page=first|all INFILE" << std::endl;
    return 1;
  }

  bool thumbnailAll = std::string("--thumbnails-for-page=all") == std::string(argv[1]);

  streamFileTextAndThumbnails(argv[2], std::cout, thumbnailAll);

  // No matter what, if we got this far we "succeeded". Because that's our
  // definition of success. So return 0, the successful status code.
  return 0;
}
