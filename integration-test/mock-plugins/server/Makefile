# We just check the binary into GitHub -- that's simplest.

serve: serve.go Makefile
	docker run -v "$(shell pwd)":/build --rm golang:1.9.2 sh -c '(cd /build && CGO_ENABLED=0 go build -a -installsuffix cgo -ldflags="-s -w" -o serve .)'
