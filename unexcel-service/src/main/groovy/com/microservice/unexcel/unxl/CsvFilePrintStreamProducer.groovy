package com.microservice.unexcel.unxl

import groovy.transform.CompileStatic
import groovy.util.logging.Log4j

import java.nio.file.Files
import java.nio.file.Path


@Log4j
@CompileStatic
class CsvFilePrintStreamProducer implements PrintStreamProducer {

    Path rootDirectory
    PrintStream previousPrintStream
    List<Path> resultFiles

    CsvFilePrintStreamProducer(Path rootDirectory) {
        this.rootDirectory = rootDirectory
        resultFiles = new ArrayList<>()
    }

    PrintStream getNextPrintStream(String sheetName) {
        if (previousPrintStream) {
            previousPrintStream.close()
        }
        log.debug("Getting new PrintStream for sheet: $sheetName")
        Path csvPath = rootDirectory.resolve(sheetName + ".csv")
        resultFiles.add(csvPath)
        PrintStream printStream = new PrintStream(csvPath.toFile())
        previousPrintStream = printStream
        return printStream
    }

    List<Path> getResultFiles() {
        return resultFiles
    }

    /**
     * Remove the latest file from the list of result files.
     * It is necessary for StreamingCsvBufferSpec when we can determine hasData flag only after full file reading
     */
    void removeLastFile() {
        if (previousPrintStream) {
            previousPrintStream.close()
        }
        Files.deleteIfExists(resultFiles.last())
        resultFiles.remove(resultFiles.last())
    }
}
