package karino2.livejournal.com.meatpieday;

import com.github.gfx.android.orma.annotation.Column;
import com.github.gfx.android.orma.annotation.PrimaryKey;
import com.github.gfx.android.orma.annotation.Table;

/**
 * Created by _ on 2017/01/11.
 */

@Table
public class Cell {
    @PrimaryKey(autoincrement = true)
    public long id;

    @Column
    public Book book;

    @Column
    public int cellType; // 0: text(markdown), 1: image(image output with dummy code).
    // https://ipython.org/ipython-doc/3/notebook/nbformat.html

    @Column
    public String source;


    public static final int CELL_TYPE_TEXT = 0;
    public static final int CELL_TYPE_IMAGE = 1;

}
