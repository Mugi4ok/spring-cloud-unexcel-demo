package com.microservice.unexcel

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import java.nio.file.Path

@RestController
@RequestMapping('/unexcel')
class UnexcelController {

    @Autowired
    Unexcel unexcel

    @Autowired
    IncomingFileRepository repository

    @RequestMapping(method = RequestMethod.POST)
    def unexcel(@RequestParam('filePath') String filePath) {
        Path path = new File(filePath).toPath()
        IncomingFile file = repository.findByFileName(unexcel.extractNameFromPath(path)) ?:
                new IncomingFile(filePath: path, fileName: unexcel.extractNameFromPath(path))
        file.status = FileStatus.PENDING
        repository.saveAndFlush(file)
        List<Path> csvFiles = []
        try {
            csvFiles = unexcel.unexcel(new File(filePath).toPath())
        } catch (Exception e) {
            file.status = FileStatus.FAILED
            repository.saveAndFlush(file)
        }
        file.status = FileStatus.DONE
        repository.saveAndFlush(file)
        csvFiles
    }
}
