package karino2.livejournal.com.meatpieday;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Environment;
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
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.gfx.android.orma.widget.OrmaListAdapter;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.reactivex.Completable;
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


        adapter = new RelodableOrmaListAdapter<Book>(this, orma.relationOfBook()) {

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
                .subscribe((inserter) -> {
                    adapter.reload();
                    /*
                    inserter.executeAsSingle(book)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(()->adapter.notifyDataSetChanged())
                            */

                });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(Menu.NONE, R.id.rename_book_item, Menu.NONE, "rename");
        menu.add(Menu.NONE, R.id.export_book_item, Menu.NONE, "export");
        menu.add(Menu.NONE, R.id.delete_book_item, Menu.NONE, "delete");
    }

    void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    void exportBook(Book target) {

        SimpleDateFormat timeStampFormat = new SimpleDateFormat("yyyyMMdd_HHmmssSS");
        String filename = timeStampFormat.format(new Date()) + "_" + target.name + ".ipynb";
        File file = new File(Environment.getExternalStorageDirectory(), filename);

        try {
            JsonWriter writer = new JsonWriter(new FileWriter(file));
            writer.beginObject();

            writer.name("cells");
            writer.beginArray();
            getOrmaDatabase().selectFromCell()
                    .bookEq(target)
                    .orderByViewOrderAsc()
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
        } catch (IOException e) {
            showMessage("JsonExport fail with IOExcetion: " + e.getMessage());
            e.printStackTrace();
        }

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
                    exportBook(book);
                    emitter.onSuccess(1);
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(x->showMessage("export done"));
                break;

            case R.id.delete_book_item:
                OrmaDatabase orma = getOrmaDatabase();
                orma.transactionAsCompletable( () -> {
                    orma.deleteFromCell()
                            .bookEq(book)
                            .execute();
                    orma.deleteFromBook()
                            .idEq(book.id)
                            .execute();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete(()->adapter.reload())
                .subscribe();
                break;

        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        super.onPrepareDialog(id, dialog, args);
        switch(id) {
            case RENAME_DIALOG_ID:
                EditText et = (EditText)dialog.findViewById(R.id.book_name_edit);
                et.setTag(args.getLong("BOOK_ID"));
                et.setText(args.getString("BOOK_NAME"));
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
                                long bookId = (long) et.getTag();
                                String newBookName = et.getText().toString();

                                getOrmaDatabase()
                                        .relationOfBook()
                                        .idEq(bookId)
                                        .updater()
                                        .name(newBookName)
                                        .executeAsSingle()
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe(x->adapter.reload());

                                        // .subscribe(x->adapter.notifyDataSetInvalidated());
                                        // seems not working. but add here anyway.
                                        // not working either
                                        // .subscribe(x->adapter.notifyDataSetChanged());


                                // database.renameBook(bookId, newBookName);
                                // cursor.requery();
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

    private OrmaDatabase getOrmaDatabase() {
        return OrmaDatabase.builder(this)
                    .build();
    }
}
