package karino2.livejournal.com.meatpieday;

import android.content.Context;
import androidx.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;

import com.github.gfx.android.orma.Relation;
import com.github.gfx.android.orma.rx.RxRelation;
import com.github.gfx.android.orma.widget.OrmaListAdapter;

/**
 * Created by _ on 2017/02/05.
 */
class CellListAdapter<CellModel extends Cell> extends OrmaListAdapter<CellModel> {

    public CellListAdapter(@NonNull Context context, @NonNull RxRelation<CellModel, ?> relation) {
        super(context, relation);
    }

    public static Cell_Relation getCellRelation(OrmaDatabase orma, Book target) {
        return orma.relationOfCell().bookEq(target).orderByViewOrderAsc();
    }

    public static CellListAdapter create(Context context, OrmaDatabase orma, Book target) {
        return new CellListAdapter(context, getCellRelation(orma, target));
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
        if (convertView == null) {
            view = (CellView) getLayoutInflater().inflate(R.layout.list_item, null);

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
            view = (CellView) convertView;
        }
        CellModel cell = getItem(position);
        view.bindCell(cell);
        return view;
    }
}
