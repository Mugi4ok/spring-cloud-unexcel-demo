package com.microservice.unexcel.unxl;

import org.apache.poi.hssf.eventusermodel.FormatTrackingHSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFListener;
import org.apache.poi.hssf.record.CellValueRecordInterface;
import org.apache.poi.hssf.record.FormulaRecord;
import org.apache.poi.hssf.record.NumberRecord;

import java.text.NumberFormat;
import java.util.Locale;

public class CustomFormatTrackingHSSFListener extends FormatTrackingHSSFListener {
    protected CustomDataFormatter formatter;
    protected NumberFormat defaultFormat;
    protected boolean isDate1904;

    public CustomFormatTrackingHSSFListener(HSSFListener childListener) {
        this(childListener, Locale.getDefault());
    }

    public CustomFormatTrackingHSSFListener(HSSFListener childListener, Locale locale) {
        super(childListener, locale);
        this.formatter = new CustomDataFormatter(locale);
        this.defaultFormat = NumberFormat.getInstance(locale);
        this.defaultFormat.setMaximumFractionDigits(UnexcelConstants.MAX_FRACTION_DIGITS);
        this.isDate1904 = false;
    }

    @Override
    public String formatNumberDateCell(CellValueRecordInterface cell) {
        double value;
        if (cell instanceof NumberRecord) {
            value = ((NumberRecord) cell).getValue();
        } else if (cell instanceof FormulaRecord) {
            value = ((FormulaRecord) cell).getValue();
        } else {
            throw new IllegalArgumentException("Unsupported CellValue Record passed in " + cell);
        }

        // Get the built in format, if there is one
        int formatIndex = this.getFormatIndex(cell);
        String formatString = this.getFormatString(cell);

        if (formatString == null) {
            return this.defaultFormat.format(value);
        }
        // Format, using the nice new
        // HSSFDataFormatter to do the work for us
        return this.formatter.formatRawCellContents(value, formatIndex, formatString, isDate1904);
    }

    public void setDate1904(boolean date1904) {
        isDate1904 = date1904;
    }
}
