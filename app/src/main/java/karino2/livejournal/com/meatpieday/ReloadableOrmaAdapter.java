package karino2.livejournal.com.meatpieday;

import android.content.Context;
import androidx.annotation.NonNull;

import com.github.gfx.android.orma.Relation;
import com.github.gfx.android.orma.rx.RxRelation;
import com.github.gfx.android.orma.widget.OrmaAdapter;

/**
 * Created by _ on 2017/01/24.
 */
public class ReloadableOrmaAdapter<Model> extends OrmaAdapter {

    public ReloadableOrmaAdapter(@NonNull Context context, @NonNull RxRelation relation) {
        super(context, relation);
    }

    public void clearCache() {
        cache.evictAll();
    }
}
