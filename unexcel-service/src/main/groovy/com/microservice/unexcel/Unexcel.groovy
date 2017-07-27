package com.microservice.unexcel

import com.microservice.unexcel.unxl.CsvFilePrintStreamProducer
import com.microservice.unexcel.unxl.XLS2CSVmra
import com.microservice.unexcel.unxl.XLSX2CSV
import groovy.util.logging.Log4j
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import org.apache.poi.poifs.filesystem.OfficeXmlFileException
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.springframework.stereotype.Service

import javax.validation.constraints.NotNull
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Log4j
@Service
// Somewhere stream is not closed, won't fix as it's a demo project
class Unexcel {
    List<Path> unexcel(Path excelFilePath) {
        Path unexcelDirectory = getUnexcelDirectory(excelFilePath)
        CsvFilePrintStreamProducer streamProducer = new CsvFilePrintStreamProducer(unexcelDirectory)
        log.debug("Start unexceling: ${excelFilePath.toString()}")
        try {
            new FileInputStream(excelFilePath.toFile()).withCloseable { stream ->
                POIFSFileSystem fs = new POIFSFileSystem(stream)
                log.debug("Unexcel using XLS2CSVmra (xls files).")
                XLS2CSVmra xls2csv = new XLS2CSVmra(fs, streamProducer, -1)
                xls2csv.process()
            }
        } catch (OfficeXmlFileException e) {
            log.debug("Unexcel using XLSX2CSV (xlsx files).")
            OPCPackage p = OPCPackage.open(excelFilePath.toFile(), PackageAccess.READ);
            XLSX2CSV xlsx2csv = new XLSX2CSV(p, streamProducer, -1);
            xlsx2csv.process();
        }
        log.debug("Done unexceling: ${excelFilePath.toString()}")
        streamProducer.getResultFiles()
    }

    Path getUnexcelDirectory(Path excelFilePath) {
        def String excelDirectory = extractName(excelFilePath.toString());

        def excelPath = Paths.get(excelDirectory)
        if (!Files.exists(excelPath)) {
            Files.createDirectory(excelPath)
        }
        excelPath
    }

    String extractNameFromPath(@NotNull Path filePath) {
        String path = filePath.toString()
        int index = path.lastIndexOf(File.separator)
        index >= 0 ? path[index + 1..-1] : path
    }

    String extractName(String fileName) {
        def indexOfDot = fileName.lastIndexOf('.')

        if (indexOfDot > 0 && indexOfDot < fileName.length() - 1) fileName[0..indexOfDot - 1]
        else fileName
    }
}
