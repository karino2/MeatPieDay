package karino2.livejournal.com.meatpieday;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.github.gfx.android.orma.widget.OrmaListAdapter;

import java.util.Date;

public class BookListActivity extends AppCompatActivity {

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
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_list);

        ListView lv = (ListView)findViewById(R.id.listView);

        final OrmaDatabase orma = getOrmaDatabase();

        // temp code.

        if(orma.selectFromBook().execute()
                .getCount() == 0)
        {
            /*
            Book book = new Book();
            book.name = "(New Book)";
            book.createdTime = new Date();

            orma.relationOfBook()
                    .inserter()
                    .execute(book);
                    */

            // orma.updateBook()

            /*
                    .name("(New Book)")
                    .createdTime(new Date())
                    .execute();
                    */
            /*
            orma.relationOfBook()
                    .updater()
                    .
                    .execute
            orma.
            orma.insertIntoBook(book);
            */

            new Thread(
                    new Runnable() {
                        @Override
                        public void run() {
                            Book book = new Book();
                            book.name = "(New Book)";
                            book.createdTime = new Date();
                            orma.insertIntoBook(book);
                        }
                    }
            ).start();
        }

        /*


         */
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Book book = (Book)view.getTag();
                Intent intent = new Intent(BookListActivity.this, BookActivity.class);
                intent.putExtra("BOOK_ID", book.id);
                startActivity(intent);
            }
        });


        ListAdapter adapter = new OrmaListAdapter<Book>(this, orma.relationOfBook()) {

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

    }

    private OrmaDatabase getOrmaDatabase() {
        return OrmaDatabase.builder(this)
                    .build();
    }
}
