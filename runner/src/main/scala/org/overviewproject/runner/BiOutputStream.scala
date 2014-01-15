package org.overviewproject.runner

import java.io.OutputStream

class BiOutputStream(out1: OutputStream, out2: OutputStream) extends OutputStream {
  override def write(b: Int): Unit = {
    out1.write(b)
    out2.write(b)
  }

  override def write(b: Array[Byte]): Unit = {
    write(b, 0, b.length)
  }

  override def write(b: Array[Byte], off: Int, len: Int) : Unit = {
    out1.write(b, off, len)
    out2.write(b, off, len)
  }

  override def flush(): Unit = {
    out1.flush()
    out2.flush()
  }

  override def close(): Unit = {
    out1.close()
    out2.close()
  }
}
