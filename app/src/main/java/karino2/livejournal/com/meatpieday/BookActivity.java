package karino2.livejournal.com.meatpieday;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class BookActivity extends AppCompatActivity {

    Book book;
    final static int REQUEST_PICK_IPYNB = 1;

    void setupBook(long bookid) {
        OrmaDatabase orma = getOrmaDatabase();
        book = orma.selectFromBook()
                .idEq(bookid)
                .get(0);

        getSupportActionBar().setTitle(book.name);
    }

    OrmaDatabase db = null;
    private OrmaDatabase getOrmaDatabase() {
        if(db == null)
            db = BookListActivity.getOrmaDatabaseInstance(this);
        return db;
    }

    @Override
    protected void onStart() {
        if(needMenuUpdate) {
            supportInvalidateOptionsMenu();
            needMenuUpdate = false;
        }
        super.onStart();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLong("BOOK_ID", book.id);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        setupBook(savedInstanceState.getLong("BOOK_ID", -1));
    }
    CellListAdapter<Cell> adapter = null;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.book_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    boolean isSyncable = false;

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        prepareSyncMenuItem(menu);


        MenuItem item = menu.findItem(R.id.create_header_item);
        ClipboardManager clipboard = getClipboardManager();
        item.setVisible(clipboard.hasPrimaryClip());
        return super.onPrepareOptionsMenu(menu);
    }

    private ClipboardManager getClipboardManager() {
        return (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
    }

    private void prepareSyncMenuItem(Menu menu) {
        MenuItem item = menu.findItem(R.id.sync_read_book_item);
        item.setVisible(isSyncable);
        if(book != null) {
            CellListAdapter.getCellRelation(getOrmaDatabase(), book)
                    .selector()
                    .executeAsObservable()
                    .firstElement()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(cell ->{
                        isSyncable = hasIssueId(cell);
                        item.setVisible(isSyncable);
                    });

        }
    }

    private boolean hasIssueId(Cell firstCell) {
        if(firstCell.cellType != Cell.CELL_TYPE_TEXT)
            return false;

        String body = firstCell.source;
        if(Pattern.matches(" *IssueId: *[0-9]+ *", body))
            return true;
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.share_book_item:
                getSender().sendTo(book);
                return true;
            case R.id.create_header_item:
                createNewHeaderCellFromClipboard();
                return true;
            case R.id.sync_read_book_item:
                showMessage("Choose ipynb file");
                Intent intent = BookListActivity.createIPYNBPickIntent();
                startActivityForResult(intent, REQUEST_PICK_IPYNB);
                return true;
            case R.id.delete_book_item:
                OrmaDatabase orma = getOrmaDatabase();
                Single.fromCallable(() -> (new Exporter()).backupAndDelete(orma, book))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                .map(f -> {
                    showMessage("File is backed up at: " + f.getAbsolutePath());
                    return 1;
                }).subscribe( (i) -> finish());
        }
        return super.onOptionsItemSelected(item);
    }

    private void createNewHeaderCellFromClipboard() {
        ClipboardManager clipboard = getClipboardManager();

        ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
        insertCellAt(1, Cell.CELL_TYPE_TEXT, item.getText().toString());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // REQUEST_PICK_IPYNB
        switch(requestCode) {
            case REQUEST_PICK_IPYNB:
                if(resultCode != RESULT_OK)
                    return;

                Uri uri = data.getData();
                try {
                    InputStream stream = getContentResolver().openInputStream(uri);

                    Importer importer = new Importer();
                    importer.syncReadIpynb(getOrmaDatabase(), book, stream)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(()-> {
                                showMessage("Sync done");
                                stream.close();
                            });
                }catch(IOException ioe) {
                    showMessage("ipyng import fail. uri: "+ uri.toString() + ",  message:" + ioe.getMessage());
                }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @NonNull
    private BookSender getSender() {
        return new BookSender(this, getOrmaDatabase());
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final OrmaDatabase orma = getOrmaDatabase();

        Intent intent = getIntent();
        if(intent != null) {
            long id = getIntent().getLongExtra("BOOK_ID", -1);
            if(id == -1) {
                // Up from EditActivity case, this is legal now.
                id  = getPrefs().getLong("BOOK_ID", -1);
                if(id == -1) {
                    throw new RuntimeException("No book ID.");
                }
            }
            setupBook(id);
            getPrefs().edit()
                    .putLong("BOOK_ID", id)
                    .commit();
        }

        ListView lv = getListView();

        adapter = (CellListAdapter<Cell>) CellListAdapter.create(this, orma, book);
        lv.setAdapter(adapter);

        lv.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
        lv.setMultiChoiceModeListener(createMultiChoiceModeListener());

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                CellView cv = (CellView)view;
                handleEditCell(cv.getBoundCell());
            }
        });

        lv.setOnKeyListener((v, keyCode, e) -> {
            if(e.getAction() == KeyEvent.ACTION_UP) {
                switch(keyCode) {
                    case KeyEvent.KEYCODE_A:
                    case KeyEvent.KEYCODE_B:
                    {
                        long id = lv.getSelectedItemId();
                        if (id == -1) {
                            return false;
                        }

                        Cell cell = getOrmaDatabase().selectFromCell().idEq(id).get(0);
                        long insertPos = cell.viewOrder;
                        if(keyCode == KeyEvent.KEYCODE_B) {
                            insertPos += 1; // insert below.
                        }
                        insertNewMarkdownAt(insertPos);
                        return true;
                    }

                }
            }
            return false;
        });

        findViewById(R.id.buttonNew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(BookActivity.this, EditActivity.class);
                intent.putExtra("BOOK_ID", book.id);
                startEditActivity(intent);
            }
        });



    }

    boolean needMenuUpdate = false;

    private void startEditActivity(Intent intent) {
        needMenuUpdate =true;
        startActivity(intent);
    }

    @NonNull
    private AbsListView.MultiChoiceModeListener createMultiChoiceModeListener() {
        return new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode actionMode, int i, long l, boolean b) {
                if(getListView().getCheckedItemCount() != 1) {
                    actionMode.getMenu().findItem(R.id.edit_item).setVisible(false);
                    actionMode.getMenu().findItem(R.id.insert_image_item).setVisible(false);
                    actionMode.getMenu().findItem(R.id.insert_markdown_item).setVisible(false);
                } else {
                    actionMode.getMenu().findItem(R.id.edit_item).setVisible(true);
                    actionMode.getMenu().findItem(R.id.insert_image_item).setVisible(true);
                    actionMode.getMenu().findItem(R.id.insert_markdown_item).setVisible(true);
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                MenuInflater inflater = actionMode.getMenuInflater();
                inflater.inflate(R.menu.cell_list_context_menu, menu);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {

                switch(menuItem.getItemId()) {
                    case R.id.delete_item:
                        long[] cellids = getListView().getCheckedItemIds();
                        ArrayList<Long> ids = new ArrayList<Long>();
                        for(long id : cellids)
                        {
                            ids.add(id);
                        }

                        // change action mode cause adapter related operation, which cause race condition.
                        // call before delete execute.
                        actionMode.finish();

                        Completable.fromAction(()-> {
                            getOrmaDatabase()
                                    .deleteFromCell()
                                    .idIn(ids)
                                    .execute();

                            // TODO: update viewOrder here.
                        }).subscribeOn(Schedulers.io())
                                .subscribe(()->{
                                    supportInvalidateOptionsMenu();
                                });

                        return true;
                    case R.id.insert_image_item:{
                        Cell cell = getSelectedCell();
                        insertNewImageAbove(cell);

                        actionMode.finish();
                        return true;
                    }
                    case R.id.insert_markdown_item: {
                        Cell cell = getSelectedCell();
                        insertNewMarkdownAbove(cell);

                        actionMode.finish();
                        return true;
                    }
                    case R.id.edit_item: {

                        Cell cell = getSelectedCell();

                        handleEditCell(cell);
                        actionMode.finish();
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {

            }
        };
    }

    private void handleEditCell(Cell cell) {
        if (cell.cellType == Cell.CELL_TYPE_IMAGE) {
            showMessage("NYI: image view mode.");
        } else {
            Intent intent = new Intent(BookActivity.this, EditActivity.class);
            intent.putExtra("BOOK_ID", book.id);
            intent.putExtra("CELL_ID", cell.id);
            startEditActivity(intent);
        }
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences("state", MODE_PRIVATE);
    }

    private void moveCellViewOrderAfter(long startViewOrder) {
        OrmaDatabase orma = getOrmaDatabase();
        Cell_Schema schema = Cell_Schema.INSTANCE;
        Cursor cursor = orma.selectFromCell().bookEq(book).viewOrderGe(startViewOrder)
                .executeWithColumns(schema.id.getQualifiedName(), schema.viewOrder.getQualifiedName());


        ArrayList<long[]> idViewOrderParis = new ArrayList<>();

        try {
            if(!cursor.moveToFirst())
                return;


            int idIndex = cursor.getColumnIndex(schema.id.name);
            int viewOrderIndex = cursor.getColumnIndex(schema.viewOrder.name);

            do {
                 long[] pair  = new long[]{
                  cursor.getLong(idIndex),
                         cursor.getLong(viewOrderIndex)
                };
                idViewOrderParis.add(pair);
            }while(cursor.moveToNext());
        }
        finally {
            cursor.close();
        }

        for(long[] pair : idViewOrderParis) {
            orma.updateCell()
                    .idEq(pair[0])
                    .viewOrder(pair[1]+1)
                    .execute();
        }

    }

    static Cell createCell(Book book, int cellType, String source, long viewOrder) {
        Cell cell = new Cell();
        cell.book = book;
        cell.cellType = cellType;
        cell.source = source;
        cell.viewOrder = viewOrder;
        cell.lastModified = (new Date()).getTime();
        return cell;
    }


    private void insertCellAbove(Cell below, int cellType, String source) {
        insertCellAt(below.viewOrder, cellType, source);
    }

    private void insertCellAt(long insertPos, int cellType, String source) {
        Cell cell = createCell(book, cellType, source, insertPos);

        OrmaDatabase orma = getOrmaDatabase();
        orma.transactionAsCompletable(
                ()-> {
                    moveCellViewOrderAfter(cell.viewOrder);
                    orma.insertIntoCell(cell);
                }
        ).subscribeOn(Schedulers.io())
                .subscribe();
    }

    private void insertNewMarkdownAbove(Cell target) {
        insertNewMarkdownAt(target.viewOrder);

    }
    private void insertNewImageAbove(Cell target) {
        insertCellAbove(target, Cell.CELL_TYPE_IMAGE, EMPTY_IMAGE_BASE64);
    }


    public void insertNewMarkdownAt(long insertAt) {
        insertCellAt(insertAt, Cell.CELL_TYPE_TEXT, "(empty)");
    }


    @NonNull
    private Cell getSelectedCell() {
        long cellid = getListView().getCheckedItemIds()[0];
        return getOrmaDatabase().selectFromCell().idEq(cellid).get(0);
    }

    public static Cell createNewCell(OrmaDatabase orma, Book book1, int cellType, String source) {
        long viewOrder =1;
        try {
            viewOrder = orma.selectFromCell().bookEq(book1).maxByViewOrder() + 1;
        }catch(NullPointerException e) {
            viewOrder = 1; // no cell in this book yet. so assign 1.
        }
        Cell cell = createCell(book1, cellType, source, viewOrder);

        return cell;
    }

    private ListView getListView() {
        return (ListView)findViewById(R.id.listView);
    }

    void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }



    final String EMPTY_IMAGE_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAoAAAADICAYAAAB4WVALAAAABHNCSVQICAgIfAhkiAAAA75JREFUeJzt1jEBwCAAwDDAvy ZkYAdcbEcTBT079z13AACQsf4OAADgWwYQACDGAAIAxBhAAIAYAwgAEGMAAQBiDCAAQIwBBACIMYAAADEGEAAgxgACAMQY QACAGAMIABBjAAEAYgwgAECMAQQAiDGAAAAxBhAAIMYAAgDEGEAAgBgDCAAQYwABAGIMIABAjAEEAIgxgAAAMQYQACDGAA IAxBhAAIAYAwgAEGMAAQBiDCAAQIwBBACIMYAAADEGEAAgxgACAMQYQACAGAMIABBjAAEAYgwgAECMAQQAiDGAAAAxBhAA IMYAAgDEGEAAgBgDCAAQYwABAGIMIABAjAEEAIgxgAAAMQYQACDGAAIAxBhAAIAYAwgAEGMAAQBiDCAAQIwBBACIMYAAAD EGEAAgxgACAMQYQACAGAMIABBjAAEAYgwgAECMAQQAiDGAAAAxBhAAIMYAAgDEGEAAgBgDCAAQYwABAGIMIABAjAEEAIgx gAAAMQYQACDGAAIAxBhAAIAYAwgAEGMAAQBiDCAAQIwBBACIMYAAADEGEAAgxgACAMQYQACAGAMIABBjAAEAYgwgAECMAQ QAiDGAAAAxBhAAIMYAAgDEGEAAgBgDCAAQYwABAGIMIABAjAEEAIgxgAAAMQYQACDGAAIAxBhAAIAYAwgAEGMAAQBiDCAA QIwBBACIMYAAADEGEAAgxgACAMQYQACAGAMIABBjAAEAYgwgAECMAQQAiDGAAAAxBhAAIMYAAgDEGEAAgBgDCAAQYwABAG IMIABAjAEEAIgxgAAAMQYQACDGAAIAxBhAAIAYAwgAEGMAAQBiDCAAQIwBBACIMYAAADEGEAAgxgACAMQYQACAGAMIABBj AAEAYgwgAECMAQQAiDGAAAAxBhAAIMYAAgDEGEAAgBgDCAAQYwABAGIMIABAjAEEAIgxgAAAMQYQACDGAAIAxBhAAIAYAw gAEGMAAQBiDCAAQIwBBACIMYAAADEGEAAgxgACAMQYQACAGAMIABBjAAEAYgwgAECMAQQAiDGAAAAxBhAAIMYAAgDEGEAA gBgDCAAQYwABAGIMIABAjAEEAIgxgAAAMQYQACDGAAIAxBhAAIAYAwgAEGMAAQBiDCAAQIwBBACIMYAAADEGEAAgxgACAM QYQACAGAMIABBjAAEAYgwgAECMAQQAiDGAAAAxBhAAIMYAAgDEGEAAgBgDCAAQYwABAGIMIABAjAEEAIgxgAAAMQYQACDG AAIAxBhAAIAYAwgAEGMAAQBiDCAAQMwDTJIFIhe4sbUAAAAASUVORK5CYII=";
}
