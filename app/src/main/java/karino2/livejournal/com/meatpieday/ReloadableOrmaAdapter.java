package karino2.livejournal.com.meatpieday;

import android.content.Context;
import android.support.annotation.NonNull;

import com.github.gfx.android.orma.Relation;
import com.github.gfx.android.orma.widget.OrmaAdapter;

/**
 * Created by _ on 2017/01/24.
 */
public class ReloadableOrmaAdapter<Model> extends OrmaAdapter {

    public ReloadableOrmaAdapter(@NonNull Context context, @NonNull Relation relation) {
        super(context, relation);
    }

    public void clearCache() {
        cache.evictAll();
    }
}
