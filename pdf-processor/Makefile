CXX = clang++
LD = $(CXX)
CFLAGS = -Wall -std=c++11 -stdlib=libc++ -I/usr/include/pdfium
LDFLAGS = -Wall -std=c++11 -stdlib=libc++ -static -lm -pthread -lpdfium

all: split-pdf-and-extract-text dump-split-pdf-and-extract-text-output

main/%.o : main/%.cc
	$(CXX) $(CFLAGS) -c $< -o $@

split-pdf-and-extract-text: main/lodepng.o main/split-pdf-and-extract-text.o
	$(LD) $^ $(LDFLAGS) -o $@

dump-split-pdf-and-extract-text-output: main/dump-split-pdf-and-extract-text-output.o
	$(LD) $^ $(LDFLAGS) -o $@

clean:
	rm -f main/*.o split-pdf-and-extract-text dump-split-pdf-and-extract-text-output
