CXX = clang++
LD = $(CXX)
CFLAGS = -std=c++11 -stdlib=libc++ -I/usr/include/pdfium
LDFLAGS = -std=c++11 -stdlib=libc++ -static -lm -pthread -lpdfium

all: split-pdf-and-extract-text

main/%.o: main/%.cc
	$(CXX) $(CFLAGS) -c $< -o $@

main/split-pdf-and-extract-text.o: main/lodepng.h

split-pdf-and-extract-text: main/lodepng.o main/split-pdf-and-extract-text.o
	$(LD) $^ $(LDFLAGS) -o $@

clean:
	rm -f main/*.o split-pdf-and-extract-text
