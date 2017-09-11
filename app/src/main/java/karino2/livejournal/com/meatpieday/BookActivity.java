package karino2.livejournal.com.meatpieday;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
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

import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;

import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;

public class BookActivity extends AppCompatActivity {

    Book book;

    void setupBook(long bookid) {
        OrmaDatabase orma = getOrmaDatabase();
        book = orma.selectFromBook()
                .idEq(bookid)
                .get(0);
    }

    OrmaDatabase db = null;
    private OrmaDatabase getOrmaDatabase() {
        if(db == null)
            db = BookListActivity.getOrmaDatabaseInstance(this);
        return db;
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.share_book_item:
                getSender().sendTo(book);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @NonNull
    private BookSender getSender() {
        return new BookSender(this, getOrmaDatabase());
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book);

        final OrmaDatabase orma = getOrmaDatabase();

        Intent intent = getIntent();
        if(intent != null) {
            long id = getIntent().getLongExtra("BOOK_ID", -1);
            if(id == -1)
                throw new RuntimeException("No book ID.");
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
                        long id = lv.getSelectedItemId();
                        if(id == -1) {
                            return false;
                        }
                        Cell cell =  getOrmaDatabase().selectFromCell().idEq(id).get(0);
                        insertNewMarkdownAbove(cell);
                        return true;
                }
            }
            return false;
        });

        findViewById(R.id.buttonNew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(BookActivity.this, EditActivity.class);
                intent.putExtra("BOOK_ID", book.id);
                startActivity(intent);
            }
        });



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

                        Completable.fromAction(()-> {
                            getOrmaDatabase()
                                    .deleteFromCell()
                                    .idIn(ids)
                                    .execute();

                            // TODO: update viewOrder here.
                        }).subscribeOn(Schedulers.io())
                                .subscribe();

                        actionMode.finish();
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
            startActivity(intent);
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
        Cell cell = createCell(book, cellType, source, below.viewOrder);

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
        insertCellAbove(target, Cell.CELL_TYPE_TEXT, "(empty)");

    }
    private void insertNewImageAbove(Cell target) {
        insertCellAbove(target, Cell.CELL_TYPE_IMAGE, EMPTY_IMAGE_BASE64);
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
