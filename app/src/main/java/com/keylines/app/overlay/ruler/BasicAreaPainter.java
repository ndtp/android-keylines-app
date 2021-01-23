package com.keylines.app.overlay.ruler;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.keylines.app.R;


/**
 * Created by chrismack on 2017-08-18.
 */

public class BasicAreaPainter extends BasicRulerView.AreaPainter {

    private Paint edgePaint;
    private Paint areaPaint;

    private int topOfArea = -1;
    private int bottomOfArea = -1;

    private float density;

    public BasicAreaPainter(Context context) {
        super(context);

        density = getResources().getDisplayMetrics().density;
        edgePaint = new Paint();
        edgePaint.setColor(this.getResources().getColor(R.color.more_transparent_fuschia));
        edgePaint.setStrokeWidth(1.5f * density);

        areaPaint = new Paint();
        areaPaint.setColor(this.getResources().getColor(R.color.more_transparent_fuschia));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (topOfArea >= 0 && bottomOfArea > topOfArea) {
            canvas.drawRect(0, topOfArea, getWidth(), bottomOfArea, areaPaint);
            canvas.drawLine(0, topOfArea, getWidth(), topOfArea, edgePaint);
            canvas.drawLine(0, bottomOfArea, getWidth(), bottomOfArea, edgePaint);
        }
    }

    @Override
    public void setArea(int topEdge, int bottomEdge) {
        topOfArea = topEdge;
        bottomOfArea = bottomEdge;
        invalidate();
    }
}
