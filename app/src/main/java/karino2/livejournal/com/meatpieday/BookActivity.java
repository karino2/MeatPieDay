package karino2.livejournal.com.meatpieday;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.github.gfx.android.orma.widget.OrmaListAdapter;

import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class BookActivity extends AppCompatActivity {

    Book book;


    // temp code.
    final int REQUEST_IMAGE = 1;

    void setupBook(long bookid) {
        OrmaDatabase orma = buildOrmaDatabase();

        book = orma.selectFromBook()
                .idEq(bookid)
                .get(0);
    }

    OrmaDatabase db = null;
    private OrmaDatabase buildOrmaDatabase() {
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

        final OrmaDatabase orma = buildOrmaDatabase();

        Intent intent = getIntent();
        if(intent != null) {
            long id = getIntent().getLongExtra("BOOK_ID", -1);
            if(id == -1)
                throw new RuntimeException("No book ID.");
            setupBook(id);
        }

        ListView lv = (ListView)findViewById(R.id.listView);

        adapter = new OrmaListAdapter<Cell>(this, orma.relationOfCell().bookEq(book)) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                CellView view;
                if(convertView == null) {
                    view = (CellView)getLayoutInflater().inflate(R.layout.list_item, null);
                    Cell cell = getItem(position);
                    view.bindCell(cell);
                } else {
                    view = (CellView)convertView;
                }
                return view;
            }
        };
        lv.setAdapter(adapter);

        registerForContextMenu(lv);

        findViewById(R.id.buttonNew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(BookActivity.this, EditActivity.class);
                intent.putExtra("BOOK_ID", book.id);
                startActivity(intent);

                /*
                Cell cell = new Cell();
                cell.book = book;
                cell.cellType = Cell.CELL_TYPE_TEXT;
                cell.source = "This is test.\n Here is another test.";

                orma.prepareInsertIntoCellAsSingle()
                        .subscribeOn(Schedulers.io())
                        .subscribe(x->x.execute(cell));
                        */
            }
        });


        findViewById(R.id.buttonNewImage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Cell cell = new Cell();
                cell.book = book;
                cell.cellType = Cell.CELL_TYPE_IMAGE;
                cell.source = EMPTY_IMAGE_BASE64;

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

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if(((CellView)  ((AdapterView.AdapterContextMenuInfo)menuInfo).targetView).isImage()) {
            menu.add(Menu.NONE, R.id.edit_image_item, Menu.NONE, "edit");
        }
        menu.add(Menu.NONE, R.id.delete_item, Menu.NONE, "delete");
    }

    void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        Cell cell = ((CellView)info.targetView).getBoundCell();
        switch(item.getItemId()) {
            case R.id.edit_image_item:
                getSharedPreferences("state", MODE_PRIVATE)
                        .edit()
                        .putLong("WAIT_IMAGE_ID", cell.id)
                        .commit();

                showMessage("Send image to this app, then replace selected image.");
                break;
            case R.id.delete_item:
                Completable.fromAction(()-> {
                    buildOrmaDatabase()
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

            Cell cell = new Cell();
            cell.book = book;
            cell.cellType =Cell.CELL_TYPE_IMAGE;
            cell.source = png64;

            OrmaDatabase orma = buildOrmaDatabase();
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
