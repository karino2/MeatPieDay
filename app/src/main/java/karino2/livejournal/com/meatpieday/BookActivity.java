package karino2.livejournal.com.meatpieday;

import android.content.Intent;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.github.gfx.android.orma.widget.OrmaListAdapter;

import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

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
            db = OrmaDatabase.builder(this)
                    .build();
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
    OrmaListAdapter<Cell> adapter = null;

    @Override
    protected void onStart() {
        super.onStart();
        adapter.notifyDataSetChanged();
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
        }

        ListView lv = getListView();

        adapter = createListAdapter(orma);
        lv.setAdapter(adapter);

        lv.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
        lv.setMultiChoiceModeListener(createMultiChoiceModeListener());


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
                    actionMode.getMenu().findItem(R.id.edit_item).setEnabled(false);
                } else {
                    actionMode.getMenu().findItem(R.id.edit_item).setEnabled(true);
                }
                /*
                Cell cell = getOrmaDatabase().selectFromCell().idEq(l).get(0);
                Menu menu = actionMode.getMenu();
                menu.findItem()
                if(cell.cellType == Cell.CELL_TYPE_IMAGE) {

                } else {
                */
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
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(() -> adapter.notifyDataSetChanged());

                        actionMode.finish();
                        return true;
                    case R.id.edit_item:

                        long cellid = getListView().getCheckedItemIds()[0];
                        Cell cell = getOrmaDatabase().selectFromCell().idEq(cellid).get(0);

                        if(cell.cellType == Cell.CELL_TYPE_IMAGE) {
                            getSharedPreferences("state", MODE_PRIVATE)
                                    .edit()
                                    .putLong("WAIT_IMAGE_ID", cellid)
                                    .commit();

                            showMessage("Send image to this app, then replace selected image.");
                        } else {
                            Intent intent = new Intent(BookActivity.this, EditActivity.class);
                            intent.putExtra("BOOK_ID", book.id);
                            intent.putExtra("CELL_ID", cellid);
                            startActivity(intent);
                        }
                        actionMode.finish();
                        return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {

            }
        };
    }

    @NonNull
    private OrmaListAdapter<Cell> createListAdapter(final OrmaDatabase orma) {
        OrmaListAdapter<Cell> tmp;
        tmp = new OrmaListAdapter<Cell>(this, orma.relationOfCell().bookEq(book).orderByViewOrderAsc()) {

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
                Cell cell = getItem(position);
                view.bindCell(cell);
                return view;
            }
        };
        return tmp;
    }

    public static Cell createNewCell(OrmaDatabase orma, Book book1, int cellType, String source) {
        Cell cell = new Cell();
        cell.book = book1;
        cell.cellType = cellType;
        cell.source = source;
        try {
            cell.viewOrder = orma.selectFromCell().idEq(book1.id).maxByViewOrder() + 1;
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
