using System;
using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Browser;

namespace OverviewProject.FileUpload {
  public partial class FilePickerControl : UserControl {
    private File file;

    [ScriptableType]
    public class FileSelectedEventArgs : EventArgs {
      private File file;

      public FileSelectedEventArgs(File file) {
        this.file = file;
      }

      public File File { get { return file; } }
    }

    [ScriptableMember] public event EventHandler<FileSelectedEventArgs> FileSelected;

    public FilePickerControl() {
      InitializeComponent();
    }

    private void Button_Click(object sender, RoutedEventArgs e) {
      OpenFileDialog dialog = new OpenFileDialog();
      dialog.Filter = "CSV Files (*.csv)|*.csv|All files (*.*)|*.*";
      dialog.FilterIndex = 1;
      dialog.Multiselect = false;

      bool? ok = dialog.ShowDialog();

      if (ok == true) {
        this.file = new File(dialog.File);
        this.RefreshText();
        this.OnFileSelected(new FileSelectedEventArgs(this.file));
      }
    }

    private void RefreshText() {
      string text = (file != null) ? file.Name : "";
      TextBlock label = FindName("Label") as TextBlock;
      label.Text = text;
    }

    protected void OnFileSelected(FileSelectedEventArgs args) {
      if (FileSelected != null) FileSelected(this, args);
    }
  }
}
