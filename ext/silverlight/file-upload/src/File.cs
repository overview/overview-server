using System;
using System.IO;
using System.Windows.Browser;

namespace OverviewProject.FileUpload {
  // Mimics HTML5 "File" type. http://www.w3.org/TR/FileAPI/
  public class File : Blob {
    private FileInfo fileInfo;

    [ScriptableMember]
    public override long Size { get { return fileInfo.Length; } }

    [ScriptableMember]
    public string Name { get { return fileInfo.Name; } }

    [ScriptableMember]
    public long LastModifiedDate { get { return 0; } } // Silverlight security

    public File(FileInfo fileInfo) {
      this.fileInfo = fileInfo;
    }

    public override Stream OpenStream() {
      // Bug: You can only iterate over this file once.
      return fileInfo.OpenRead();
    }
  }
}
