#include <algorithm>
#include <cstdio>
#include <iostream>
#include <limits>
#include <string>

const int RecordTypeHeader = 0x1;
const int RecordTypePage = 0x2;
const int RecordTypeFooter = 0x3;
const int RecordTypeErrorWeAlreadyHandled = -1;

const uint32_t ReadSizeError = std::numeric_limits<std::uint32_t>::max();
const size_t BufferSize = 1024 * 1024;

uint32_t
readSize(std::FILE* input)
{
  char buf[4];
  uint32_t nRead = std::fread(&buf[0], 1, 4, input);
  if (nRead < 4) {
    if (std::ferror(input)) {
      std::perror("Error reading 4-byte stream size from input");
    } else {
      std::cerr << "Expected a 4-byte integer; got " << nRead << " bytes" << std::endl;
    }
    return ReadSizeError;
  }

  return ((buf[0] & 0xff) << 24) | ((buf[1] & 0xff) << 16) | ((buf[2] & 0xff) << 8) | (buf[3] & 0xff);
}

int
readRecordType(std::FILE* input, const char* noun)
{
  int recordType = std::fgetc(input);
  if (recordType == EOF) {
    if (std::ferror(input)) {
      std::cerr << "Error reading " << noun << " from input: ";
      std::perror(NULL);
    } else {
      std::cerr << "Expected " << noun << " to have input; it had nothing" << std::endl;
    }
    return RecordTypeErrorWeAlreadyHandled;
  }
  return recordType;
}

bool
writeFile(const std::string& filename, const std::string& bytes)
{
  FILE* f = std::fopen(filename.c_str(), "w");
  if (!f) {
    std::cerr << "Failed to open " << filename << " for writing: ";
    std::perror(NULL);
    return false;
  }

  bool error = false;

  if (bytes.size() != std::fwrite(bytes.c_str(), 1, bytes.size(), f)) {
    std::cerr << "Failed to write to " << filename << ": ";
    std::perror(NULL);
    error = true;
  }

  // fclose(), regardless of whether there's an error
  if (0 != std::fclose(f)) {
    // ... but don't print the second error
    if (!error) {
      std::cerr << "Failed to close " << filename << ": ";
      std::perror(NULL);
    }
    error = true;
  }

  return !error;
}

bool
streamBlob(std::FILE* input, uint32_t nBytes, const std::string& filename)
{
  FILE* f = std::fopen(filename.c_str(), "w");
  if (!f) {
    std::cerr << "Failed to open " << filename << " for writing: ";
    std::perror(NULL);
    return false;
  }

  bool error = false;

  while (nBytes > 0) {
    char buf[BufferSize];
    const size_t nBytesToTry = std::min<size_t>(nBytes, BufferSize);

    if (nBytesToTry != std::fread(&buf[0], 1, nBytesToTry, input)) {
      if (std::feof(input)) {
        std::cerr << "Truncated blob input" << std::endl;
      } else {
        std::perror("Failed to read blob");
      }
      error = true;
      break;
    }

    if (nBytesToTry != std::fwrite(&buf[0], 1, nBytesToTry, f)) {
      std::cerr << "Failed to write to " << filename << ": ";
      std::perror(NULL);
      error = true;
      break;
    }

    nBytes -= nBytesToTry;
  }

  // fclose(), regardless of whether there's an error
  if (0 != std::fclose(f)) {
    // ... but don't print the second error
    if (!error) {
      std::cerr << "Failed to close " << filename << ": ";
      std::perror(NULL);
    }
    error = true;
  }

  return !error;
}

bool
streamSizedBlob(std::FILE* input, const std::string& filename)
{
  uint32_t nBytes = readSize(input);
  if (nBytes == ReadSizeError) return false;

  return streamBlob(input, nBytes, filename);
}

bool
streamSizedBlobIfNonEmpty(std::FILE* input, const std::string& filename)
{
  uint32_t nBytes = readSize(input);
  if (nBytes == ReadSizeError) return false;

  if (nBytes > 0) {
    return streamBlob(input, nBytes, filename);
  } else {
    return true;
  }
}

bool
streamPage(std::FILE* input, const std::string& outdir, uint32_t pageNum)
{
  const std::string basename = outdir + "/p" + std::to_string(pageNum);

  int isOcrC = std::fgetc(input);
  switch (isOcrC) {
    case EOF:
      if (std::ferror(input)) {
        std::perror("Error reading isOcr from input");
      } else {
        std::cerr << "Expected isOcr byte; got end of stream" << std::endl;
      }
      return false;
    case 0:
    case 1:
      writeFile(basename + ".is-ocr", isOcrC == 1 ? "true" : "false");
      break;
    default:
      std::cerr << "Expected isOcr byte of 0x0 or 0x1; got " << isOcrC << std::endl;
      return false;
  }

  if (!streamSizedBlobIfNonEmpty(input, basename + ".png")) return false;
  if (!streamSizedBlobIfNonEmpty(input, basename + ".pdf")) return false;
  if (!streamSizedBlob(input, basename + ".txt")) return false;

  return true;
}

bool
streamFooter(std::FILE* input, const std::string& outdir)
{
  if (!streamSizedBlobIfNonEmpty(input, outdir + "/error.txt")) return false;

  if (EOF != std::fgetc(input)) {
    std::cerr << "Input has extra byte(s) after the end of its footer" << std::endl;
    return false;
  }

  return true;
}

bool
streamNonEmptyInput(std::FILE* input, const std::string& outdir)
{
  uint32_t nPages = readSize(input);
  if (nPages == ReadSizeError) return false;
  if (nPages == 0) {
    std::cerr << "Expected non-zero number of pages" << std::endl;
    return false;
  }
  writeFile(outdir + "/n-pages", std::to_string(nPages));

  for (uint32_t i = 0; i < nPages; i++) {
    int recordType = readRecordType(input, "PAGE or FOOTER");
    switch (recordType)
    {
      case RecordTypePage:
        if (!streamPage(input, outdir, i + 1)) return false;
        break;
      case RecordTypeFooter:
        return streamFooter(input, outdir);
      default:
        std::cerr << "Expected PAGE or FOOTER record type; got " << recordType << std::endl;
        return false;
    }
  }

  int finalRecordType = readRecordType(input, "FOOTER");
  switch (finalRecordType)
  {
    case RecordTypeFooter: return streamFooter(input, outdir);
    default:
      std::cerr << "Expected FOOTER record type; got " << finalRecordType << std::endl;
      return false;
  }
}

/**
 * Main loop; returns `true` on success.
 *
 * If there's an error, it's already been printed to stderr.
 *
 * Be aware: an input with an early error is _valid_ according to this program.
 * The simplest file is 0x00
 */
bool
streamInputToDir(std::FILE* input, const std::string& outdir)
{
  int recordType = readRecordType(input, "HEADER or FOOTER");
  switch (recordType)
  {
    case RecordTypeErrorWeAlreadyHandled: return false;
    case RecordTypeHeader: return streamNonEmptyInput(input, outdir);
    case RecordTypeFooter: return streamFooter(input, outdir);
    default:
      std::cerr << "Expected HEADER or FOOTER record type; got " << recordType << std::endl;
      return false;
  }
}

int
main(int argc, char** argv)
{
  if (argc != 2) {
    std::cerr << "Usage: " << argv[0] << " OUTDIR" << std::endl;
    return 1;
  }

  // Linux: no difference between text mode and binary mode streams
  return streamInputToDir(stdin, argv[1]) ? 0 : 1;
}
