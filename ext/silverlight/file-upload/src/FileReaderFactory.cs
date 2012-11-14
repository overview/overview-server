using System.Windows.Browser;

namespace OverviewProject.FileUpload {
  public class FileReaderFactory {
    public FileReaderFactory() {}

    [ScriptableMember]
    public FileReader CreateFileReader() {
      return new FileReader();
    }
  }
}
