package karino2.livejournal.com.meatpieday;

import android.graphics.BitmapFactory;
import android.util.Base64;

import com.github.gfx.android.orma.annotation.Column;
import com.github.gfx.android.orma.annotation.PrimaryKey;
import com.github.gfx.android.orma.annotation.Table;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Created by _ on 2017/01/11.
 */

@Table
public class Cell {
    @PrimaryKey(autoincrement = true)
    public long id;

    @Column(indexed = true)
    public Book book;


    @Column(indexed = true)
    public long viewOrder;

    @Column
    public int cellType; // 0: text(markdown), 1: image(image output with dummy code).
    // https://ipython.org/ipython-doc/3/notebook/nbformat.html

    @Column
    public String source;

    @Column(defaultExpr = "0")
    public long lastModified = 0;


    public static final int CELL_TYPE_TEXT = 0;
    public static final int CELL_TYPE_IMAGE = 1;


    public void toJson(JsonWriter writer) throws IOException {
        switch(cellType) {
            case CELL_TYPE_TEXT:
                toJsonMarkdownCell(writer);
                return;
            case CELL_TYPE_IMAGE:
                toJsonImageCell(writer);
                return;
            default:
                throw new RuntimeException("Unknown cell type. " + String.valueOf(cellType));
        }
    }



    void toJsonImageCell(JsonWriter writer) throws IOException {
        /*
          {
   "cell_type": "code",
   "execution_count": 4,
   "metadata": {
    "collapsed": false
   },
   "outputs": [
    {
     "data": {
      "image/png": "iVBORw0...==\n",
      "text/plain": [
       "<IPython.core.display.Image object>"
      ]
     },
     "execution_count": 4,
     "metadata": {},
     "output_type": "execute_result"
    }
   ],
   "source": [
    "from IPython.display import Image\n",
    "Image(\"img/2_1.png\")"
   ]
  },
         */

        final int DUMMY_EXEC_COUNT = 1;

        writer.beginObject();
        writer.name("cell_type").value("code")
                .name("execution_count").value(DUMMY_EXEC_COUNT) // dummy val
                .name("metadata").beginObject().name("collapsed").value(false).endObject();

        writer.name("outputs")
                .beginArray()
                .beginObject()
                .name("data").beginObject()
                        .name(getMimeType(source) /* "image/png" */ ).value(source)
                        .name("text/plain").beginArray().value("<IPython.core.display.Image object>").endArray()
                    .endObject()
                .name("execution_count").value(DUMMY_EXEC_COUNT) // dummy val
                .name("metadata").beginObject().endObject()
                .name("output_type").value("execute_result")
                .endObject()
                .endArray();

        writer.name("source").beginArray().
                value("MeatPieImage()") // dummy source.
                .endArray();

        writer.endObject();
    }

    private String getMimeType(String source) {
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        byte[] decodedBytes = Base64.decode(source, Base64.DEFAULT);
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length, opt);

        return opt.outMimeType;
    }

    void toJsonMarkdownCell(JsonWriter writer) throws IOException {
        /*
        {
            "cell_type": "markdown",
                "metadata": {},
            "source": [
            "This block is MarkDown."
            ]
        },
        */
        writer.beginObject()
                .name("cell_type").value("markdown")
                .name("metadata").beginObject()
                    .name("updated_at").value(lastModified)
                    .endObject()
                .name("source").beginArray().value(source).endArray()
                .endObject();

    }

}
