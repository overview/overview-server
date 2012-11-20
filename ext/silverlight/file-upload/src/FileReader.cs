using System;
using System.IO;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Browser;

namespace OverviewProject.FileUpload {
  // Mimics HTML5 "FileReader" type. http://www.w3.org/TR/FileAPI/#FileReader-interface
  public class FileReader {
    public const ushort EMPTY = 0;
    public const ushort LOADING = 1;
    public const ushort DONE = 2;

    private const string DOM_ERROR_NOT_FOUND = "NotFoundError";
    private const string DOM_ERROR_SECURITY = "SecurityError";
    private const string DOM_ERROR_NOT_READABLE = "NotReadableError";
    private const string DOM_ERROR_INVALID_STATE = "InvalidStateError";

#region Member variables
    private bool completed = false;

    private ushort readyState = EMPTY;
    [ScriptableMember] public ushort ReadyState { get { return readyState; } }

    private string result;
    [ScriptableMember] public string Result { get { return result; } }

    private DOMError error;
    [ScriptableMember] public DOMError Error { get { return error; } }
#endregion

#region Events
    [ScriptableType]
    public class ProgressEventArgs : EventArgs {
      private bool lengthComputable;
      public bool LengthComputable { get { return lengthComputable; } }

      private ulong length;
      public ulong Length { get { return length; } }

      private ulong total;
      public ulong Total { get { return total; } }

      public ProgressEventArgs(bool lengthComputable, ulong length, ulong total) {
        this.lengthComputable = lengthComputable;
        this.length = length;
        this.total = total;
      }
    }

    [ScriptableMember] public event EventHandler<ProgressEventArgs> OnLoadEnd;
#endregion

    public FileReader() {}

#region ReadAsText() and helpers
    public Stream TryOpenStream(Blob blob) {
      Stream result = null;

      try {
        result = blob.OpenStream();
      } catch (UnauthorizedAccessException) {
        // Path is a directory.
        // File API: "the file has changed on disk since the user selected it"
        ThrowError(DOM_ERROR_SECURITY);
      } catch (DirectoryNotFoundException) {
        // Path is invalid
        // File API: "the File or Blob resource could not be found at the time the read was processed"
        ThrowError(DOM_ERROR_NOT_FOUND);
      } catch (IOException) {
        // The file is already open
        // File API: "it is determined that too many read calls are being made on File or Blob resources"
        ThrowError(DOM_ERROR_SECURITY);
      }

      return result;
    }

    public Encoding LookupEncoding(string tryEncoding) {
      Encoding encoding = null;

      if (tryEncoding == "windows-1252") {
        encoding = new Windows1252Encoding();
      } else {
        try {
          encoding = Encoding.GetEncoding(tryEncoding);
        } catch (ArgumentException) {
          // The user didn't present a valid string such as "utf-8".
          // We're supposed to do auto-encoding here, but I'm too lazy. Guess
          // at "utf-8". Don't throw an error.
          // http://www.w3.org/TR/FileAPI/#encoding-determination
          //
          // Silverlight actually only supports UTF-8 and UTF-16, so this
          // fallback will happen lots.
          // http://msdn.microsoft.com/en-us/library/system.text.encoding(v=vs.95).aspx
          encoding = Encoding.UTF8;
        }
      }

      return encoding;
    }

    public async Task<string> TryReadToEndAsync(StreamReader reader) {
      string result = null;
      
      try {
        result = await reader.ReadToEndAsync();
      } catch (ArgumentOutOfRangeException) {
        // File size > 2GB
        // "If the File or Blob cannot be read"
        ThrowError(DOM_ERROR_NOT_READABLE);
      }

      return result;
    }

    // http://www.w3.org/TR/FileAPI/#dfn-readAsText
    [ScriptableMember]
    public async void ReadAsText(Blob blob, string tryEncoding) {
      if (this.readyState == LOADING) {
        ThrowError(DOM_ERROR_INVALID_STATE);
        return;
      }

      this.readyState = LOADING;
      Encoding encoding = LookupEncoding(tryEncoding);

      using (Stream stream = TryOpenStream(blob)) {
        if (stream == null) return; // We called ThrowError() already

        using (StreamReader reader = new StreamReader(stream, encoding)) {
          // StreamReader's ctor won't throw an exception

          this.result = await TryReadToEndAsync(reader);
          if (this.result == null) return; // We called ThrowError() already

          this.error = null;
          this.readyState = DONE;
          OnOnLoadEnd(new ProgressEventArgs(true, (ulong) result.Length, (ulong) result.Length));
        }
      }
    }
#endregion

#region Event machinery
    protected void OnOnLoadEnd(ProgressEventArgs args) {
      if (!completed) {
        completed = true;
        if (OnLoadEnd != null) OnLoadEnd(this, args);
      }
    }

    [ScriptableType]
    public class DOMError {
      private string name;
      public string Name { get { return name; } }

      public DOMError(string name) {
        this.name = name;
      }
    }

    protected void ThrowError(string domErrorName) {
      this.error = new DOMError(domErrorName);
      this.result = null;
      this.readyState = DONE;
      OnOnLoadEnd(new ProgressEventArgs(false, 0, 0));
    }
#endregion
  }
}
