package karino2.livejournal.com.meatpieday;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class CellView extends FrameLayout {


    enum CellType {
        UNINITIALIZE,
        TEXT,
        IMAGE
    }

    CellType cellType = CellType.UNINITIALIZE;
    TextView markDownView;
    ImageView imageView;
    Cell cell = null;

    public boolean isImage() { return cellType == CellType.IMAGE; }
    public Cell getBoundCell() { return cell; }

    public CellView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void ensureInitialize() {
        if(cellType == CellType.UNINITIALIZE) {
            markDownView = (TextView)findViewById(R.id.textView);
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

        markDownView.setText(text);

        if(cellType != CellType.TEXT) {
            imageView.setVisibility(GONE);
            markDownView.setVisibility(VISIBLE);
        }
        cellType = CellType.TEXT;


    }



}
