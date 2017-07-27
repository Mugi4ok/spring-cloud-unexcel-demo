package com.microservice.unexcel

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
class IncomingFile {
    @Id
    @GeneratedValue
    Long id
    String fileName
    String filePath
    FileStatus status

    IncomingFile() {}

    @Override
    public String toString() {
        return "IncomingFile{id=$id, fileName='$fileName', status=$status}"
    }
}

enum FileStatus {
    PENDING,
    DONE,
    FAILED
}
