package karino2.livejournal.com.meatpieday;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import java.util.Date;

import io.github.karino2.tegashiki.TegashikiDialog;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import kotlin.Unit;

public class EditActivity extends AppCompatActivity {

    long bookId = -1;
    long cellId = -1;

    final int DIALOG_ID_TEGASHIKI=1;

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
            case R.id.tegashiki_item:
                EditText et = (EditText)findViewById(R.id.editText);
                int curPos = et.getSelectionEnd();

                CharSequence text = et.getText();
                et.setText(text.subSequence(0, curPos) + "$$  $$"  + text.subSequence(curPos, text.length()));
                et.setSelection(curPos+3);

                showDialog(DIALOG_ID_TEGASHIKI);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    void insertTextToCurrentPos(String text) {
        EditText et = (EditText)findViewById(R.id.editText);
        int curPos = et.getSelectionEnd();

        CharSequence charSeq = et.getText();
        et.setText(charSeq.subSequence(0, curPos) + text + charSeq.subSequence(curPos, charSeq.length()));
        et.setSelection(curPos+text.length());
    }



    @Override
    protected Dialog onCreateDialog(int id) {
        switch(id) {
            case DIALOG_ID_TEGASHIKI:
                Dialog dialog =  new TegashikiDialog(this);
                dialog.getWindow().setGravity(Gravity.BOTTOM);
                return dialog;
        }
        return super.onCreateDialog(id);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch(id) {
            case DIALOG_ID_TEGASHIKI:
                TegashikiDialog tegashiki = (TegashikiDialog)dialog;
                tegashiki.setSendResultListener((tex)-> {
                    insertTextToCurrentPos(tex);
                    tegashiki.clearAll();
                    return Unit.INSTANCE;
                });
                tegashiki.startListening();
                return;
        }
        super.onPrepareDialog(id, dialog);
    }

    Cell newCell(String source) {
        OrmaDatabase orma = BookListActivity.getOrmaDatabaseInstance(this);
        Book book = orma.selectFromBook().idEq(bookId).get(0);
        return BookActivity.createNewCell(orma, book, Cell.CELL_TYPE_TEXT, source);
    }

    Cell getCell(long cellid) {
        OrmaDatabase orma = BookListActivity.getOrmaDatabaseInstance(this);

        if(cellid != -1) {
            return orma.selectFromCell().idEq(cellid).get(0);
        } else {
            return newCell("");
        }

    }

    private void saveMarkdown() {
        OrmaDatabase orma = BookListActivity.getOrmaDatabaseInstance(this);

        String text = ((EditText)findViewById(R.id.editText)).getText().toString();

        orma.transactionAsCompletable( () -> {
            Book book = orma.selectFromBook().idEq(bookId).get(0);


            if(cellId != -1) {
                orma.updateCell().idEq(cellId)
                        .source(text)
                        .lastModified((new Date()).getTime())
                        .execute();
            } else {
                Cell cell = newCell(text);
                orma.insertIntoCell(cell);
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(()->finish());
    }
}
