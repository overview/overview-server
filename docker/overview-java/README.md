Overview Java
=============

Our Java image with all our jarfiles.

This Dockerfile incorporates a tarball from outside its build context. To
build, from two directories below:

```bash
./build archive.zip && \
    tar -c archive.tar.gz -C docker/java Dockerfile \
      | docker build -t overview/java -
```
