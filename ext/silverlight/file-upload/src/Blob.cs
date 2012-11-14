using System.IO;
using System.Windows.Browser;

namespace OverviewProject.FileUpload {
  // Mimics HTML5 "Blob" type. http://www.w3.org/TR/FileAPI/
  public class Blob {
    private Blob parentBlob;
    protected long size;
    protected long start;

    [ScriptableMember]
    public virtual long Size { get { return size; } }

    // no content-type because we don't use it

    internal Blob() {
      // There *ought* to be an IBlob that we extend, but I'm lazy.
      this.parentBlob = null;
      this.start = 0;
      this.size = 0;
    }

    internal Blob(Blob parentBlob, long start, long size) {
      this.parentBlob = parentBlob;
      this.start = start;
      this.size = size;
    }

    [ScriptableMember]
    public Blob slice(long start, long end) {
      return new Blob(this, start, end - start);
    }

    // no close() because we don't use it

    private class TruncatedStream : Stream {
      private Stream inputStream;
      private long length;
      private long position;

      public override bool CanRead { get { return true; } }
      public override bool CanSeek { get { return false; } }
      public override bool CanWrite { get { return false; } }
      public override long Length { get { return length; } }
      public override long Position {
        get { return inputStream.Position; }
        set { throw new System.NotSupportedException(); }
      }

      public TruncatedStream(Stream inputStream, long length) {
        this.inputStream = inputStream;
        this.length = length;
        this.position = 0;
      }

      public override void Flush() {}

      public override int Read(byte[] buffer, int offset, int count) {
        // Read from the stream, but never more than count
        if ((long) count > length - position) count = (int) (length - position);
        int bytesRead = inputStream.Read(buffer, offset, count);
        this.position += bytesRead;
        return bytesRead;
      }

      public override void Write(byte[] a, int b, int c) {
        throw new System.NotSupportedException();
      }

      public override long Seek(long a, SeekOrigin b) {
        throw new System.NotSupportedException();
      }

      public override void SetLength(long a) {
        throw new System.NotSupportedException();
      }
    }

    public virtual Stream OpenStream() {
      Stream parentStream = parentBlob.OpenStream();
      parentStream.Seek(start, SeekOrigin.Current);
      return new TruncatedStream(parentStream, size);
    }
  }
}
