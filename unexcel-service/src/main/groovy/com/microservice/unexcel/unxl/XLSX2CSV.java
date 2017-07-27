/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package com.microservice.unexcel.unxl;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.util.CellReference;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTWorkbookPr;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.WorkbookDocument;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * A rudimentary XLSX -> CSV processor modeled on the
 * POI sample program XLS2CSVmra by Nick Burch from the
 * package org.apache.poi.hssf.eventusermodel.examples.
 * Unlike the HSSF version, this one completely ignores
 * missing rows.
 * <p>
 * Data sheets are read using a SAX parser to keep the
 * memory footprint relatively small, so this should be
 * able to read enormous workbooks.  The styles table and
 * the shared-string table must be kept in memory.  The
 * standard POI styles table class is used, but a custom
 * (read-only) class is used for the shared string table
 * because the standard POI SharedStringsTable grows very
 * quickly with the number of unique strings.
 * <p>
 * Thanks to Eric Smith for a patch that fixes a problem
 * triggered by cells with multiple "t" elements, which is
 * how Excel represents different formats (e.g., one word
 * plain and one word bold).
 *
 * @author Chris Lott
 */
public class XLSX2CSV {

    /**
     * The type of the data value is indicated by an attribute on the cell.
     * The value is usually in a "v" element within the cell.
     */
    enum xssfDataType {
        BOOL,
        ERROR,
        FORMULA,
        INLINESTR,
        SSTINDEX,
        NUMBER,
    }


    /**
     * Derived from http://poi.apache.org/spreadsheet/how-to.html#xssf_sax_api
     * <p>
     * Also see Standard ECMA-376, 1st edition, part 4, pages 1928ff, at
     * http://www.ecma-international.org/publications/standards/Ecma-376.htm
     * <p>
     * A web-friendly version is http://openiso.org/Ecma/376/Part4
     */
    class MyXSSFSheetHandler extends DefaultHandler {

        /**
         * Table with styles
         */
        private StylesTable stylesTable;

        /**
         * Table with unique strings
         */
        private ReadOnlySharedStringsTable sharedStringsTable;

        /**
         * Destination for data
         */
        private final StreamingCsvBuffer csvBuffer;

        private final boolean isDate1904;

        /**
         * Number of columns to read starting with leftmost
         */
        private final int minColumnCount;

        // Set when V start element is seen
        private boolean vIsOpen;

        // Set when cell start element is seen;
        // used when cell close element is seen.
        private xssfDataType nextDataType;

        // Used to format numeric cell values.
        private short formatIndex;
        private String formatString;
        private final DataFormatter formatter;
        private final NumberFormat defaultNumberFormat;

        private int thisRow = -1;
        private int lastRowNumber = 1;

        private int thisColumn = -1;
        // The last column printed to the output stream
        private int lastColumnNumber = -1;

        // Gathers characters as they are seen.
        private StringBuffer value;

        /**
         * Accepts objects needed while parsing.
         *
         * @param printStream output print stream
         * @param styles      Table of styles
         * @param strings     Table of shared strings
         * @param cols        Minimum number of columns to show
         * @param isDate1904  Flag to process dates as starting from 1904 year
         */
        public MyXSSFSheetHandler(
                PrintStream printStream,
                StylesTable styles,
                ReadOnlySharedStringsTable strings,
                int cols,
                boolean isDate1904) {
            this.stylesTable = styles;
            this.sharedStringsTable = strings;
            this.minColumnCount = cols;
            this.csvBuffer = new StreamingCsvBuffer(printStream);
            this.value = new StringBuffer();
            this.nextDataType = xssfDataType.NUMBER;
            this.formatter = new CustomDataFormatter(Locale.US);
            this.defaultNumberFormat = NumberFormat.getInstance(Locale.US);
            this.defaultNumberFormat.setMaximumFractionDigits(UnexcelConstants.MAX_FRACTION_DIGITS);
            this.defaultNumberFormat.setGroupingUsed(false);
            this.isDate1904 = isDate1904;
        }

        public StreamingCsvBuffer getCsvBuffer() {
            return csvBuffer;
        }

        /*
                   * (non-Javadoc)
                   * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
                   */
        public void startElement(String uri, String localName, String name,
                                 Attributes attributes) throws SAXException {

            if ("inlineStr".equals(name) || "v".equals(name) || ("t".equals(name) && nextDataType == xssfDataType.INLINESTR)) {
                vIsOpen = true;
                // Clear contents cache
                value.setLength(0);
            }
            // row
            else if ("row".equals(name)) {
                thisRow = Integer.parseInt(attributes.getValue("r"));
                // add missed rows if we got row #4 after row #2
                if (thisRow > lastRowNumber + 1) {
                    for (int i = lastRowNumber + 1; i < thisRow; i++) {
                        csvBuffer.newLine();
                    }
                }
                lastRowNumber = thisRow;
            }
            // c => cell
            else if ("c".equals(name)) {
                // Get the cell reference
                String r = attributes.getValue("r");
                int firstDigit = -1;
                for (int c = 0; c < r.length(); ++c) {
                    if (Character.isDigit(r.charAt(c))) {
                        firstDigit = c;
                        break;
                    }
                }
                if (firstDigit != 0) {
                    thisColumn = nameToColumn(r.substring(0, firstDigit));
                } else {
                    thisColumn++; // in some very rare cases, r maybe 237150 instead of D37150, there is no column letter. Treat it as next column
                }


                // Set up defaults.
                this.nextDataType = xssfDataType.NUMBER;
                this.formatIndex = -1;
                this.formatString = null;
                String cellType = attributes.getValue("t");
                String cellStyleStr = attributes.getValue("s");
                if ("b".equals(cellType))
                    nextDataType = xssfDataType.BOOL;
                else if ("e".equals(cellType))
                    nextDataType = xssfDataType.ERROR;
                else if ("inlineStr".equals(cellType))
                    nextDataType = xssfDataType.INLINESTR;
                else if ("s".equals(cellType))
                    nextDataType = xssfDataType.SSTINDEX;
                else if ("str".equals(cellType))
                    nextDataType = xssfDataType.FORMULA;
                else if (cellStyleStr != null) {
                    // It's a number, but almost certainly one
                    //  with a special style or format 
                    int styleIndex = Integer.parseInt(cellStyleStr);
                    XSSFCellStyle style = stylesTable.getStyleAt(styleIndex);
                    this.formatIndex = style.getDataFormat();
                    this.formatString = style.getDataFormatString();
                    if (this.formatString == null)
                        this.formatString = BuiltinFormats.getBuiltinFormat(this.formatIndex);
                }
            } else if ("dimension".equals(name)) {
                String ref = attributes.getValue("ref");
                String[] dimensionCells = ref.split(":");
                if (dimensionCells.length == 2) {
                    // number of cells in ref attribute should 2 (like "A1:NC536"), otherwise it is an empty list (only "A1" in ref)
                    String lastCell = dimensionCells[1];
                    CellReference cellReference = new CellReference(lastCell);
                    csvBuffer.setMaxNumberOfColumns(cellReference.getCol() + 1);
                }
            }

        }

        /*
           * (non-Javadoc)
           * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
           */
        public void endElement(String uri, String localName, String name)
                throws SAXException {

            String thisStr = null;

            // v => contents of a cell
            if ("v".equals(name) || ("t".equals(name) && nextDataType == xssfDataType.INLINESTR)) {
                // Process the value contents as required.
                // Do now, as characters() may be called more than once
                switch (nextDataType) {

                    case BOOL:
                        char first = value.charAt(0);
                        thisStr = first == '0' ? "FALSE" : "TRUE";
                        break;

                    case ERROR:
                        thisStr = "ERROR:" + escapeQuotesAndSlashes(value.toString());
                        break;

                    case FORMULA:
                        // A formula could result in a string value,
                        // so always add double-quote characters.
                        thisStr = escapeQuotesAndSlashes(value.toString());
                        break;

                    case INLINESTR:
                        // TODO: have seen an example of this, so it's untested.
                        XSSFRichTextString rtsi = new XSSFRichTextString(value.toString());
                        thisStr = escapeQuotesAndSlashes(rtsi.toString());
                        break;

                    case SSTINDEX:
                        String sstIndex = value.toString();
                        try {
                            int idx = Integer.parseInt(sstIndex);
                            XSSFRichTextString rtss = new XSSFRichTextString(sharedStringsTable.getEntryAt(idx));
                            thisStr = escapeQuotesAndSlashes(rtss.toString());
                        } catch (NumberFormatException ex) {
                            csvBuffer.add("\"Failed to parse SST index '" + sstIndex + "': " + ex.toString() + "\"");
                            csvBuffer.newLine();
                        }
                        break;

                    case NUMBER:
                        String n = value.toString();
                        if (this.formatString != null) {
                            thisStr = formatter.formatRawCellContents(Double.parseDouble(n), this.formatIndex, this.formatString, isDate1904);
                        } else {
                            thisStr = defaultNumberFormat.format(Double.parseDouble(n));
                        }
                        break;

                    default:
                        thisStr = "(TODO: Unexpected type: " + nextDataType + ")";
                        break;
                }

                // Output after we've seen the string contents
                // Emit commas for any fields that were missing on this row
                for (int i = lastColumnNumber + 1; i < thisColumn; ++i) {
                    csvBuffer.add("");
                }
                if (lastColumnNumber == -1) {
                    lastColumnNumber = 0;
                }

                // Might be the empty string. Escape with " every value to make it Excel-compatible.
                csvBuffer.add('"' + thisStr + '"');

                // Update column
                if (thisColumn > -1)
                    lastColumnNumber = thisColumn;

            } else if ("row".equals(name)) {

                // Print out any missing commas if needed
                if (minColumns > 0) {
                    // Columns are 0 based
                    if (lastColumnNumber == -1) {
                        lastColumnNumber = 0;
                    }
                    for (int i = lastColumnNumber; i < (this.minColumnCount); i++) {
                        csvBuffer.add("");
                    }
                }

                // We're onto a new row
                csvBuffer.newLine();
                lastColumnNumber = -1;
            }

        }

        /**
         * Captures characters only if a suitable element is open.
         * Originally was just "v"; extended for inlineStr also.
         */
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            if (vIsOpen)
                value.append(ch, start, length);
        }

        /**
         * Converts an Excel column name like "C" to a zero-based index.
         *
         * @param name
         * @return Index corresponding to the specified name
         */
        private int nameToColumn(String name) {
            int column = -1;
            for (int i = 0; i < name.length(); ++i) {
                int c = name.charAt(i);
                column = (column + 1) * 26 + c - 'A';
            }
            return column;
        }


        private String escapeQuotesAndSlashes(String str) {
            return StringUtils.replaceEach(str, new String[]{"\\", "\""}, new String[]{"\\\\", "\"\""});
        }
    }

    ///////////////////////////////////////

    private OPCPackage xlsxPackage;
    private int minColumns;
    private PrintStreamProducer printStreamProducer;

    /**
     * Creates a new XLSX -> CSV converter
     *
     * @param pkg                 The XLSX package to process
     * @param printStreamProducer The CsvFilePrintStreamProducer to output the CSV to
     * @param minColumns          The minimum number of columns to output, or -1 for no minimum
     */
    public XLSX2CSV(OPCPackage pkg, PrintStreamProducer printStreamProducer, int minColumns) {
        this.xlsxPackage = pkg;
        this.printStreamProducer = printStreamProducer;
        this.minColumns = minColumns;
    }

    /**
     * Parses and shows the content of one sheet
     * using the specified styles and shared-strings tables.
     *
     * @param styles
     * @param strings
     * @param sheetInputStream
     */
    public StreamingCsvBuffer processSheet(
            StylesTable styles,
            ReadOnlySharedStringsTable strings,
            InputStream sheetInputStream,
            PrintStream printStream,
            boolean isDate1904)
            throws IOException, ParserConfigurationException, SAXException {

        InputSource sheetSource = new InputSource(sheetInputStream);
        SAXParserFactory saxFactory = SAXParserFactory.newInstance();
        SAXParser saxParser = saxFactory.newSAXParser();
        XMLReader sheetParser = saxParser.getXMLReader();
        MyXSSFSheetHandler handler = new MyXSSFSheetHandler(printStream, styles, strings, this.minColumns, isDate1904);
        sheetParser.setContentHandler(handler);
        sheetParser.parse(sheetSource);
        return handler.getCsvBuffer();
    }

    /**
     * Initiates the processing of the XLS workbook file to CSV.
     *
     * @throws IOException
     * @throws OpenXML4JException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    public void process()
            throws IOException, OpenXML4JException, ParserConfigurationException, SAXException, XmlException {

        ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(this.xlsxPackage);
        XSSFReader xssfReader = new XSSFReader(this.xlsxPackage);
        InputStream workbookXml = xssfReader.getWorkbookData();
        WorkbookDocument doc = WorkbookDocument.Factory.parse(workbookXml);
        CTWorkbook wb = doc.getWorkbook();
        CTWorkbookPr prefix = wb.getWorkbookPr();
        boolean isDate1904 = prefix.getDate1904();
        StylesTable styles = xssfReader.getStylesTable();
        XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
        int index = 0;
        while (iter.hasNext()) {
            InputStream stream = iter.next();
            String sheetName = iter.getSheetName();
            PrintStream printStream = this.printStreamProducer.getNextPrintStream(sheetName);
            StreamingCsvBuffer csvBuffer = processSheet(styles, strings, stream, printStream, isDate1904);
            if (!csvBuffer.getHasData()) {
                this.printStreamProducer.removeLastFile();
            }
            stream.close();
            ++index;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Use:");
            System.err.println("  XLSX2CSV <xlsx file> [min columns]");
            return;
        }

        File xlsxFile = new File(args[0]);
        if (!xlsxFile.exists()) {
            System.err.println("Not found or not a file: " + xlsxFile.getPath());
            return;
        }

        int minColumns = -1;
        if (args.length >= 2)
            minColumns = Integer.parseInt(args[1]);

        // The package open is instantaneous, as it should be.
        OPCPackage p = OPCPackage.open(xlsxFile.getPath(), PackageAccess.READ);
        XLSX2CSV xlsx2csv = new XLSX2CSV(p, new SystemOutPrintStreamProducer(), minColumns);
        xlsx2csv.process();
    }

}