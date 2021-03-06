package karino2.livejournal.com.meatpieday;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.util.Base64;
import android.widget.FrameLayout;
import android.widget.ImageView;

import io.github.karino2.kotlitex.view.MarkdownView;

public class CellView extends FrameLayout {


    enum CellType {
        UNINITIALIZE,
        TEXT,
        IMAGE
    }

    CellType cellType = CellType.UNINITIALIZE;
    MarkdownView markDownView;
    ImageView imageView;
    Cell cell = null;

    public boolean isImage() { return cellType == CellType.IMAGE; }
    public Cell getBoundCell() { return cell; }

    public CellView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void ensureInitialize() {
        if(cellType == CellType.UNINITIALIZE) {
            markDownView = (MarkdownView)findViewById(R.id.textView);
            imageView = (ImageView)findViewById(R.id.imageView);

            cellType = CellType.TEXT;
        }
    }

    public void bindCell(Cell cell) {
        this.cell = cell;

        if(cell.cellType == Cell.CELL_TYPE_IMAGE) {
            setImageContents(cell.source);
        } else {
            setMarkdownContents(cell.source);
        }
    }

    void setImageContents(String base64) {
        ensureInitialize();

        byte[] decodedBytes = Base64.decode(base64, Base64.DEFAULT);
        Bitmap bmp = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        imageView.setImageBitmap(bmp);

        if(cellType != CellType.IMAGE) {
            imageView.setVisibility(VISIBLE);
            markDownView.setVisibility(GONE);
        }
        cellType = CellType.IMAGE;
    }

    void setMarkdownContents(String text) {
        ensureInitialize();

        markDownView.setMarkdown(text);

        if(cellType != CellType.TEXT) {
            imageView.setVisibility(GONE);
            markDownView.setVisibility(VISIBLE);
        }
        cellType = CellType.TEXT;


    }



}
