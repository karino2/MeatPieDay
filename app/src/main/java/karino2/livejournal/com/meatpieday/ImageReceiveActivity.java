package karino2.livejournal.com.meatpieday;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Toast;

import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class ImageReceiveActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
/*
        getSharedPreferences("state", MODE_PRIVATE)
                .edit()
                .putLong("WAIT_IMAGE_ID", cell.id)
                .commit();


 */

        SharedPreferences prefs = getSharedPreferences("state", MODE_PRIVATE);
        long id = prefs.getLong("WAIT_IMAGE_ID", -1);
        if(id == -1) {
            showMessage("No wait image cell. Ignore.");
            finish();
        }

        Intent intent = getIntent();

        Uri uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) {
            showMessage("not supported. getParcelableExtra fail.");
            finish();
            return;
        }
        String mimeType = intent.getType();

        InputStream stream = null;
        try {
            stream = getContentResolver().openInputStream(uri);
            byte[] bytes = IOUtils.toByteArray(stream);
            String png64 = Base64.encodeToString(bytes, Base64.DEFAULT);

            Completable.fromAction(
                    () -> {
                        //noinspection WrongThread
                        OrmaDatabase.builder(ImageReceiveActivity.this)
                                .build()
                                .updateCell()
                                .idEq(id)
                                .source(png64)
                                .execute();
                    }
            )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe( ()-> {
                getSharedPreferences("state", MODE_PRIVATE)
                        .edit()
                        .putLong("WAIT_IMAGE_ID", -1)
                        .commit();
                showMessage("replace image.");
                finish();
            });


            return;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        showMessage("replace image fail.");
        finish();
    }
    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
