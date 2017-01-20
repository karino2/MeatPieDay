package karino2.livejournal.com.meatpieday;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.github.gfx.android.orma.widget.OrmaListAdapter;

public class BookActivity extends AppCompatActivity {

    // need to restore on onRestoreInstanceState;
    Book book;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book);

        final OrmaDatabase orma = OrmaDatabase.builder(this)
                .build();

        Intent intent = getIntent();
        if(intent != null) {
            long id = getIntent().getLongExtra("BOOK_ID", -1);
            if(id == -1)
                throw new RuntimeException("No book ID.");
            book = orma.selectFromBook()
                    .idEq(id)
                    .get(0);
        }

        ListView lv = (ListView)findViewById(R.id.listView);


        final OrmaListAdapter<Cell> adapter = new OrmaListAdapter<Cell>(this, orma.relationOfCell()) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                CellView view;
                if(convertView == null) {
                    view = (CellView)getLayoutInflater().inflate(R.layout.list_item, null);
                    Cell cell = getItem(position);
                    view.setMarkdownContents(cell.source);
                } else {
                    view = (CellView)convertView;
                }
                return view;
            }
        };

        lv.setAdapter(adapter);

        findViewById(R.id.buttonNew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: goto new activity.
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                Cell cell = new Cell();
                                cell.book = book;
                                cell.cellType = 0;
                                cell.source = "This is test.\n Here is another test.";

                                orma.insertIntoCell(cell);


                                // should be handler. but this is only test code.
                                // adapter.notifyDataSetChanged();
                            }
                        }
                ).start();
            }
        });

    }
}
