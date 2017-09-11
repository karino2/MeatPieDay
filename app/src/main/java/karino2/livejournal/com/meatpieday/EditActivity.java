package karino2.livejournal.com.meatpieday;

import android.content.Intent;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class EditActivity extends AppCompatActivity {

    long bookId = -1;
    long cellId = -1;

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        outState.putLong("BOOK_ID", bookId);
        outState.putLong("CELL_ID", cellId);
        super.onSaveInstanceState(outState, outPersistentState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        bookId = savedInstanceState.getLong("BOOK_ID");
        cellId = savedInstanceState.getLong("CELL_ID");
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        EditText et = (EditText)findViewById(R.id.editText);
        et.setOnKeyListener((v, keyCode, e)-> {
            if(keyCode == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN &&
                    e.isShiftPressed()) {
                saveMarkdown();
                return true;
            }
            return false;
        });

        Intent intent = getIntent();
        if(intent != null) {
            bookId = intent.getLongExtra("BOOK_ID", -1);
            if(bookId == -1)
                throw new RuntimeException("No book ID.");

            cellId = intent.getLongExtra("CELL_ID", -1);
            et.setText(getCell(cellId).source);
        }




    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.edit_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.save_item:
                saveMarkdown();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    Cell getCell(long cellid) {
        OrmaDatabase orma = BookListActivity.getOrmaDatabaseInstance(this);

        if(cellid != -1) {
            return orma.selectFromCell().idEq(cellid).get(0);
        } else {
            Book book = orma.selectFromBook().idEq(bookId).get(0);
            return BookActivity.createNewCell(orma, book, Cell.CELL_TYPE_TEXT, "");
        }

    }

    private void saveMarkdown() {
        OrmaDatabase orma = BookListActivity.getOrmaDatabaseInstance(this);

        String text = ((EditText)findViewById(R.id.editText)).getText().toString();

        orma.transactionAsCompletable( () -> {
            Book book = orma.selectFromBook().idEq(bookId).get(0);


            if(cellId != -1) {
                orma.updateCell().idEq(cellId).source(text).execute();
            } else {
                Cell cell = getCell(cellId);
                cell.source = text;

                orma.insertIntoCell(cell);
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(()->finish());
    }
}
