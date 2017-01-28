package karino2.livejournal.com.meatpieday;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.github.gfx.android.orma.Relation;

import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class BookActivity extends AppCompatActivity {

    Book book;


    // temp code.
    final int REQUEST_IMAGE = 1;

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
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        outState.putLong("BOOK_ID", book.id);
        super.onSaveInstanceState(outState, outPersistentState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        setupBook(savedInstanceState.getLong("BOOK_ID"));
    }
    MyOrmaListAdapter<Cell> adapter = null;


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
        }

        ListView lv = getListView();

        adapter = createListAdapter(orma);
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

        findViewById(R.id.buttonNew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(BookActivity.this, EditActivity.class);
                intent.putExtra("BOOK_ID", book.id);
                startActivity(intent);
            }
        });


        findViewById(R.id.buttonNewImage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Cell cell = createNewCell(orma, Cell.CELL_TYPE_IMAGE, EMPTY_IMAGE_BASE64);

                orma.prepareInsertIntoCellAsSingle()
                        .subscribeOn(Schedulers.io())
                        .subscribe(x->x.execute(cell));
            }
        });

        findViewById(R.id.buttonDebug).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setType("image/png");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, "Select PNG"), REQUEST_IMAGE);
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

            SharedPreferences prefs = getSharedPreferences("state", MODE_PRIVATE);

            if (prefs.getLong("WAIT_IMAGE_ID", -1) == cell.id) {
                prefs.edit()
                    .putLong("WAIT_IMAGE_ID", -1)
                    .commit();

                showMessage("Cancel wait image.");
            }else {
                prefs.edit()
                    .putLong("WAIT_IMAGE_ID", cell.id)
                    .commit();
                showMessage("Send image to this app, then replace selected image.");
            }
        } else {
            Intent intent = new Intent(BookActivity.this, EditActivity.class);
            intent.putExtra("BOOK_ID", book.id);
            intent.putExtra("CELL_ID", cell.id);
            startActivity(intent);
        }
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
    private void insertCellAbove(Cell below, int cellType, String source) {
        Cell cell = new Cell();
        cell.book = book;
        cell.cellType = cellType;
        cell.source = source;
        cell.viewOrder = below.viewOrder;

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

    class MyOrmaListAdapter<CellModel extends Cell> extends  RelodableOrmaListAdapter<CellModel> {

        public MyOrmaListAdapter(@NonNull Context context, @NonNull Relation<CellModel, ?> relation) {
            super(context, relation);
        }


        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            return this.delegate.getItem(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CellView view;
            if(convertView == null) {
                view = (CellView)getLayoutInflater().inflate(R.layout.list_item, null);

                    /*
                    view.setOnClickListener((v) -> {
                        CellView cv = (CellView)v;
                        if(view.isImage()) {
                            return;
                        }
                        Intent intent = new Intent(BookActivity.this, EditActivity.class);
                        intent.putExtra("BOOK_ID", book.id);
                        intent.putExtra("CELL_ID", cv.getBoundCell().id);
                        startActivity(intent);
                    });
                    */
            } else {
                view = (CellView)convertView;
            }
            CellModel cell = getItem(position);
            view.bindCell(cell);
            return view;
        }
    }

    @NonNull
    private MyOrmaListAdapter<Cell> createListAdapter(final OrmaDatabase orma) {
        return new MyOrmaListAdapter(this, orma.relationOfCell().bookEq(book).orderByViewOrderAsc());
    }

    public static Cell createNewCell(OrmaDatabase orma, Book book1, int cellType, String source) {
        Cell cell = new Cell();
        cell.book = book1;
        cell.cellType = cellType;
        cell.source = source;
        try {
            cell.viewOrder = orma.selectFromCell().bookEq(book1).maxByViewOrder() + 1;
        }catch(NullPointerException e) {
            cell.viewOrder = 1; // no cell in this book yet. so assign 1.
        }

        return cell;
    }

    Cell createNewCell(OrmaDatabase orma, int cellType, String source) {
        return createNewCell(orma, book, cellType, source);
    }

    private ListView getListView() {
        return (ListView)findViewById(R.id.listView);
    }

    void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    /*
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        Cell cell = ((CellView)info.targetView).getBoundCell();
        switch(item.getItemId()) {
            case R.id.edit_item:
                getSharedPreferences("state", MODE_PRIVATE)
                        .edit()
                        .putLong("WAIT_IMAGE_ID", cell.id)
                        .commit();

                showMessage("Send image to this app, then replace selected image.");
                break;
            case R.id.delete_item:
                Completable.fromAction(()-> {
                    getOrmaDatabase()
                            .deleteFromCell()
                            .idEq(cell.id)
                            .execute();
                }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> adapter.notifyDataSetChanged());

                break;
        }



        return super.onContextItemSelected(item);
    }
    */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK) {
            if(requestCode == REQUEST_IMAGE) {
                handleReceiveImage(data);

                return;
            }
        }
    }

    private void handleReceiveImage(Intent data) {
        InputStream stream = null;
        try {
            stream = getContentResolver().openInputStream(data.getData());

            byte[] bytes = IOUtils.toByteArray(stream);
            String png64 = Base64.encodeToString(bytes, Base64.DEFAULT);


            OrmaDatabase orma = getOrmaDatabase();
            Cell cell = createNewCell(orma, Cell.CELL_TYPE_IMAGE, png64);

            orma.prepareInsertIntoCellAsSingle()
                    .subscribeOn(Schedulers.io())
                    .subscribe(x -> x.execute(cell));

            /*
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(x->adapter.notifyDataSetChanged());
                    */


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(stream != null){
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    final String EMPTY_IMAGE_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAoAAAADICAYAAAB4WVALAAAABHNCSVQICAgIfAhkiAAAA75JREFUeJzt1jEBwCAAwDDAvy ZkYAdcbEcTBT079z13AACQsf4OAADgWwYQACDGAAIAxBhAAIAYAwgAEGMAAQBiDCAAQIwBBACIMYAAADEGEAAgxgACAMQY QACAGAMIABBjAAEAYgwgAECMAQQAiDGAAAAxBhAAIMYAAgDEGEAAgBgDCAAQYwABAGIMIABAjAEEAIgxgAAAMQYQACDGAA IAxBhAAIAYAwgAEGMAAQBiDCAAQIwBBACIMYAAADEGEAAgxgACAMQYQACAGAMIABBjAAEAYgwgAECMAQQAiDGAAAAxBhAA IMYAAgDEGEAAgBgDCAAQYwABAGIMIABAjAEEAIgxgAAAMQYQACDGAAIAxBhAAIAYAwgAEGMAAQBiDCAAQIwBBACIMYAAAD EGEAAgxgACAMQYQACAGAMIABBjAAEAYgwgAECMAQQAiDGAAAAxBhAAIMYAAgDEGEAAgBgDCAAQYwABAGIMIABAjAEEAIgx gAAAMQYQACDGAAIAxBhAAIAYAwgAEGMAAQBiDCAAQIwBBACIMYAAADEGEAAgxgACAMQYQACAGAMIABBjAAEAYgwgAECMAQ QAiDGAAAAxBhAAIMYAAgDEGEAAgBgDCAAQYwABAGIMIABAjAEEAIgxgAAAMQYQACDGAAIAxBhAAIAYAwgAEGMAAQBiDCAA QIwBBACIMYAAADEGEAAgxgACAMQYQACAGAMIABBjAAEAYgwgAECMAQQAiDGAAAAxBhAAIMYAAgDEGEAAgBgDCAAQYwABAG IMIABAjAEEAIgxgAAAMQYQACDGAAIAxBhAAIAYAwgAEGMAAQBiDCAAQIwBBACIMYAAADEGEAAgxgACAMQYQACAGAMIABBj AAEAYgwgAECMAQQAiDGAAAAxBhAAIMYAAgDEGEAAgBgDCAAQYwABAGIMIABAjAEEAIgxgAAAMQYQACDGAAIAxBhAAIAYAw gAEGMAAQBiDCAAQIwBBACIMYAAADEGEAAgxgACAMQYQACAGAMIABBjAAEAYgwgAECMAQQAiDGAAAAxBhAAIMYAAgDEGEAA gBgDCAAQYwABAGIMIABAjAEEAIgxgAAAMQYQACDGAAIAxBhAAIAYAwgAEGMAAQBiDCAAQIwBBACIMYAAADEGEAAgxgACAM QYQACAGAMIABBjAAEAYgwgAECMAQQAiDGAAAAxBhAAIMYAAgDEGEAAgBgDCAAQYwABAGIMIABAjAEEAIgxgAAAMQYQACDG AAIAxBhAAIAYAwgAEGMAAQBiDCAAQMwDTJIFIhe4sbUAAAAASUVORK5CYII=";
}
