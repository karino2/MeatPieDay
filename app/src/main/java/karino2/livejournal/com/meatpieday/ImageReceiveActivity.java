package karino2.livejournal.com.meatpieday;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Action;
import io.reactivex.schedulers.Schedulers;

public class ImageReceiveActivity extends AppCompatActivity {

    // TODO: saveInstanceState.
    Uri uri;
    String mimeType;
    Book book;


    CellListAdapter<Cell> adapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("state", MODE_PRIVATE);
        long bookId = prefs.getLong("BOOK_ID", -1);
        if(bookId == -1) {
            showMessage("No book is opend. Open window first.");
            finish();
            return;
        }

        Intent intent = getIntent();

        uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) {
            showMessage("not supported. getParcelableExtra fail.");
            finish();
            return;
        }


        book = getBook(bookId);
        if(book  == null) {
            showMessage("No book selected. Please select book first.");
            finish();
            return;
        }


        mimeType = intent.getType();

        setContentView(R.layout.activity_image_receive);
        ListView lv = (ListView)findViewById(R.id.listView);


        adapter = (CellListAdapter<Cell>) CellListAdapter.create(this, getOrmaDatabase(), book);
        lv.setAdapter(adapter);

        View addTailCell = getLayoutInflater().inflate(R.layout.add_tail_cell, null);
        addTailCell.findViewById(R.id.buttonAddTail).setOnClickListener(v-> newImageToTailAndFinish());
        lv.addFooterView(addTailCell);

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                CellView cv = (CellView)view;
                if(cv.isImage()) {
                    updateImageAndFinish(cv.getBoundCell());
                    return;
                }

            }
        });



        /*
        long cellId = 0;

        if (saveImage(cellId)) return;


        showMessage("replace image fail.");
        finish();
        */

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.image_receive_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.add_tail_menu:
                newImageToTailAndFinish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    Book getBook(long bookId) {
        OrmaDatabase orma = getOrmaDatabase();
        Book_Selector selector = orma.selectFromBook()
                .idEq(bookId);
        if(selector.isEmpty())
            return null;

        return selector.get(0);
    }

    String getPng64(Uri uri) {
        InputStream stream = null;
        try {
            stream = getContentResolver().openInputStream(uri);
            byte[] bytes = IOUtils.toByteArray(stream);
            return Base64.encodeToString(bytes, Base64.DEFAULT);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            showMessage("file not found: " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            showMessage("IOException: " + e.getMessage());
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private boolean updateImageAndFinish(Cell imgCell) {
        String png64 = getPng64(uri);
        if(png64 == null)
            return false;

        imgCell.source = png64;
        updateCellAndFinish(imgCell);
        return true;
    }


    private void insertCellAndFinish(Cell imgCell) {
        upsertCellAndFinish(()-> {
            getOrmaDatabase().insertIntoCell(imgCell);
        }, "add image.");
    }

    private void updateCellAndFinish(Cell imgCell) {
        upsertCellAndFinish(()-> {
            getOrmaDatabase().updateCell()
                    .idEq(imgCell.id)
                    .source(imgCell.source)
                    .execute();
        }, "replace image.");


        /*                .
        // this code add new cell below to selected cell. Is this correct?
        Completable.fromAction(()->{
            getOrmaDatabase().insertIntoCell(imgCell);
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(()-> {
                showMessage("replace image.");
                finish();
            });
            */
    }

    private void upsertCellAndFinish(Action action, String msg) {
        Completable.fromAction(action)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(()-> {
            showMessage(msg);
            finish();
        });
    }

    private boolean newImageToTailAndFinish() {
        String png64 = getPng64(uri);
        if(png64 == null)
            return false;

        OrmaDatabase orma= getOrmaDatabase();
        Cell cell = createNewImageCell(orma, book, png64);
        insertCellAndFinish(cell);

        return true;

    }

    public static Cell createNewImageCell(OrmaDatabase orma, Book book1,String source) {
        return BookActivity.createNewCell(orma, book1, Cell.CELL_TYPE_IMAGE, source);
    }


        private OrmaDatabase getOrmaDatabase() {
        return BookListActivity.getOrmaDatabaseInstance(this);
    }


    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
