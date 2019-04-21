package karino2.livejournal.com.meatpieday;

import android.os.Environment;
import androidx.annotation.NonNull;

import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by _ on 2017/01/31.
 */

public class Exporter {
    String folderName = "MeatPieDay";

    public static void ensureDirExist(File dir) throws IOException {
        if(!dir.exists()) {
            if(!dir.mkdir()){
                throw new IOException();
            }
        }
    }

    File getStoreDirectory() throws IOException {
        // getExternalStoragePublicDirectory
        File dir = new File(Environment.getExternalStorageDirectory(), folderName);
        ensureDirExist(dir);
        return dir;
    }

    public File exportBookForShare(OrmaDatabase orma, Book target) throws IOException {
        File shareDir = new File(getStoreDirectory(), "share_tmp");
        ensureDirExist(shareDir);

        File file = new File(shareDir,  target.name + ".ipynb");

        saveBookToFile(orma, file, target);
        return file;
    }


    public File exportBook(OrmaDatabase orma, Book target) throws IOException {
        File targetDir = getStoreDirectory();

        return exportBookAt(orma, target, targetDir);
    }

    @NonNull
    private File exportBookAt(OrmaDatabase orma, Book target, File targetDir) throws IOException {
        String filename = createFileName(target);
        File file = new File(targetDir, filename);

        saveBookToFile(orma, file, target);
        return file;
    }

    @NonNull
    private String createFileName(Book target) {
        SimpleDateFormat timeStampFormat = new SimpleDateFormat("yyyyMMdd_HHmmssSS");
        return timeStampFormat.format(new Date()) + "_" + target.name + ".ipynb";
    }

    public void doDeleteBook(OrmaDatabase orma, Book book1) {
        orma.deleteFromCell()
                .bookEq(book1)
                .execute();
        orma.deleteFromBook()
                .idEq(book1.id)
                .execute();
    }

    public File backupAndDelete(OrmaDatabase orma, Book target) throws IOException {
        File recycleDir = new File(getStoreDirectory(), "recyclebin");
        ensureDirExist(recycleDir);
        File result = exportBookAt(orma, target, recycleDir);
        doDeleteBook(orma, target);
        return result;
    }


    public void saveBookToFile(OrmaDatabase orma, File file, Book target) throws IOException {
        JsonWriter writer = new JsonWriter(new FileWriter(file));
        writer.beginObject();

        writer.name("cells");
        writer.beginArray();
        CellListAdapter.getCellRelation(orma, target)
                .selector()
                .executeAsObservable()
                .subscribe(cell -> {
                    cell.toJson(writer);
                });



        writer.endArray(); // end of cells array

            /*
 "metadata": {
  "kernelspec": {
   "display_name": "Python 2",
   "language": "python",
   "name": "python2"
  },
  "language_info": {
   "codemirror_mode": {
    "name": "ipython",
    "version": 2
   },
   "file_extension": ".py",
   "mimetype": "text/x-python",
   "name": "python",
   "nbconvert_exporter": "python",
   "pygments_lexer": "ipython2",
   "version": "2.7.11"
  }
 },

             */
        writer.name("metadata"); // begin metaata:
        writer.beginObject();

        writer.name("kernelspec")
                .beginObject()
                .name("display_name").value("Python 2")
                .name("language").value("python")
                .name("name").value("python2")
                .endObject();
        writer.name("lanbuage_info")
                .beginObject()
                .name("codemirror_mode")
                .beginObject()
                .name("name").value("ipython")
                .name("version").value(2)
                .endObject()
                .name("file_extension").value(".py")
                .name("mimetype").value("text/x-python")
                .name("name").value("python")
                .name("nbconvert_exporter").value("python")
                .name("pygments_lexer").value("ipython2")
                .name("version").value("2.7.11")
                .endObject();
        writer.endObject(); // end metadata;

            /*
             "nbformat": 4,
             "nbformat_minor": 0
             */
        writer.name("nbformat").value(4);
        writer.name("nbformat_minor").value(0);

        writer.endObject();
        writer.close();
    }

}
