package karino2.livejournal.com.meatpieday;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ActionMode;
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
        bookId = savedInstanceState.getLong("BOOK_ID", bookId);
        cellId = savedInstanceState.getLong("CELL_ID", cellId);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        EditText et = (EditText)findViewById(R.id.editText);
        et.setOnKeyListener((v, keyCode, e)-> {
            if(keyCode == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN &&
                    e.isShiftPressed()) {
                saveMarkdown();
                return true;
            }
            return false;
        });

        et.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                menu.add(Menu.NONE, R.id.action_link, Menu.NONE, "Link");
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                if(menuItem.getItemId() == R.id.action_link) {
                    int start = et.getSelectionStart();
                    int end = et.getSelectionEnd();
                    int min = Math.min(start, end);
                    int max = Math.max(start, end);

                    CharSequence link = "";

                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);

                    link = item.getText();
                    if(link == null) {
                        link = "";
                    }

                    CharSequence text = et.getText();

                    CharSequence target = text.subSequence(min, max);
                    et.setText(text.subSequence(0, min) + "[" + target + "](" + link + ")"  + text.subSequence(max, text.length()));

                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {

            }
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
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
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
