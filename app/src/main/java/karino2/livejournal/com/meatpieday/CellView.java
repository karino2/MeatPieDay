package karino2.livejournal.com.meatpieday;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;
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

    public void setMarkdownContents(String text) {
        ensureInitialize();

        markDownView.setText(text);

        if(cellType == CellType.IMAGE) {
            imageView.setVisibility(GONE);
            markDownView.setVisibility(VISIBLE);
        }

    }



}
