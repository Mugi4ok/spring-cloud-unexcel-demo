package com.microservice.unexcel.unxl

import groovy.transform.CompileStatic

@CompileStatic
class StreamingCsvBuffer {
    public static final char nbsp = (char) 160.intValue()
    public static final char space = (char) 32.intValue()

    String delimiter = ',' // comma is the default CSV delimeter

    /**
     * Output print stream
     */
    PrintStream stream

    List<String> currentRow

    boolean hasData
    boolean hasNotEmptyFirstRow
    int numberOfEmptyRows
    int maxNumberOfColumns;

    StreamingCsvBuffer(PrintStream printStream) {
        this.stream = printStream
        hasData = false
        currentRow = new ArrayList<>()
    }

    StreamingCsvBuffer add(String value) {
        currentRow.add(value.replace(nbsp, space)) // replace nbsp to simple space
        this
    }

    StreamingCsvBuffer add(char value) {
        currentRow.add(String.valueOf(value))
        this
    }

    StreamingCsvBuffer newLine() {
        flush()
        currentRow = new ArrayList<>()
        this
    }

    void close() {
        flush()
        stream.close()
    }

    protected void flush() {
        // as soon as we cannot determine number of columns before start reading Excel, maxNumberOfColumns is optional and may be set during file processing
        maxNumberOfColumns = Math.max(maxNumberOfColumns, currentRow.size())
        if (!currentRow.isEmpty()) {
            flushLines()
            hasData = true // mark that we has written a line at least once
            numberOfEmptyRows = 0
        } else if (hasData) {
            // keep that we have an empty line. If then there will be any non-empty line, we need to flush these empty lines
            // if there is no such non-empty lines, then do nothing, skip last empty lines completely.
            numberOfEmptyRows++
        } else {
            // do nothing, because there was no non-empty line, the first lines are empty, skip them.
        }
    }

    protected void flushLines() {
        // write a new line if there was data before
        if (hasData) {
            stream.println()
        }
        // write empty lines between lines with data
        if (numberOfEmptyRows) {
            String emptyLine = delimiter.multiply(maxNumberOfColumns - 1)
            for (int i = 0; i < numberOfEmptyRows; i++) {
                stream.println(emptyLine)
            }
        }
        // write down data from current row
        Iterator<String> iterator = currentRow.iterator()
        while (iterator.hasNext()) {
            stream.print(iterator.next())
            if (iterator.hasNext()) {
                stream.print(delimiter)
            }
        }
        // add missed columns if necessary
        for (int i = currentRow.size(); i < maxNumberOfColumns; i++) {
            stream.print(delimiter)
        }
    }
}
