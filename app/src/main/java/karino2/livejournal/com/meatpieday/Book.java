package karino2.livejournal.com.meatpieday;

import com.github.gfx.android.orma.annotation.Column;
import com.github.gfx.android.orma.annotation.PrimaryKey;
import com.github.gfx.android.orma.annotation.Table;

import java.util.Date;

/**
 * Created by _ on 2017/01/11.
 */

@Table
public class Book {
    @PrimaryKey(autoincrement = true)
    public long id;

    @Column
    public String name;

    @Column(indexed = true)
    public Date createdTime;
}
