package karino2.livejournal.com.meatpieday;

import android.os.Environment;

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
    String folderName;

    public static void ensureDirExist(File dir) throws IOException {
        if(!dir.exists()) {
            if(!dir.mkdir()){
                throw new IOException();
            }
        }
    }

    public File exportBook(OrmaDatabase orma, Book target) throws IOException {
        SimpleDateFormat timeStampFormat = new SimpleDateFormat("yyyyMMdd_HHmmssSS");
        String filename = timeStampFormat.format(new Date()) + "_" + target.name + ".ipynb";
        File file = new File(Environment.getExternalStorageDirectory(), filename);

        saveBookToFile(orma, file, target);
        return file;
    }


    public void saveBookToFile(OrmaDatabase orma, File file, Book target) throws IOException {
        JsonWriter writer = new JsonWriter(new FileWriter(file));
        writer.beginObject();

        writer.name("cells");
        writer.beginArray();
        BookActivity.getCellRelation(orma, target)
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
