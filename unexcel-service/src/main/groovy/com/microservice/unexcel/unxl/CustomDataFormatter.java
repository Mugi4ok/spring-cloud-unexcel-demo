package com.microservice.unexcel.unxl;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;

import java.lang.reflect.Method;
import java.text.Format;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * This class override maximum number of fraction digits for number formats.
 * It is necessary to prevent loosing fraction part during unexcel if cell style shows only integer digits.
 * <p><a href="https://support.office.com/en-us/article/Create-or-delete-a-custom-number-format-78f2a361-936b-4c03-8772-09fab54be7f4">
 * More information about Excel data formatting </a> </p>
 */
public class CustomDataFormatter extends DataFormatter {

    public static char[] DATE_CHARS = {'d', 'y'}; // no m because m maybe both dates and times
    public static char[] TIME_CHARS = {'h', 's', 'a', 'p'};
    public static Pattern ELAPSED_TIME = Pattern.compile(".*(\\[(s|m|h){1,2}\\]).*");  // elapsed time format such as [mm]:ss or [h]:mm:ss

    public static String DEFAULT_EXCEL_DATE_FORMAT = "yyyy-mm-dd"; // default format is ISO one
    public static String DEFAULT_EXCEL_DATE_WITH_DAY_OF_WEEL_FORMAT = "ddd, yyyy-mm-dd"; // default format is ISO one
    public static String DEFAULT_EXCEL_TIME_FORMAT = "hh:mm:ss"; // 24-hours format, no milliseconds. Milliseconds is not supported by POI (it looks so)

    Map<Integer, String> cache;

    Method getFormatMethod;

    public CustomDataFormatter() {
        super();
        init();

    }

    public CustomDataFormatter(Locale locale) {
        super(locale);
        init();
    }

    private void init() {
        try {
            /* getFormat() method is private and I need either to copy the whole class source code or use reflection to inkove it */
            getFormatMethod = getClass().getSuperclass().getDeclaredMethod("getFormat", double.class, int.class, String.class);
            getFormatMethod.setAccessible(true);
            cache = new HashMap<>();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String formatRawCellContents(double value, int formatIndex, final String formatString, boolean use1904Windowing) {
        // Is it a date?
        if (DateUtil.isADateFormat(formatIndex, formatString)) {
            String overriddenFormat = cache.computeIfAbsent(formatIndex, integer -> {
                String formatStringLowerCase = formatString.toLowerCase();
                boolean hasDateCharacters = StringUtils.containsAny(formatStringLowerCase, DATE_CHARS);
                boolean hasTimeCharacters = StringUtils.containsAny(formatStringLowerCase, TIME_CHARS);
                // format string may be either date or time or both, but have no prohibited chars
                if ((hasDateCharacters || hasTimeCharacters) && !ELAPSED_TIME.matcher(formatString).matches()) {
                    List<String> parts = new ArrayList<String>();
                    if (hasDateCharacters) {
                        if (formatStringLowerCase.contains("ddd")) {
                            parts.add(DEFAULT_EXCEL_DATE_WITH_DAY_OF_WEEL_FORMAT);
                        } else {
                            parts.add(DEFAULT_EXCEL_DATE_FORMAT);
                        }
                    }
                    if (hasTimeCharacters) {
                        parts.add(DEFAULT_EXCEL_TIME_FORMAT);
                    }
                    return StringUtils.join(parts, ' ');
                }
                return formatString;
            });
            return super.formatRawCellContents(value, formatIndex, overriddenFormat, use1904Windowing);
        }
        // else Number
        Format numberFormat = this.getFormatFromParent(value, formatIndex, formatString);
        if (numberFormat == null) {
            return String.valueOf(value);
        }
        if (numberFormat instanceof NumberFormat) {
            ((NumberFormat) numberFormat).setMaximumFractionDigits(UnexcelConstants.MAX_FRACTION_DIGITS);
        }
        // RK: This hack handles scientific notation by adding the missing + back.
        String result = numberFormat.format(new Double(value));
        if (result.contains("E") && !result.contains("E-")) {
            result = result.replaceFirst("E(\\d{2})", "E+$1");
        }
        return result;
    }


    private Format getFormatFromParent(double cellValue, int formatIndex, String formatStrIn) {
        try {
            return (Format) getFormatMethod.invoke(this, cellValue, formatIndex, formatStrIn);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
