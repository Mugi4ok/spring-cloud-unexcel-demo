package com.microservice.unexcel.unxl;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

public interface PrintStreamProducer {
    PrintStream getNextPrintStream(String name);

    List<Path> getResultFiles();

    void removeLastFile();
}
