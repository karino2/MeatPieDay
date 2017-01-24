package karino2.livejournal.com.meatpieday;

import android.content.Context;
import android.support.annotation.NonNull;

import com.github.gfx.android.orma.Relation;
import com.github.gfx.android.orma.widget.OrmaListAdapter;

/**
 * Created by _ on 2017/01/24.
 */
abstract class RelodableOrmaListAdapter<Model> extends OrmaListAdapter<Model> {
    public RelodableOrmaListAdapter(@NonNull Context context, @NonNull Relation<Model, ?> relation) {
        super(new ReloadableOrmaAdapter<Model>(context, relation));
    }

    public void reload() {
        ((ReloadableOrmaAdapter) this.delegate).clearCache();
        notifyDataSetChanged();
    }
}
