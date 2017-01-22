package karino2.livejournal.com.meatpieday;

import android.content.Intent;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class EditActivity extends AppCompatActivity {

    long bookId = -1;
    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        outState.putLong("BOOK_ID", bookId);
        super.onSaveInstanceState(outState, outPersistentState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        bookId = savedInstanceState.getLong("BOOK_ID");
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        Intent intent = getIntent();
        if(intent != null) {
            bookId = getIntent().getLongExtra("BOOK_ID", -1);
            if(bookId == -1)
                throw new RuntimeException("No book ID.");
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

    private void saveMarkdown() {
        OrmaDatabase orma = OrmaDatabase.builder(this)
                .build();

        String text = ((EditText)findViewById(R.id.editText)).getText().toString();

        orma.transactionAsCompletable( () -> {
            Book book = orma.selectFromBook().idEq(bookId).get(0);

            Cell cell = new Cell();
            cell.book = book;
            cell.cellType = Cell.CELL_TYPE_TEXT;
            cell.source = text;

            orma.insertIntoCell(cell);

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(()->finish());
        /*
        orma.prepareInsertIntoCellAsSingle()
                .subscribeOn(Schedulers.io())
                .subscribe(x->x.execute(cell));
                */


        // finishActivity(RESULT_OK);
    }
}
