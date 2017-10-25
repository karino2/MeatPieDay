package karino2.livejournal.com.meatpieday;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by _ on 2017/04/14.
 */

public class BookSender {
    OrmaDatabase db;
    Context context;

    public BookSender(Context ctx, OrmaDatabase db) {
        this.context = ctx;
        this.db = db;
    }

    public void exportBook(Book target) {
        try {
            Exporter exporter = new Exporter();
            exporter.exportBook(db, target);
        } catch (IOException e) {
            showMessage("JsonExport fail with IOException: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public File exportBookForShare(Book book) throws IOException {
        Exporter exporter = new Exporter();

        return  exporter.exportBookForShare(db, book);
    }

    private void sendToFile(File exported) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/x-ipynb+json");
        Uri fileUri = FileProvider.getUriForFile(context,
                BuildConfig.APPLICATION_ID+".provider",
                exported);
        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
        context.startActivity(intent);
    }


    public void sendTo(Book book) {
        Single.fromCallable(() -> exportBookForShare(book))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(f ->
                        sendToFile(f));

    }



    void showMessage(String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }


}
