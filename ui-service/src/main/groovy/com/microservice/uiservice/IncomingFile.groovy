package com.microservice.uiservice

class IncomingFile {
    String fileName
    FileStatus status

    @Override
    public String toString() {
        return "IncomingFile{fileName='$fileName', status=$status}"
    }
}

enum FileStatus {
    PENDING,
    DONE,
    FAILED
}
