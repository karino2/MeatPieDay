package karino2.livejournal.com.meatpieday;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class BookListActivity extends AppCompatActivity {

    static final int RENAME_DIALOG_ID = 1;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.book_list_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_delete_all:
                (new Thread(new Runnable() {
                    @Override
                    public void run() {
                        getOrmaDatabase().deleteAll();
                    }
                })).start();

                return true;
            case R.id.menu_new:
                createNewBook();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    RelodableOrmaListAdapter<Book> adapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_list);

        ListView lv = (ListView)findViewById(R.id.listView);

        final OrmaDatabase orma = getOrmaDatabase();

        // temp code.

        try {
            if (orma.selectFromBook().execute()
                    .getCount() == 0) {

                createNewBook();
            }
        }
        catch(SQLiteConstraintException e) {
            showMessage("invalid sql table. please clear data from settings: " + e.getMessage());
        }

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Book book = (Book)view.getTag();
                Intent intent = new Intent(BookListActivity.this, BookActivity.class);
                intent.putExtra("BOOK_ID", book.id);
                startActivity(intent);
            }
        });


        adapter = new RelodableOrmaListAdapter<Book>(this, orma.relationOfBook().orderByCreatedTimeDesc()) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                Book book = this.getItem(position);
                TextView view;
                if(convertView == null) {
                    view = new TextView(BookListActivity.this);
                } else {
                    view = (TextView)convertView;
                }
                view.setTag(book);
                view.setText(book.name);
                return view;
            }
        };
        lv.setAdapter(adapter);

        registerForContextMenu(lv);

    }

    private void createNewBook() {
        OrmaDatabase orma = getOrmaDatabase();
        orma.prepareInsertIntoBookAsSingle()
                .subscribeOn(Schedulers.io())
                .doOnSuccess((inserter) -> {
                    Book book = new Book();
                    book.name = "(New Book)";
                    book.createdTime = new Date();
                    inserter.execute(book);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(Menu.NONE, R.id.rename_book_item, Menu.NONE, "rename");
        menu.add(Menu.NONE, R.id.export_book_item, Menu.NONE, "export");
        menu.add(Menu.NONE, R.id.share_book_item, Menu.NONE, "share");
        menu.add(Menu.NONE, R.id.delete_book_item, Menu.NONE, "delete");
    }

    void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        Book book = (Book)info.targetView.getTag();
        switch(item.getItemId()) {
            case R.id.rename_book_item:
                Bundle args = new Bundle();
                // info.targetView
                args.putLong("BOOK_ID", book.id);
                args.putString("BOOK_NAME", book.name);
                showDialog(RENAME_DIALOG_ID, args);

                break;
            case R.id.export_book_item:
                Single.create(emitter->{
                    getSender().exportBook(book);
                    emitter.onSuccess(1);
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(x->showMessage("export done"));
                break;
            case R.id.share_book_item:
                getSender().sendTo(book);
                break;
            case R.id.delete_book_item:
                OrmaDatabase orma = getOrmaDatabase();
                Single.fromCallable(() -> (new Exporter()).backupAndDelete(orma, book))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(f -> {
                            showMessage("File is backed up at: " + f.getAbsolutePath());
                        });
                break;

        }
        return super.onContextItemSelected(item);
    }

    @NonNull
    private BookSender getSender() {
        return new BookSender(this, getOrmaDatabase());
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        super.onPrepareDialog(id, dialog, args);
        switch(id) {
            case RENAME_DIALOG_ID:
                EditText et = (EditText)dialog.findViewById(R.id.book_name_edit);
                et.setTag(args.getLong("BOOK_ID"));
                et.setText(args.getString("BOOK_NAME"));
                et.setOnEditorActionListener((v, aid, ev) -> {
                    if(aid == EditorInfo.IME_ACTION_DONE) {
                        renameBook((EditText) v);
                        dialog.dismiss();
                        return true;
                    }
                    return false;
                });
                break;
        }
    }

    @Nullable
    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch(id) {
            case RENAME_DIALOG_ID:
                LayoutInflater factory = LayoutInflater.from(this);
                final View textEntryView = factory.inflate(R.layout.new_book_text_entry, null);
                return new AlertDialog.Builder(this).setTitle("Rename Book")
                        .setView(textEntryView)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                EditText et = (EditText) textEntryView.findViewById(R.id.book_name_edit);
                                renameBook(et);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                            }
                        })
                        .create();
        }
        return super.onCreateDialog(id, args);
    }

    private void renameBook(EditText et) {
        long bookId = (long) et.getTag();
        String newBookName = et.getText().toString();

        getOrmaDatabase()
                .relationOfBook()
                .idEq(bookId)
                .updater()
                .name(newBookName)
                .executeAsSingle()
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    static OrmaDatabase ormaDatabase;

    public static OrmaDatabase getOrmaDatabaseInstance(Context ctx) {
        if(ormaDatabase == null) {
            ormaDatabase = OrmaDatabase.builder(ctx)
                    .build();
        }
        return ormaDatabase;

    }

    private OrmaDatabase getOrmaDatabase() {
        return getOrmaDatabaseInstance(this);
    }

}
