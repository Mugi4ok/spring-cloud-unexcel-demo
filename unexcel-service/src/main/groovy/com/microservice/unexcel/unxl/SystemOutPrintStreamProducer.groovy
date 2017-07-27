package com.microservice.unexcel.unxl

import java.nio.file.Path


class SystemOutPrintStreamProducer implements PrintStreamProducer {

    @Override
    PrintStream getNextPrintStream(String sheetName) {
        System.out.println();
        System.out.println("Sheet: $sheetName");
        return System.out
    }

    @Override
    List<Path> getResultFiles() {
        return []
    }

    @Override
    void removeLastFile() {
        // do nothing
    }
}
